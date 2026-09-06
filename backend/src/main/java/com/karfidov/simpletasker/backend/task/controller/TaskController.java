package com.karfidov.simpletasker.backend.task.controller;

import com.karfidov.simpletasker.backend.common.web.request.param.SortMode;
import com.karfidov.simpletasker.backend.common.web.response.body.PageResponse;
import com.karfidov.simpletasker.backend.task.dto.response.TaskShortDto;
import com.karfidov.simpletasker.backend.task.model.TaskStatus;
import com.karfidov.simpletasker.backend.task.web.request.param.TaskSortField;
import com.karfidov.simpletasker.backend.task.dto.request.TaskRequestDto;
import com.karfidov.simpletasker.backend.task.dto.response.TaskFullDto;
import com.karfidov.simpletasker.backend.task.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@AllArgsConstructor
@Validated
public class TaskController {
    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskFullDto create(@RequestBody @Valid TaskRequestDto dto) {
        return taskService.create(dto);
    }

    @PatchMapping("/{taskId}")
    public TaskFullDto update(@PathVariable long taskId,
                              @RequestBody @Valid TaskRequestDto dto) {
        return taskService.update(taskId, dto);
    }

    @PostMapping("/{taskId}/start")
    public TaskFullDto startTask(@PathVariable long taskId) {
        return taskService.startTask(taskId);
    }

    @PostMapping("/{taskId}/complete")
    public TaskFullDto completeTask(@PathVariable long taskId) {
        return taskService.completeTask(taskId);
    }

    @GetMapping
    public PageResponse<TaskShortDto> getAllTasks(@RequestParam(defaultValue = "CREATED_AT") TaskSortField sortBy,
                                                  @RequestParam(defaultValue = "DESC") SortMode sortMode,
                                                  @RequestParam(required = false) TaskStatus status,
                                                  @Min(0)@RequestParam(defaultValue = "0") int page,
                                                  @Positive @Max(100) @RequestParam(defaultValue = "10") int size) {
        return taskService.getAllTasks(sortBy, sortMode, status, page, size);
    }

    @GetMapping("/{taskId}")
    public TaskFullDto getTask(@PathVariable long taskId) {
        return taskService.getTask(taskId);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable long taskId) {
        taskService.deleteTask(taskId);
    }
}
