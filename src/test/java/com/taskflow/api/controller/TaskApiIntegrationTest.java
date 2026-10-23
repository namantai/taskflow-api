package com.taskflow.api.controller;

import com.taskflow.api.dto.TaskRequest;
import com.taskflow.api.model.Task;
import com.taskflow.api.model.TaskPriority;
import com.taskflow.api.model.TaskStatus;
import com.taskflow.api.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
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
        LocalDate dueDate = LocalDate.of(2026, 11, 15);
        TaskRequest request = new TaskRequest("Learn Spring", "Build TaskFlow", TaskStatus.TODO, TaskPriority.HIGH, dueDate);

        String body = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Learn Spring"))
                .andExpect(jsonPath("$.description").value("Build TaskFlow"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-15"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).path("id").longValue();
        Task created = taskRepository.findById(id).orElseThrow();
        Instant createdAt = Instant.parse(objectMapper.readTree(body).path("createdAt").asText());
        assertThat(created.getCreatedAt()).isEqualTo(createdAt);
        assertThat(created.getUpdatedAt()).isEqualTo(createdAt);
        assertThat(created.getDueDate()).isEqualTo(dueDate);

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().json(body));

        TaskRequest update = new TaskRequest("Finished project", null, TaskStatus.DONE, TaskPriority.LOW, null);
        String updatedBody = mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Finished project"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.dueDate").isEmpty())
                .andReturn().getResponse().getContentAsString();
        Task persisted = taskRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Finished project");
        assertThat(persisted.getDescription()).isNull();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(persisted.getPriority()).isEqualTo(TaskPriority.LOW);
        assertThat(persisted.getDueDate()).isNull();
        assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
        assertThat(persisted.getUpdatedAt()).isAfter(createdAt);
        assertThat(Instant.parse(objectMapper.readTree(updatedBody).path("updatedAt").asText()))
                .isEqualTo(persisted.getUpdatedAt());
        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().json(updatedBody));

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
                .andExpect(jsonPath("$.dueDate").isEmpty())
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
                assertThat(task.path("status").asText()).isEqualTo("TODO");
            }
            if (query.contains("priority=HIGH")) {
                assertThat(task.path("priority").asText()).isEqualTo("HIGH");
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
                Arguments.of(new TaskRequest(null, null, TaskStatus.TODO, TaskPriority.LOW, null), "title"),
                Arguments.of(new TaskRequest("   ", null, TaskStatus.TODO, TaskPriority.LOW, null), "title"),
                Arguments.of(new TaskRequest("ab", null, TaskStatus.TODO, TaskPriority.LOW, null), "title"),
                Arguments.of(new TaskRequest("x".repeat(101), null, TaskStatus.TODO, TaskPriority.LOW, null), "title"),
                Arguments.of(new TaskRequest("Valid", "x".repeat(2001), TaskStatus.TODO, TaskPriority.LOW, null), "description"),
                Arguments.of(new TaskRequest("Valid", null, null, TaskPriority.LOW, null), "status"),
                Arguments.of(new TaskRequest("Valid", null, TaskStatus.TODO, null, null), "priority")
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 100})
    void titleBoundariesAndMaximumDescriptionLengthAreAccepted(int titleLength) throws Exception {
        TaskRequest request = new TaskRequest("x".repeat(titleLength), "x".repeat(2000), TaskStatus.TODO, TaskPriority.LOW, null);
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
                                {"title":"   ","status":"DONE","priority":"LOW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Title is required"));
        Task unchanged = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Keep this title");
        assertThat(unchanged.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(unchanged.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(task.getUpdatedAt());
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

    @Test
    void updateCanSetANewDueDate() throws Exception {
        Task task = saveTask("Plan the demo", TaskStatus.TODO, TaskPriority.HIGH);
        mockMvc.perform(put("/api/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plan the demo","status":"IN_PROGRESS","priority":"HIGH","dueDate":"2026-12-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2026-12-01"));
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getDueDate())
                .isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "01-12-2026", "not-a-date"})
    void invalidDueDatesReturn400(String dueDate) throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plan the demo","status":"TODO","priority":"HIGH","dueDate":"%s"}
                                """.formatted(dueDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void clientsCannotSetServerManagedFields() throws Exception {
        String body = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999999,"title":"Server owns timestamps","status":"TODO","priority":"LOW",
                                 "createdAt":"2000-01-01T00:00:00Z","updatedAt":"2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var response = objectMapper.readTree(body);
        assertThat(response.path("id").longValue()).isNotEqualTo(999999L);
        assertThat(Instant.parse(response.path("createdAt").asText()))
                .isAfter(Instant.parse("2000-01-01T00:00:00Z"));
        assertThat(response.path("updatedAt").asText()).isEqualTo(response.path("createdAt").asText());
    }

    private Task saveTask(String title, TaskStatus status, TaskPriority priority) {
        return taskRepository.save(new Task(title, null, status, priority, null));
    }
}
