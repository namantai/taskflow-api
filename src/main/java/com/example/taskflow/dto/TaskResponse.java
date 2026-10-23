package com.example.taskflow.dto;

import com.example.taskflow.model.Task;
import com.example.taskflow.model.TaskPriority;
import com.example.taskflow.model.TaskStatus;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getPriority());
    }
}
