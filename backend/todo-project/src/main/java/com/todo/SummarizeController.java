package com.todo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SummarizeController {

    @Autowired private TodoRepository todoRepo;
    @Value("${openai.api.key}") private String openAiKey;
    @Value("${slack.webhook.url}") private String slackWebhook;

    @PostMapping("/summarize")
    public ResponseEntity<String> summarizeTodos() throws IOException, InterruptedException {
        List<Todo> todos = todoRepo.findByCompletedFalse();

        String todoText = todos.stream()
            .map(t -> "- " + t.getTask())
            .collect(Collectors.joining("\n"));

        String prompt = "Summarize the following to-dos:\n" + todoText;

        String summary = callOpenAI(prompt);
        postToSlack(summary);

        return ResponseEntity.ok("Summary sent to Slack!");
    }

    private String callOpenAI(String prompt) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String requestBody = """
        {
          "model": "gpt-3.5-turbo",
          "messages": [{"role": "user", "content": "%s"}]
        }
        """.formatted(prompt);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + openAiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }

    private void postToSlack(String summary) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String body = new JSONObject().put("text", summary).toString();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(slackWebhook))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

