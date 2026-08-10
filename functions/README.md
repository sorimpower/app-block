# OpenAI Firebase Function

The OpenAI key must never be placed in the Android app. Configure and deploy the callable function from the repository root:

```bash
npm --prefix functions install
firebase login
firebase functions:secrets:set OPENAI_API_KEY
firebase deploy --only functions:openAiGenerate
```

Firebase Functions deployment requires the Firebase project to be on a billing-enabled plan. The function enforces Firebase App Check and accepts only allowlisted tasks and models.
