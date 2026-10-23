package com.taskflow.api.dto;

import com.taskflow.api.model.TaskPriority;
import com.taskflow.api.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// The same fields are required when creating a task and fully updating one.
public record TaskRequest(
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
        @Schema(example = "Implement REST API endpoints")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        @Schema(example = "Add task management features and validation")
        String description,

        @NotNull(message = "Status is required")
        @Schema(example = "TODO")
        TaskStatus status,

        @NotNull(message = "Priority is required")
        @Schema(example = "HIGH")
        TaskPriority priority,

        @Schema(example = "2026-11-15", description = "Optional due date in YYYY-MM-DD format; past dates are allowed")
        LocalDate dueDate
) {
}
