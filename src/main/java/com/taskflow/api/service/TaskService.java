package com.taskflow.api.service;

import com.taskflow.api.dto.TaskRequest;
import com.taskflow.api.dto.TaskResponse;
import com.taskflow.api.exception.TaskNotFoundException;
import com.taskflow.api.model.Task;
import com.taskflow.api.model.TaskPriority;
import com.taskflow.api.model.TaskStatus;
import com.taskflow.api.repository.TaskRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<TaskResponse> getTasks(TaskStatus status, TaskPriority priority) {
        List<Task> tasks;
        if (status != null && priority != null) {
            tasks = taskRepository.findByStatusAndPriorityOrderByIdAsc(status, priority);
        } else if (status != null) {
            tasks = taskRepository.findByStatusOrderByIdAsc(status);
        } else if (priority != null) {
            tasks = taskRepository.findByPriorityOrderByIdAsc(priority);
        } else {
            tasks = taskRepository.findAll(Sort.by("id"));
        }
        return tasks.stream().map(TaskResponse::from).toList();
    }

    public TaskResponse getTask(Long id) {
        return TaskResponse.from(findTask(id));
    }

    @Transactional
    public TaskResponse createTask(TaskRequest request) {
        Task task = new Task(request.title(), request.description(), request.status(), request.priority(), request.dueDate());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskRequest request) {
        Task task = findTask(id);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        // Flush before mapping so the response includes the timestamp set by @PreUpdate.
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = findTask(id);
        taskRepository.delete(task);
    }

    private Task findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
