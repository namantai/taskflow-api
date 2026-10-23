package com.taskflow.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatedDocumentationMatchesTheTaskApi() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(body);
        assertThat(document.path("info").path("title").asText()).isEqualTo("TaskFlow API");
        var paths = document.path("paths");
        assertThat(paths.size()).isEqualTo(2);
        var collection = paths.path("/api/tasks");
        var item = paths.path("/api/tasks/{id}");
        assertThat(collection.size()).isEqualTo(2);
        assertThat(item.size()).isEqualTo(3);
        assertThat(collection.path("get").path("responses").has("200")).isTrue();
        var listContent = collection.path("get").path("responses").path("200").path("content");
        assertThat(listContent.size()).isEqualTo(1);
        var listSchema = listContent.path("application/json").path("schema");
        assertThat(listSchema.path("type").asText()).isEqualTo("array");
        assertThat(listSchema.path("items").path("$ref").asText()).isEqualTo("#/components/schemas/TaskResponse");
        assertThat(collection.path("post").path("responses").has("201")).isTrue();
        assertThat(collection.path("post").path("responses").has("200")).isFalse();
        assertThat(collection.path("post").path("responses").path("201").path("headers").has("Location")).isTrue();
        for (String method : Set.of("get", "put", "delete")) {
            assertThat(item.path(method).path("responses").has("404")).isTrue();
        }
        assertThat(item.path("get").path("responses").has("200")).isTrue();
        assertThat(item.path("put").path("responses").has("200")).isTrue();
        for (String method : Set.of("get", "put")) {
            var taskContent = item.path(method).path("responses").path("200").path("content");
            assertThat(taskContent.size()).isEqualTo(1);
            assertThat(taskContent.path("application/json").path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/TaskResponse");
        }
        assertThat(item.path("delete").path("responses").has("204")).isTrue();
        assertThat(item.path("delete").path("responses").path("204").has("content")).isFalse();

        Set<String> filters = new HashSet<>();
        for (var parameter : collection.path("get").path("parameters")) {
            filters.add(parameter.path("name").asText());
            assertThat(parameter.path("required").asBoolean()).isFalse();
            assertThat(parameter.path("schema").path("enum").size()).isEqualTo(3);
        }
        assertThat(filters).containsExactlyInAnyOrder("status", "priority");

        var schemas = document.path("components").path("schemas");
        var request = schemas.path("TaskRequest").path("properties");
        assertThat(request.path("title").path("minLength").asInt()).isEqualTo(3);
        assertThat(request.path("title").path("maxLength").asInt()).isEqualTo(100);
        assertThat(request.path("dueDate").path("format").asText()).isEqualTo("date");
        assertThat(request.has("createdAt")).isFalse();
        var response = schemas.path("TaskResponse").path("properties");
        assertThat(response.path("createdAt").path("format").asText()).isEqualTo("date-time");
        assertThat(response.path("updatedAt").path("format").asText()).isEqualTo("date-time");
    }

    @Test
    void swaggerUiEntryPointAndResourcesAreAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/swagger-ui/index.html")));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mockMvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/v3/api-docs/swagger-config")));
    }
}
