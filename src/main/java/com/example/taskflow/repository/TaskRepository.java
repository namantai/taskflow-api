package com.example.taskflow.repository;

import com.example.taskflow.model.Task;
import com.example.taskflow.model.TaskPriority;
import com.example.taskflow.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Spring Data creates these queries from the method names.
    List<Task> findByStatusOrderByIdAsc(TaskStatus status);

    List<Task> findByPriorityOrderByIdAsc(TaskPriority priority);

    List<Task> findByStatusAndPriorityOrderByIdAsc(TaskStatus status, TaskPriority priority);
}
