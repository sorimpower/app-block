const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

const openAiApiKey = defineSecret("OPENAI_API_KEY");
const youtubeDataApiKey = defineSecret("YOUTUBE_DATA_API_KEY");

const MODELS = Object.freeze({
  OPENAI_FAST: "gpt-5.6-luna",
  OPENAI_SMART: "gpt-5.6-terra",
  OPENAI_DEEP: "gpt-5.6-sol",
});
const TASKS = new Set(["BODY_LOG_PROGRESS_ANALYSIS", "BODY_LOG_DAILY_CALORIE_ANALYSIS", "HEALTH_CHECKUP_PAGE_SELECTION", "HEALTH_CHECKUP_EXTRACTION", "HEALTH_TREND_ANALYSIS", "HEALTH_SCREENING_OPTION_RECOMMENDATION", "AUCTION_RIGHTS_ANALYSIS", "PHONE_INSIGHT_BATCH", "PROPERTY_TAX_DEEP_ANALYSIS", "PROPERTY_TAX_RULE_CHANGE_ANALYSIS", "PROPERTY_TAX_SCENARIO_COMPARISON", "PERSPECTIVE_TOPIC_SUGGESTION", "PERSPECTIVE_METADATA_ANALYSIS"]);
const MAX_PROMPT_LENGTH = 60_000;
// Base64 expands files by about one third. Keep this safely below the Gen 2
// callable request limit while allowing typical high-resolution checkup PDFs.
const MAX_IMAGES_BASE64_LENGTH = 20 * 1024 * 1024;
const MAX_AUDIO_BASE64_LENGTH = 12 * 1024 * 1024;
const TAX_OFFICIAL_DOMAINS = Object.freeze([
  "law.go.kr",
  "nts.go.kr",
  "taxlaw.nts.go.kr",
  "moef.go.kr",
  "mois.go.kr",
  "wetax.go.kr",
  "molit.go.kr",
]);

function isOfficialTaxSource(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return TAX_OFFICIAL_DOMAINS.some(domain => host === domain || host.endsWith(`.${domain}`));
  } catch (_) {
    return false;
  }
}

function extractOfficialTaxSources(body) {
  const sources = [];
  if (!Array.isArray(body.output)) return sources;
  for (const item of body.output) {
    if (item?.type === "web_search_call" && Array.isArray(item.action?.sources)) {
      for (const source of item.action.sources) {
        if (typeof source?.url === "string" && isOfficialTaxSource(source.url)) {
          sources.push({ title: typeof source.title === "string" ? source.title : source.url, url: source.url });
        }
      }
    }
    if (Array.isArray(item?.content)) {
      for (const content of item.content) {
        if (!Array.isArray(content?.annotations)) continue;
        for (const annotation of content.annotations) {
          const citation = annotation?.url_citation || annotation;
          if (typeof citation?.url === "string" && isOfficialTaxSource(citation.url)) {
            sources.push({ title: typeof citation.title === "string" ? citation.title : citation.url, url: citation.url });
          }
        }
      }
    }
  }
  return [...new Map(sources.map(source => [source.url, source])).values()];
}

function normalizeYoutubeText(value) {
  return String(value || "")
    .normalize("NFKC")
    .toLocaleLowerCase("ko-KR")
    .replace(/[\s\p{P}\p{S}_]+/gu, "");
}

function youtubeTokens(value) {
  return new Set(
    String(value || "")
      .normalize("NFKC")
      .toLocaleLowerCase("ko-KR")
      .split(/[^\p{L}\p{N}]+/u)
      .map(token => token.trim())
      .filter(token => token.length >= 2),
  );
}

function tokenSimilarity(first, second) {
  const a = youtubeTokens(first);
  const b = youtubeTokens(second);
  if (a.size === 0 || b.size === 0) return 0;
  let intersection = 0;
  for (const token of a) if (b.has(token)) intersection += 1;
  return intersection / Math.max(a.size, b.size);
}

/**
 * MediaSession이 실제 video ID를 주지 않는 YouTube 재생을 보완한다.
 * 검색 결과의 제목과 채널이 모두 정확히 일치할 때만 URL을 돌려주므로,
 * 비슷한 제목의 다른 영상으로 잘못 분석하는 일을 막는다.
 */
function decodeYoutubeXml(value) {
  return String(value || "").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&#39;/g, "'").replace(/&quot;/g, '"');
}

function extractChapters(description) {
  return String(description || "").split(/\r?\n/)
    .filter(line => /(?:^|\s)(?:\d{1,2}:)?\d{1,2}:\d{2}(?:\s|$)/.test(line))
    .map(line => line.trim()).slice(0, 30);
}

