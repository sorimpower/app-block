const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

const openAiApiKey = defineSecret("OPENAI_API_KEY");

const MODELS = Object.freeze({
  OPENAI_FAST: "gpt-5.6-luna",
  OPENAI_SMART: "gpt-5.6-terra",
});
const TASKS = new Set(["BODY_LOG_PROGRESS_ANALYSIS", "HEALTH_CHECKUP_PAGE_SELECTION", "HEALTH_CHECKUP_EXTRACTION", "HEALTH_TREND_ANALYSIS", "HEALTH_SCREENING_OPTION_RECOMMENDATION", "AUCTION_RIGHTS_ANALYSIS"]);
const MAX_PROMPT_LENGTH = 60_000;
// Base64 expands files by about one third. Keep this safely below the Gen 2
// callable request limit while allowing typical high-resolution checkup PDFs.
const MAX_IMAGES_BASE64_LENGTH = 20 * 1024 * 1024;

exports.openAiGenerate = onCall(
  {
    region: "asia-northeast3",
    enforceAppCheck: true,
    serviceAccount: "sorimpower-ff78e@appspot.gserviceaccount.com",
    secrets: [openAiApiKey],
  },
  async (request) => {
    const { taskType, model, prompt, jsonOutput, images } = request.data || {};
    if (!TASKS.has(taskType)) throw new HttpsError("invalid-argument", "허용되지 않은 AI 작업입니다.");
    if (!Object.hasOwn(MODELS, model)) throw new HttpsError("invalid-argument", "허용되지 않은 모델입니다.");
    if (typeof prompt !== "string" || !prompt.trim() || prompt.length > MAX_PROMPT_LENGTH) {
      throw new HttpsError("invalid-argument", "분석 요청의 길이가 올바르지 않습니다.");
    }

    const hasImages = Array.isArray(images) && images.length > 0;
    if (hasImages && !["HEALTH_CHECKUP_PAGE_SELECTION", "HEALTH_CHECKUP_EXTRACTION", "HEALTH_SCREENING_OPTION_RECOMMENDATION"].includes(taskType)) throw new HttpsError("invalid-argument", "이 작업에는 이미지를 첨부할 수 없습니다.");
    const imageSize = hasImages ? images.reduce((total, image) => total + (typeof image.base64 === "string" ? image.base64.length : MAX_IMAGES_BASE64_LENGTH + 1), 0) : 0;
    if (imageSize > MAX_IMAGES_BASE64_LENGTH || (hasImages && images.some(image => typeof image.mimeType !== "string"))) throw new HttpsError("invalid-argument", "검진 이미지 형식 또는 크기가 올바르지 않습니다.");
    const input = hasImages ? [{ role: "user", content: [
      { type: "input_text", text: prompt },
      ...images.map(image => ({ type: "input_image", image_url: `data:${image.mimeType};base64,${image.base64}` })),
    ] }] : prompt;
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
