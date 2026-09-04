package com.gptwatch.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
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
                GPTWatchApp(this)
            }
        }
    }
}

@Composable
fun GPTWatchApp(activity: Activity) {

    val prefs = remember {
        activity.getSharedPreferences("gpt_watch", Activity.MODE_PRIVATE)
    }

    var apiKey by remember {
        mutableStateOf(prefs.getString("api_key", "") ?: "")
    }

    var savedKey by remember {
        mutableStateOf(apiKey.isNotBlank())
    }

    var question by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf("Hi! Ask me anything.")
    }

    var loading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = "GPT Watch",
                style = MaterialTheme.typography.titleMedium
            )

            if (!savedKey) {

                Text(
                    text = "Enter your OpenAI API key",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (apiKey.startsWith("sk-")) {
                            prefs.edit()
                                .putString("api_key", apiKey)
                                .apply()

                            savedKey = true
                            answer = "API key saved."
                        } else {
                            answer = "Please enter a valid OpenAI API key."
                        }
                    }
                ) {
                    Text("Save")
                }

                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodySmall
                )

            } else {

                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Ask") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = !loading && question.isNotBlank(),
                    onClick = {

                        val currentQuestion = question.trim()

                        loading = true
                        answer = "Thinking..."

                        scope.launch {

                            answer = askOpenAI(
                                apiKey = apiKey,
                                question = currentQuestion
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
                        prefs.edit().remove("api_key").apply()
                        apiKey = ""
                        savedKey = false
                        answer = "Enter your OpenAI API key."
                    }
                ) {
                    Text("Change API key")
                }
            }
        }
    }
}

suspend fun askOpenAI(
    apiKey: String,
    question: String
): String = withContext(Dispatchers.IO) {

    try {

        val client = OkHttpClient()

        val json = JSONObject().apply {
            put("model", "gpt-5-mini")
            put("input", question)
        }

        val body = json
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .addHeader(
                "Authorization",
                "Bearer $apiKey"
            )
            .addHeader(
                "Content-Type",
                "application/json"
            )
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->

            val responseText =
                response.body?.string()
                    ?: return@withContext "No response received."

            if (!response.isSuccessful) {

                val errorMessage = try {
                    JSONObject(responseText)
                        .getJSONObject("error")
                        .optString(
                            "message",
                            "Request failed."
                        )
                } catch (_: Exception) {
                    "Request failed: ${response.code}"
                }

                return@withContext errorMessage
            }

            val root = JSONObject(responseText)

            val output = root.optJSONArray("output")
                ?: return@withContext "No answer returned."

            for (i in 0 until output.length()) {

                val item = output.optJSONObject(i)
                    ?: continue

                val content =
                    item.optJSONArray("content")
                        ?: continue

                for (j in 0 until content.length()) {

                    val contentItem =
                        content.optJSONObject(j)
                            ?: continue

                    if (
                        contentItem.optString("type") ==
                        "output_text"
                    ) {
                        return@withContext contentItem
                            .optString(
                                "text",
                                "No answer returned."
                            )
                    }
                }
            }

            "No text answer returned."
        }

    } catch (e: Exception) {

        "Error: ${e.message ?: "Unable to connect"}"
    }
}
