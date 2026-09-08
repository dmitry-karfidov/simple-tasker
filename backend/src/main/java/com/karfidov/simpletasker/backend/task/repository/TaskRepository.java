package com.karfidov.simpletasker.backend.task.repository;

import com.karfidov.simpletasker.backend.task.model.Task;


import com.karfidov.simpletasker.backend.task.model.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.id = :id")
    int deleteTaskById(@Param("id") long id);
}
