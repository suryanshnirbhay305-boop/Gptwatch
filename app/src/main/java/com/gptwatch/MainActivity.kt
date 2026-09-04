package com.gptwatch.app

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                GPTWatch()
            }
        }
    }
}

@Composable
fun GPTWatch() {

    val activity = androidx.compose.ui.platform.LocalContext.current as Activity

    val prefs = remember {
        activity.getSharedPreferences("GPTWatch", Activity.MODE_PRIVATE)
    }

    var apiKey by remember {
        mutableStateOf(prefs.getString("api_key", "") ?: "")
    }

    var keySaved by remember {
        mutableStateOf(apiKey.isNotBlank())
    }

    var question by remember { mutableStateOf("") }

    var answer by remember {
        mutableStateOf("Hello! What can I help you with?")
    }

    var loading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text(
            text = "GPT Watch",
            style = MaterialTheme.typography.titleMedium
        )

        if (!keySaved) {

            Text(
                "Enter your OpenAI API key",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (apiKey.startsWith("sk-")) {
                        prefs.edit()
                            .putString("api_key", apiKey)
                            .apply()

                        keySaved = true
                        answer = "Ready!"
                    } else {
                        answer = "Please enter a valid API key."
                    }
                }
            ) {
                Text("Save")
            }

            Text(
                answer,
                style = MaterialTheme.typography.bodySmall
            )

        } else {

            Text(
                text = answer,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Ask me anything") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = question.isNotBlank() && !loading,

                onClick = {

                    val prompt = question.trim()

                    loading = true
                    answer = "Thinking..."

                    scope.launch {

                        answer = askGPT(
                            apiKey = apiKey,
                            prompt = prompt
                        )

                        loading = false
                        question = ""
                    }
                }
            ) {

                if (loading) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )

                } else {

                    Text("Send")
                }
            }

            TextButton(
                onClick = {
                    prefs.edit()
                        .remove("api_key")
                        .apply()

                    apiKey = ""
                    keySaved = false
                    answer = "Enter your API key."
                }
            ) {
                Text("Change key")
            }
        }
    }
}

suspend fun askGPT(
    apiKey: String,
    prompt: String
): String = withContext(Dispatchers.IO) {

    try {

        val client = OkHttpClient()

        val requestJson = JSONObject().apply {
            put("model", "gpt-5-mini")
            put("input", prompt)
        }

        val body = requestJson
            .toString()
            .toRequestBody(
                "application/json".toMediaType()
            )

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .header(
                "Authorization",
                "Bearer $apiKey"
            )
            .header(
                "Content-Type",
                "application/json"
            )
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->

            val text =
                response.body?.string()
                    ?: return@withContext "No response received."

            if (!response.isSuccessful) {

                return@withContext try {

                    JSONObject(text)
                        .getJSONObject("error")
                        .optString(
                            "message",
                            "Request failed."
                        )

                } catch (_: Exception) {

                    "Request failed (${response.code})."
                }
            }

            val json = JSONObject(text)

            val output = json.optJSONArray("output")
                ?: return@withContext "No answer received."

            for (i in 0 until output.length()) {

                val item =
                    output.optJSONObject(i)
                        ?: continue

                val content =
                    item.optJSONArray("content")
                        ?: continue

                for (j in 0 until content.length()) {

                    val part =
                        content.optJSONObject(j)
                            ?: continue

                    if (part.optString("type") == "output_text") {

                        return@withContext part.optString(
                            "text",
                            "No answer received."
                        )
                    }
                }
            }

            "No answer received."
        }

    } catch (e: Exception) {

        "Connection error: ${e.message ?: "Unknown error"}"
    }
}