/** 공개 자막이 제공되는 경우에만 best-effort로 짧은 대본을 수집한다. 실패해도 분석은 계속한다. */
async function fetchPublicYoutubeTranscript(videoId) {
  try {
    const list = await fetch(`https://www.youtube.com/api/timedtext?type=list&v=${encodeURIComponent(videoId)}`, { signal: AbortSignal.timeout(6_000) });
    if (!list.ok) return "";
    const tracksXml = await list.text();
    const track = [...tracksXml.matchAll(/<track\b([^>]*)\/>/g)].map(match => match[1]).find(attrs => /lang_code="ko"/.test(attrs))
      || [...tracksXml.matchAll(/<track\b([^>]*)\/>/g)].map(match => match[1])[0];
    if (!track) return "";
    const lang = track.match(/lang_code="([^"]+)"/)?.[1];
    if (!lang) return "";
    const transcriptResponse = await fetch(`https://www.youtube.com/api/timedtext?v=${encodeURIComponent(videoId)}&lang=${encodeURIComponent(lang)}`, { signal: AbortSignal.timeout(8_000) });
    if (!transcriptResponse.ok) return "";
    const xml = await transcriptResponse.text();
    return decodeYoutubeXml([...xml.matchAll(/<text\b[^>]*>([\s\S]*?)<\/text>/g)].map(match => match[1].replace(/<[^>]+>/g, "").trim()).filter(Boolean).join(" ")).slice(0, 24_000);
  } catch (error) {
    console.warn("Public YouTube transcript unavailable", videoId, error?.message);
    return "";
  }
}

/** MediaSession ID 또는 정확한 제목·채널 매칭 후, Terra 분석에 필요한 공개 정보를 함께 반환한다. */
exports.resolveYoutubeVideoContext = onCall(
  {
    region: "asia-northeast3",
    timeoutSeconds: 20,
    enforceAppCheck: true,
    serviceAccount: "sorimpower-ff78e@appspot.gserviceaccount.com",
    secrets: [youtubeDataApiKey],
  },
  async (request) => {
    const suppliedVideoId = typeof request.data?.videoId === "string" ? request.data.videoId.trim() : "";
    const title = typeof request.data?.title === "string" ? request.data.title.trim() : "";
    const channel = typeof request.data?.channel === "string" ? request.data.channel.trim() : "";
    if ((!suppliedVideoId && (!title || !channel)) || title.length > 300 || channel.length > 150) {
      throw new HttpsError("invalid-argument", "영상 제목 또는 채널 정보가 올바르지 않습니다.");
    }
    let videoId = /^[A-Za-z0-9_-]{11}$/.test(suppliedVideoId) ? suppliedVideoId : "";
    if (!videoId) {
      const params = new URLSearchParams({ part: "snippet", type: "video", maxResults: "25", q: title, key: youtubeDataApiKey.value() });
      const response = await fetch(`https://www.googleapis.com/youtube/v3/search?${params}`);
      const body = await response.json();
      if (!response.ok) throw new HttpsError("internal", "YouTube 영상 주소를 조회하지 못했습니다.");
      const expectedTitle = normalizeYoutubeText(title);
      const expectedChannel = normalizeYoutubeText(channel);
      const match = Array.isArray(body.items) ? body.items.map((item) => {
        const candidateId = item?.id?.videoId;
        const candidateTitle = item?.snippet?.title || "";
        const candidateChannel = item?.snippet?.channelTitle || "";
        const exactTitle = normalizeYoutubeText(candidateTitle) === expectedTitle;
        const sameChannel = normalizeYoutubeText(candidateChannel) === expectedChannel;
        const trusted = typeof candidateId === "string" && (exactTitle || (tokenSimilarity(title, candidateTitle) >= 0.82 && (sameChannel || tokenSimilarity(channel, candidateChannel) >= 0.82)));
        return { item, trusted, score: exactTitle ? 2 : tokenSimilarity(title, candidateTitle) + tokenSimilarity(channel, candidateChannel) };
      }).filter(candidate => candidate.trusted).sort((first, second) => second.score - first.score)[0]?.item : null;
      videoId = match?.id?.videoId || "";
    }
    if (!videoId) return { matched: false };
    const detailParams = new URLSearchParams({ part: "snippet,contentDetails,statistics", id: videoId, key: youtubeDataApiKey.value() });
    const detailResponse = await fetch(`https://www.googleapis.com/youtube/v3/videos?${detailParams}`);
    const detail = await detailResponse.json();
    const item = detail?.items?.[0];
    if (!detailResponse.ok || !item) throw new HttpsError("internal", "YouTube 공개 정보를 불러오지 못했습니다.");
    const description = String(item.snippet?.description || "");
    const transcript = await fetchPublicYoutubeTranscript(videoId);
    return {
      matched: true,
      videoId,
      url: `https://www.youtube.com/watch?v=${videoId}`,
      description: description.slice(0, 12_000),
      tags: Array.isArray(item.snippet?.tags) ? item.snippet.tags.slice(0, 40) : [],
      category: item.snippet?.categoryId || "",
      publishedAt: item.snippet?.publishedAt || "",
      duration: item.contentDetails?.duration || "",
      viewCount: item.statistics?.viewCount || "",
      chapters: extractChapters(description),
      transcript,
    };
  },
);

