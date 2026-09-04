# AI Watch for Galaxy Watch 7
Native Wear OS chat client. It stays inside the app and calls the OpenAI Responses API directly.

## Build
Upload the contents of this project to a GitHub repository. GitHub Actions builds `app-debug.apk` as the `ChatGPTWatch-APK` artifact.

## First run
Open the app, enter your OpenAI API key, tap Save key, then type a question and Send. The key is stored in this app's private preferences on the watch. API billing is separate from ChatGPT Plus.

Note: Direct client-side API keys are convenient for a personal sideloaded app but are less secure than a backend. Do not publish a project containing your key.
