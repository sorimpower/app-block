const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

const openAiApiKey = defineSecret("OPENAI_API_KEY");

const MODELS = Object.freeze({
  OPENAI_FAST: "gpt-5.6-luna",
  OPENAI_SMART: "gpt-5.6-terra",
});
const TASKS = new Set(["BODY_LOG_PROGRESS_ANALYSIS", "HEALTH_CHECKUP_PAGE_SELECTION", "HEALTH_CHECKUP_EXTRACTION", "HEALTH_TREND_ANALYSIS", "HEALTH_SCREENING_OPTION_RECOMMENDATION", "AUCTION_RIGHTS_ANALYSIS", "PHONE_INSIGHT_BATCH"]);
const MAX_PROMPT_LENGTH = 60_000;
// Base64 expands files by about one third. Keep this safely below the Gen 2
// callable request limit while allowing typical high-resolution checkup PDFs.
const MAX_IMAGES_BASE64_LENGTH = 20 * 1024 * 1024;
const MAX_AUDIO_BASE64_LENGTH = 12 * 1024 * 1024;

exports.openAiGenerate = onCall(
  {
    region: "asia-northeast3",
    timeoutSeconds: 120,
    enforceAppCheck: true,
    serviceAccount: "sorimpower-ff78e@appspot.gserviceaccount.com",
    secrets: [openAiApiKey],
  },
  async (request) => {
    const { taskType, model, prompt, jsonOutput, images, audios } = request.data || {};
    if (!TASKS.has(taskType)) throw new HttpsError("invalid-argument", "허용되지 않은 AI 작업입니다.");
    if (!Object.hasOwn(MODELS, model)) throw new HttpsError("invalid-argument", "허용되지 않은 모델입니다.");
    if (typeof prompt !== "string" || !prompt.trim() || prompt.length > MAX_PROMPT_LENGTH) {
      throw new HttpsError("invalid-argument", "분석 요청의 길이가 올바르지 않습니다.");
    }

    const hasImages = Array.isArray(images) && images.length > 0;
    if (hasImages && !["HEALTH_CHECKUP_PAGE_SELECTION", "HEALTH_CHECKUP_EXTRACTION", "HEALTH_SCREENING_OPTION_RECOMMENDATION", "PHONE_INSIGHT_BATCH"].includes(taskType)) throw new HttpsError("invalid-argument", "이 작업에는 이미지를 첨부할 수 없습니다.");
    const imageSize = hasImages ? images.reduce((total, image) => total + (typeof image.base64 === "string" ? image.base64.length : MAX_IMAGES_BASE64_LENGTH + 1), 0) : 0;
    if (imageSize > MAX_IMAGES_BASE64_LENGTH || (hasImages && images.some(image => typeof image.mimeType !== "string"))) throw new HttpsError("invalid-argument", "검진 이미지 형식 또는 크기가 올바르지 않습니다.");
    const hasAudios = Array.isArray(audios) && audios.length > 0;
    if (hasAudios && taskType !== "PHONE_INSIGHT_BATCH") throw new HttpsError("invalid-argument", "이 작업에는 음성을 첨부할 수 없습니다.");
    const audioSize = hasAudios ? audios.reduce((total, audio) => total + (typeof audio.base64 === "string" ? audio.base64.length : MAX_AUDIO_BASE64_LENGTH + 1), 0) : 0;
    if (audioSize > MAX_AUDIO_BASE64_LENGTH || (hasAudios && audios.some(audio => typeof audio.sourceId !== "string" || typeof audio.mimeType !== "string"))) throw new HttpsError("invalid-argument", "통화 녹음 형식 또는 크기가 올바르지 않습니다.");
    let effectivePrompt = prompt;
    if (hasAudios) {
      for (const audio of audios) {
        const form = new FormData();
        form.append("model", "gpt-transcribe");
        form.append("prompt", "한국어 전화 통화입니다. 약속, 일정, 금액, 다시 연락할 내용과 고유명사를 정확히 기록하세요.");
        form.append("languages[]", "ko");
        const safeName = typeof audio.fileName === "string" && audio.fileName.trim() ? audio.fileName.replace(/[^a-zA-Z0-9._-]/g, "_") : "call.m4a";
        form.append("file", new Blob([Buffer.from(audio.base64, "base64")], { type: audio.mimeType }), safeName);
        const transcriptResponse = await fetch("https://api.openai.com/v1/audio/transcriptions", { method: "POST", headers: { Authorization: `Bearer ${openAiApiKey.value()}` }, body: form });
        const transcript = await transcriptResponse.json();
        if (!transcriptResponse.ok) {
          console.error("OpenAI transcription failed", transcriptResponse.status, transcript?.error?.type, transcript?.error?.message);
          continue;
        }
        effectivePrompt += `\n[통화 녹음 전사 sourceId=${audio.sourceId}] ${(transcript.text || "").slice(0, 12000)}`;
      }
    }
    const input = hasImages ? [{ role: "user", content: [
      { type: "input_text", text: effectivePrompt },
      ...images.flatMap(image => [
        { type: "input_text", text: `[다음 이미지 sourceId=${typeof image.sourceId === "string" ? image.sourceId : ""}]` },
        { type: "input_image", image_url: `data:${image.mimeType};base64,${image.base64}` },
      ]),
    ] }] : effectivePrompt;
    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${openAiApiKey.value()}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: MODELS[model],
        input,
        store: false,
        ...(jsonOutput ? { text: { format: { type: "json_object" } } } : {}),
      }),
    });
    const body = await response.json();
    if (!response.ok) {
      console.error("OpenAI response failed", response.status, body?.error?.type, body?.error?.message);
      throw new HttpsError("internal", "OpenAI 분석 요청에 실패했습니다.");
    }
    // Most Responses API replies expose `output_text`, but some model/revision
    // responses provide text only inside output[].content[]. Accept both shapes.
    const nestedText = Array.isArray(body.output)
      ? body.output.flatMap(item => Array.isArray(item.content) ? item.content : [])
        .map(content => {
          if (typeof content.text === "string") return content.text;
          if (typeof content.value === "string") return content.value;
          return "";
        })
        .filter(Boolean)
        .join("\n")
      : "";
    const text = typeof body.output_text === "string" && body.output_text.trim()
      ? body.output_text
      : nestedText;
    if (!text.trim()) {
      console.error("OpenAI response has no readable text", body.status, body.incomplete_details, body.output?.map(item => item.type));
      throw new HttpsError("internal", "OpenAI 응답에 분석 결과가 없습니다.");
    }
    return {
      text,
      model: body.model || MODELS[model],
      inputTokens: body.usage?.input_tokens ?? null,
      outputTokens: body.usage?.output_tokens ?? null,
    };
  },
);
