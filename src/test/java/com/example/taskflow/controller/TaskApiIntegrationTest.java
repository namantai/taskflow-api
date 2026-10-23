package com.example.taskflow.controller;

import com.example.taskflow.dto.TaskRequest;
import com.example.taskflow.model.Task;
import com.example.taskflow.model.TaskPriority;
import com.example.taskflow.model.TaskStatus;
import com.example.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// These requests go through the controller, service, repository, and a real H2 database.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void clearDatabase() {
        taskRepository.deleteAll();
    }

    @Test
    void taskCanBeCreatedReadUpdatedAndDeleted() throws Exception {
        TaskRequest request = new TaskRequest("Learn Spring", "Build TaskFlow", TaskStatus.TODO, TaskPriority.HIGH);

        String body = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Learn Spring"))
                .andExpect(jsonPath("$.description").value("Build TaskFlow"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).path("id").longValue();
        assertThat(taskRepository.findById(id)).isPresent();

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().json(body));

        TaskRequest update = new TaskRequest("Finished project", null, TaskStatus.DONE, TaskPriority.LOW);
        mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Finished project"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.priority").value("LOW"));
        Task persisted = taskRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Finished project");
        assertThat(persisted.getDescription()).isNull();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(persisted.getPriority()).isEqualTo(TaskPriority.LOW);

        mockMvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(taskRepository.existsById(id)).isFalse();
        mockMvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void createReturnsLocationAndAcceptsOptionalDescription() throws Exception {
        var result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plan work","status":"IN_PROGRESS","priority":"MEDIUM"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").isEmpty())
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").longValue();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/tasks/" + id);
    }

    @Test
    void listReturnsTasksInIdOrder() throws Exception {
        Task first = saveTask("First", TaskStatus.TODO, TaskPriority.HIGH);
        Task second = saveTask("Second", TaskStatus.DONE, TaskPriority.LOW);

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()));
    }

    @ParameterizedTest
    @CsvSource({
            "status=TODO, 2",
            "priority=HIGH, 2",
            "status=TODO&priority=HIGH, 1",
            "status=IN_PROGRESS&priority=LOW, 0"
    })
    void filtersAreAppliedInTheDatabase(String query, int expectedCount) throws Exception {
        saveTask("Todo high", TaskStatus.TODO, TaskPriority.HIGH);
        saveTask("Todo low", TaskStatus.TODO, TaskPriority.LOW);
        saveTask("Done high", TaskStatus.DONE, TaskPriority.HIGH);

        var result = mockMvc.perform(get("/api/tasks?" + query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expectedCount))
                .andReturn();
        var tasks = objectMapper.readTree(result.getResponse().getContentAsString());
        for (var task : tasks) {
            if (query.contains("status=TODO")) {
                assertThat(task.path("status").asString()).isEqualTo("TODO");
            }
            if (query.contains("priority=HIGH")) {
                assertThat(task.path("priority").asString()).isEqualTo("HIGH");
            }
        }
    }

    @Test
    void emptyDatabaseReturnsAnEmptyArray() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void missingTasksReturnProblemDetailsForReadUpdateAndDelete() throws Exception {
        mockMvc.perform(get("/api/tasks/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Task with id 999999 was not found"))
                .andExpect(jsonPath("$.instance").value("/api/tasks/999999"));
        mockMvc.perform(put("/api/tasks/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Missing","status":"TODO","priority":"LOW"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/999999"))
                .andExpect(status().isNotFound());
        assertThat(taskRepository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidFieldsReturn400WithoutSaving(TaskRequest request, String field) throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors." + field).isString());
        assertThat(taskRepository.count()).isZero();
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new TaskRequest(null, null, TaskStatus.TODO, TaskPriority.LOW), "title"),
                Arguments.of(new TaskRequest("   ", null, TaskStatus.TODO, TaskPriority.LOW), "title"),
                Arguments.of(new TaskRequest("x".repeat(201), null, TaskStatus.TODO, TaskPriority.LOW), "title"),
                Arguments.of(new TaskRequest("Valid", "x".repeat(2001), TaskStatus.TODO, TaskPriority.LOW), "description"),
                Arguments.of(new TaskRequest("Valid", null, null, TaskPriority.LOW), "status"),
                Arguments.of(new TaskRequest("Valid", null, TaskStatus.TODO, null), "priority")
        );
    }

    @Test
    void maximumFieldLengthsAreAccepted() throws Exception {
        TaskRequest request = new TaskRequest("x".repeat(200), "x".repeat(2000), TaskStatus.TODO, TaskPriority.LOW);
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        assertThat(taskRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidUpdateLeavesStoredTaskUnchanged() throws Exception {
        Task task = saveTask("Keep this title", TaskStatus.TODO, TaskPriority.HIGH);
        mockMvc.perform(put("/api/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","status":"DONE","priority":"LOW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Title is required"));
        Task unchanged = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Keep this title");
        assertThat(unchanged.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(unchanged.getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{broken", "", "null", "{}",
            "{\"title\":\"Task\",\"status\":\"CANCELLED\",\"priority\":\"HIGH\"}",
            "{\"title\":\"Task\",\"status\":\"TODO\",\"priority\":\"URGENT\"}",
            "{\"title\":\"Task\",\"status\":0,\"priority\":\"HIGH\"}",
            "{\"title\":\"Task\",\"status\":\"todo\",\"priority\":\"HIGH\"}"
    })
    void malformedOrIncompleteBodiesReturn400(String body) throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(taskRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/tasks?status=UNKNOWN", "/api/tasks?priority=URGENT",
            "/api/tasks?status=todo", "/api/tasks/not-a-number"
    })
    void invalidParametersReturn400(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unsupportedMethodAndContentTypeKeepTheirHttpStatus() throws Exception {
        mockMvc.perform(patch("/api/tasks/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    private Task saveTask(String title, TaskStatus status, TaskPriority priority) {
        return taskRepository.save(new Task(title, null, status, priority));
    }
}
