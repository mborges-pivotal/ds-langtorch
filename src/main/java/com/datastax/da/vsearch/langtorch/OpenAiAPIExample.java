package com.datastax.da.vsearch.langtorch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.jsonpath.JsonPath;

import ai.knowly.langtorch.llm.openai.OpenAIService;
import ai.knowly.langtorch.llm.openai.schema.config.OpenAIServiceConfig;
import ai.knowly.langtorch.llm.openai.schema.dto.completion.chat.ChatCompletionChoice;
import ai.knowly.langtorch.llm.openai.schema.dto.completion.chat.ChatCompletionRequest;
import ai.knowly.langtorch.llm.openai.schema.dto.completion.chat.ChatCompletionResult;
import ai.knowly.langtorch.llm.openai.schema.dto.completion.chat.Function;
import ai.knowly.langtorch.llm.openai.schema.dto.completion.chat.FunctionCall;
import ai.knowly.langtorch.llm.openai.schema.dto.completion.chat.Parameters;
import ai.knowly.langtorch.llm.openai.schema.dto.embedding.Embedding;
import ai.knowly.langtorch.llm.openai.schema.dto.embedding.EmbeddingRequest;
import ai.knowly.langtorch.schema.chat.ChatMessage;
import ai.knowly.langtorch.schema.chat.Role;
import ai.knowly.langtorch.schema.chat.SystemMessage;
import ai.knowly.langtorch.schema.chat.UserMessage;

@RestController
public class OpenAiAPIExample {

    private static Logger logger = LoggerFactory.getLogger(OpenAiAPIExample.class);

    private RestTemplate restTemplate = new RestTemplate();

    @Value("${OPENAI_LLM_MODEL:text-embedding-ada-002}")
    private String llmModel;

    @Value("${OPENAI_CHAT_MODEL:gpt-3.5-turbo}")
    private String chatModel;

    @Value("${WEATHER_API_KEY}")
    private String weatherApiKey;

    @Autowired
    private AstraDbVectorStore store;

    private OpenAIService service;

    // constructor
    public OpenAiAPIExample(@Value("${OPENAI_API_KEY}") String openAiKey) {
        OpenAIServiceConfig config = OpenAIServiceConfig.builder()
                .setApiKey(openAiKey).build();
        service = new OpenAIService(config);
    }

    @GetMapping("/v1/embeddings")
    public @ResponseBody CompletableFuture<List<Embedding>> createEmbedding(@RequestParam String message) {
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(llmModel)
                .input(Collections.singletonList(message))
                .build();

        return callCreateEmbedding(embeddingRequest);
    }

    @GetMapping("/v1/search")
    public @ResponseBody List<String> searchEmbedding(@RequestParam String message)
            throws InterruptedException, ExecutionException {
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(llmModel)
                .input(Collections.singletonList(message))
                .build();

        // Message embedding
        CompletableFuture<List<Embedding>> result = callCreateEmbedding(embeddingRequest);
        List<Double> embedding = result.get().get(0).getValue();

        // Vector Search
        CompletionStage<AsyncResultSet> rs = store.searchWebsite(embedding.toString());
        rs.toCompletableFuture().join();

        List<String> simSearchResults = new ArrayList<String>();
        Row r = null;
        while ((r = rs.toCompletableFuture().get().one()) != null) {
            String rowString = r.getFormattedContents();
            logger.debug(rowString);
            simSearchResults.add(rowString);
        }

        return simSearchResults;
    }

