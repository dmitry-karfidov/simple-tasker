package com.karfidov.simpletasker.backend.task.service;

import com.karfidov.simpletasker.backend.common.web.request.param.SortMode;
import com.karfidov.simpletasker.backend.common.web.response.body.PageResponse;
import com.karfidov.simpletasker.backend.error.exception.ConditionsNotMetException;
import com.karfidov.simpletasker.backend.error.exception.NotFoundException;
import com.karfidov.simpletasker.backend.error.reasons_and_messages.ExceptionMessages;
import com.karfidov.simpletasker.backend.task.dto.request.TaskRequestDto;
import com.karfidov.simpletasker.backend.task.dto.response.TaskFullDto;
import com.karfidov.simpletasker.backend.task.dto.response.TaskShortDto;
import com.karfidov.simpletasker.backend.task.mapper.TaskMapper;
import com.karfidov.simpletasker.backend.task.model.Task;
import com.karfidov.simpletasker.backend.task.model.TaskStatus;
import com.karfidov.simpletasker.backend.task.repository.TaskRepository;
import com.karfidov.simpletasker.backend.task.web.request.param.TaskSortField;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;


@AllArgsConstructor
@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;

    private final Clock clock;

    @Transactional
    public TaskFullDto create(TaskRequestDto dto) {
        Task task = taskMapper.fromTaskRequestDto(dto);
        task.setStatus(TaskStatus.NEW);
        task.setCreatedAt(Instant.now(clock));
        Task saved = taskRepository.save(task);

        return taskMapper.toFullDto(saved);
    }

    @Transactional(readOnly = true)
    public TaskFullDto getTask(long taskId) {
        Task task = getTaskOrThrow404(taskId);
        return taskMapper.toFullDto(task);
    }

    @Transactional
    public TaskFullDto update(long taskId, TaskRequestDto dto) {
        Task task = getTaskOrThrow404(taskId);
        if (dto.getTitle() != null) {
            task.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            task.setDescription(dto.getDescription());
        }
        return taskMapper.toFullDto(task);
    }

    @Transactional
    public TaskFullDto startTask(long taskId) {
        Task task = getTaskOrThrow404(taskId);
        if (task.getStatus() != TaskStatus.NEW) {
            throw new ConditionsNotMetException(ExceptionMessages.TASK_IS_ALREADY_STARTED_OR_DONE);
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        return taskMapper.toFullDto(task);
    }

    @Transactional
    public TaskFullDto completeTask(long taskId) {
        Task task = getTaskOrThrow404(taskId);
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new ConditionsNotMetException(ExceptionMessages.TASK_IS_NOT_IN_PROGRESS);
        }
        task.setStatus(TaskStatus.DONE);
        return taskMapper.toFullDto(task);
    }

    @Transactional
    public void deleteTask(long taskId) {
        int deleted = taskRepository.deleteTaskById(taskId);

        if (deleted == 0) {
            throw new NotFoundException(String.format(ExceptionMessages.TASK_NOT_FOUND, taskId));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskShortDto> getAllTasks(TaskSortField sortBy,
                                                  SortMode sortMode,
                                                  TaskStatus status,
                                                  int page,
                                                  int size) {


        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortMode.getDirection(), sortBy.getProperty())
        );

        Page<Task> tasksPage;

        if (status != null) {
            tasksPage = taskRepository.findByStatus(status, pageable);
        } else {
            tasksPage = taskRepository.findAll(pageable);
        }

        return new PageResponse<>(
                taskMapper.toShortDtos(tasksPage.getContent()),
                tasksPage.getNumber(),
                tasksPage.getSize(),
                tasksPage.getTotalElements(),
                tasksPage.getTotalPages()
        );
    }

    private Task getTaskOrThrow404(long taskId) {
        return taskRepository.findById(taskId).orElseThrow(
                () -> new NotFoundException(String.format(ExceptionMessages.TASK_NOT_FOUND, taskId)));
    }
}
