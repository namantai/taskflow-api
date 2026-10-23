package com.example.taskflow.service;

import com.example.taskflow.dto.TaskRequest;
import com.example.taskflow.dto.TaskResponse;
import com.example.taskflow.exception.TaskNotFoundException;
import com.example.taskflow.model.Task;
import com.example.taskflow.model.TaskPriority;
import com.example.taskflow.model.TaskStatus;
import com.example.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createTaskSavesTheRequestedFields() {
        TaskRequest request = new TaskRequest("Learn JPA", "Read about entities", TaskStatus.TODO, TaskPriority.HIGH);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.createTask(request);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();
        assertThat(saved.getId()).isNull(); // The database assigns the id, not the service.
        assertThat(saved.getTitle()).isEqualTo("Learn JPA");
        assertThat(saved.getDescription()).isEqualTo("Read about entities");
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(saved.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(response).isEqualTo(TaskResponse.from(saved));
    }

    @Test
    void getTaskReturnsTheFoundTask() {
        Task task = exampleTask();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThat(taskService.getTask(1L)).isEqualTo(TaskResponse.from(task));
    }

    @Test
    void getTaskThrowsWhenMissing() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTask(99L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with id 99 was not found");
    }

    @Test
    void getTasksWithoutFiltersUsesIdOrder() {
        Task task = exampleTask();
        when(taskRepository.findAll(Sort.by("id"))).thenReturn(List.of(task));

        assertThat(taskService.getTasks(null, null)).containsExactly(TaskResponse.from(task));
    }

    @Test
    void getTasksFiltersByStatus() {
        Task task = exampleTask();
        when(taskRepository.findByStatusOrderByIdAsc(TaskStatus.TODO)).thenReturn(List.of(task));

        assertThat(taskService.getTasks(TaskStatus.TODO, null)).containsExactly(TaskResponse.from(task));
    }

    @Test
    void getTasksFiltersByPriority() {
        Task task = exampleTask();
        when(taskRepository.findByPriorityOrderByIdAsc(TaskPriority.HIGH)).thenReturn(List.of(task));

        assertThat(taskService.getTasks(null, TaskPriority.HIGH)).containsExactly(TaskResponse.from(task));
    }

    @Test
    void getTasksCombinesBothFilters() {
        Task task = exampleTask();
        when(taskRepository.findByStatusAndPriorityOrderByIdAsc(TaskStatus.TODO, TaskPriority.HIGH))
                .thenReturn(List.of(task));

        assertThat(taskService.getTasks(TaskStatus.TODO, TaskPriority.HIGH))
                .containsExactly(TaskResponse.from(task));
    }

    @Test
    void updateTaskReplacesAllEditableFields() {
        Task task = exampleTask();
        TaskRequest request = new TaskRequest("Finished", null, TaskStatus.DONE, TaskPriority.LOW);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateTask(1L, request);

        assertThat(response.title()).isEqualTo("Finished");
        assertThat(response.description()).isNull();
        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
        assertThat(response.priority()).isEqualTo(TaskPriority.LOW);
        verify(taskRepository).save(task);
    }

    @Test
    void updateTaskDoesNotCreateAMissingTask() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());
        TaskRequest request = new TaskRequest("Missing", null, TaskStatus.TODO, TaskPriority.LOW);

        assertThatThrownBy(() -> taskService.updateTask(99L, request))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void deleteTaskDeletesTheFoundTask() {
        Task task = exampleTask();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        taskService.deleteTask(1L);

        verify(taskRepository).delete(task);
    }

    @Test
    void deleteTaskThrowsWhenMissing() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteTask(99L))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }

    private Task exampleTask() {
        return new Task("Learn Spring", "Build a REST API", TaskStatus.TODO, TaskPriority.HIGH);
    }
}