    @GetMapping("/v1/chat/completions")
    public @ResponseBody CompletableFuture<ChatCompletionResult> createChatCompletion(@RequestParam String message) throws InterruptedException, ExecutionException {

        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(SystemMessage.of("You are a very polite hotel concierge"));
        messages.add(SystemMessage.of("The hotel you work is in Austin, Texas "));
        messages.add(UserMessage.of(message));

        // Similarity Search on the WebSite content to help results
        List<String> simSearchResults = searchEmbedding(message);
        for(String s: simSearchResults) {
            messages.add(SystemMessage.of(s));
        }

        // Build initial message
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .setModel(chatModel)
                .setMessages(messages)
                .setN(3)
                .setMaxTokens(250)
                .setLogitBias(new HashMap<>())
                .setFunctions(ImmutableList.of(buildWeatherFunction()))
                .setFunctionCall("auto")
                .setLogitBias(new HashMap<>()).build();

        // Calling and Processing response for possible function calls
        try {
            CompletableFuture<ChatCompletionResult> res = callChatCompletion(chatCompletionRequest);
            ChatCompletionResult chatResult = res.get();
            ChatCompletionChoice choice = chatResult.getChoices().get(0);

            // Don't need to call function
            if (!choice.getFinishReason().equals("function_call")) {
                return res;
            }

            // Calling function
            FunctionCall functionCall = choice.getMessage().getFunctionCall();
            String functionName = functionCall.getName();
            if (!functionName.equals("get_current_weather")) {
                throw new Error("Function not recognized " + functionName);
            }
            String args = choice.getMessage().getFunctionCall().getArguments();
            String location = JsonPath.read(args, "$.location");

            logger.info("Calling weather function for {}", location);
            String weather = getCurrentWeather(location);

            logger.info("Adding weather function response. Feels like {}", weather);
            // ChatMessage msg = new ChatMessage(String.format("Weather in Austin, TX is %s fahrenheit",weather), Role.ASSISTANT, functionName, functionCall);

            messages.add(2,SystemMessage.of(String.format("Weather in Austin, TX is %s fahrenheit",weather)));

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        logger.info("Calling final interaction");
        CompletableFuture<ChatCompletionResult> response = callChatCompletion(chatCompletionRequest);

        logger.debug("response: {}", response);

        return response;
    }

    ///////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////

    @Async
    private CompletableFuture<List<Embedding>> callCreateEmbedding(EmbeddingRequest req) {
        return CompletableFuture.completedFuture(service.createEmbeddings(req).getData());
    }

    @Async
    private CompletableFuture<ChatCompletionResult> callChatCompletion(ChatCompletionRequest req) {
        return CompletableFuture.completedFuture(service.createChatCompletion(req));
    }

    // Build a chatGPT Function to check the current weather
    private Function buildWeatherFunction() {
        return Function.builder()
                .setName("get_current_weather")
                .setDescription("Get the current weather in a given location")
                .setParameters(
                        Parameters.builder()
                                .setRequired(ImmutableList.of("location"))
                                .setProperties(
                                        ImmutableMap.<String, Object>builder()
                                                .put(
                                                        "location",
                                                        ImmutableMap.builder()
                                                                .put("type", "string")
                                                                .put(
                                                                        "description",
                                                                        "The city and state, e.g. San Francisco, CA")
                                                                .build())
                                                .put(
                                                        "unit",
                                                        ImmutableMap.builder()
                                                                .put("type", "string")
                                                                .put(
                                                                        "enum",
                                                                        ImmutableList.of("celsius", "fahrenheit"))
                                                                .build())
                                                .build())
                                .setType("object")
                                .build())
                .build();
    }

    // https://openweathermap.org/
    /*
     * {"coord":{"lon":-97.7431,"lat":30.2672},"weather":[{"id":801,"main":"Clouds",
     * "description":"few clouds","icon":"02n"}],"base":"stations","main":{"temp":74
     * .05,"feels_like":73.08,"temp_min":69.19,"temp_max":77.13,"pressure":1025,
     * "humidity":41},"visibility":10000,"wind":{"speed":9.22,"deg":10},"clouds":{
     * "all":20},"dt":1697253576,"sys":{"type":2,"id":2073627,"country":"US",
     * "sunrise":1697200285,"sunset":1697241764},"timezone":-18000,"id":4671654,
     * "name":"Austin","cod":200}
     */
    private String getCurrentWeather(String city) {
        String units = "imperial";
        String country = "US";
        String reqUrl = String.format("http://api.openweathermap.org/data/2.5/weather?appid=%s&q=%s,%s&units=%s",
                weatherApiKey, city, country, units);
        ResponseEntity<String> res = restTemplate.getForEntity(reqUrl, String.class);

        logger.debug("response: {} ", res.getBody());

        if (res.getStatusCode().isError()) {
            throw new Error(res.getBody());
        }

        return JsonPath.read(res.getBody(), "$.main.feels_like").toString();
    }

}
