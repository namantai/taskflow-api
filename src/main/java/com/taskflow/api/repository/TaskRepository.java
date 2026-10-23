package com.taskflow.api.repository;

import com.taskflow.api.model.Task;
import com.taskflow.api.model.TaskPriority;
import com.taskflow.api.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Spring Data creates these queries from the method names.
    List<Task> findByStatusOrderByIdAsc(TaskStatus status);

    List<Task> findByPriorityOrderByIdAsc(TaskPriority priority);

    List<Task> findByStatusAndPriorityOrderByIdAsc(TaskStatus status, TaskPriority priority);
}