/** AI가 만든 관점별 검색어를 실제 공개 YouTube 영상 카드로 바꾼다. 한 분석에서 최대 4회만 조회한다. */
exports.findYoutubePerspectiveVideos = onCall(
  {
    region: "asia-northeast3",
    timeoutSeconds: 20,
    enforceAppCheck: true,
    serviceAccount: "sorimpower-ff78e@appspot.gserviceaccount.com",
    secrets: [youtubeDataApiKey],
  },
  async (request) => {
    const queries = Array.isArray(request.data?.queries)
      ? request.data.queries.filter(query => typeof query === "string" && query.trim() && query.length <= 240).map(query => query.trim()).slice(0, 4)
      : [];
    if (queries.length === 0) throw new HttpsError("invalid-argument", "추천할 관점 검색어가 없습니다.");
    const results = {};
    await Promise.all(queries.map(async (query) => {
      try {
        const params = new URLSearchParams({ part: "snippet", type: "video", maxResults: "2", q: query, key: youtubeDataApiKey.value(), safeSearch: "moderate" });
        const response = await fetch(`https://www.googleapis.com/youtube/v3/search?${params}`);
        const body = await response.json();
        if (!response.ok) {
          console.warn("Perspective YouTube search failed", response.status, body?.error?.message);
          results[query] = [];
          return;
        }
        results[query] = (Array.isArray(body.items) ? body.items : []).map((item) => {
          const videoId = item?.id?.videoId;
          if (typeof videoId !== "string") return null;
          const snippet = item?.snippet || {};
          return {
            videoId,
            title: String(snippet.title || "YouTube 영상"),
            channelName: String(snippet.channelTitle || ""),
            thumbnailUrl: String(snippet.thumbnails?.medium?.url || snippet.thumbnails?.default?.url || ""),
            url: `https://www.youtube.com/watch?v=${videoId}`,
            publishedAt: String(snippet.publishedAt || ""),
          };
        }).filter(Boolean);
      } catch (error) {
        console.warn("Perspective YouTube search unavailable", error?.message);
        results[query] = [];
      }
    }));
    return { results };
  },
);

exports.openAiGenerate = onCall(
  {
    region: "asia-northeast3",
    timeoutSeconds: 120,
    enforceAppCheck: true,
    serviceAccount: "sorimpower-ff78e@appspot.gserviceaccount.com",
    secrets: [openAiApiKey],
  },
  async (request) => {
    const { taskType, model, prompt, jsonOutput, images, audios, reasoningEffort } = request.data || {};
    if (!TASKS.has(taskType)) throw new HttpsError("invalid-argument", "허용되지 않은 AI 작업입니다.");
    if (!Object.hasOwn(MODELS, model)) throw new HttpsError("invalid-argument", "허용되지 않은 모델입니다.");
    const isTaxAnalysis = taskType.startsWith("PROPERTY_TAX_");
    if (isTaxAnalysis && (model !== "OPENAI_DEEP" || reasoningEffort !== "max")) throw new HttpsError("invalid-argument", "세금 정밀 분석은 GPT-5.6 Sol max만 허용됩니다.");
    if (reasoningEffort != null && !["none", "low", "medium", "high", "xhigh", "max"].includes(reasoningEffort)) throw new HttpsError("invalid-argument", "허용되지 않은 reasoning 설정입니다.");
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
    if (isTaxAnalysis) {
      effectivePrompt = `[서버 기준 확인 시각: ${new Date().toISOString()}]\n${effectivePrompt}`;
    }
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
        ...(reasoningEffort ? { reasoning: { effort: reasoningEffort } } : {}),
        ...(jsonOutput ? { text: { format: { type: "json_object" } } } : {}),
        ...(isTaxAnalysis ? {
          tools: [{
            type: "web_search",
            external_web_access: true,
            search_context_size: "high",
            ...(isTaxAnalysis ? { filters: { allowed_domains: TAX_OFFICIAL_DOMAINS } } : {}),
          }],
          tool_choice: isTaxAnalysis ? "required" : "auto",
          include: ["web_search_call.action.sources"],
        } : {}),
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
    const sources = isTaxAnalysis ? extractOfficialTaxSources(body) : [];
    if (isTaxAnalysis && sources.length === 0) {
      console.error("Tax analysis completed without an official web source", body.status, body.output?.map(item => item.type));
      throw new HttpsError("internal", "공식 법령 검색 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
    return {
      text,
      model: body.model || MODELS[model],
      inputTokens: body.usage?.input_tokens ?? null,
      outputTokens: body.usage?.output_tokens ?? null,
      sources,
      checkedAt: isTaxAnalysis ? Date.now() : null,
    };
  },
);
