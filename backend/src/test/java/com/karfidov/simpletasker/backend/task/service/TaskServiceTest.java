package com.karfidov.simpletasker.backend.task.service;

import com.karfidov.simpletasker.backend.common.web.request.param.SortMode;
import com.karfidov.simpletasker.backend.error.exception.ConditionsNotMetException;
import com.karfidov.simpletasker.backend.error.exception.NotFoundException;
import com.karfidov.simpletasker.backend.error.reasons_and_messages.ExceptionMessages;
import com.karfidov.simpletasker.backend.support.TestTime;
import com.karfidov.simpletasker.backend.task.dto.request.TaskRequestDto;
import com.karfidov.simpletasker.backend.task.dto.response.TaskFullDto;
import com.karfidov.simpletasker.backend.task.builder.TaskRequestDtoTestBuilder;
import com.karfidov.simpletasker.backend.task.builder.TaskTestBuilder;
import com.karfidov.simpletasker.backend.task.mapper.TaskMapper;
import com.karfidov.simpletasker.backend.task.model.Task;
import com.karfidov.simpletasker.backend.task.model.TaskStatus;
import com.karfidov.simpletasker.backend.task.repository.TaskRepository;
import com.karfidov.simpletasker.backend.task.web.request.param.TaskSortField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskRepository taskRepository;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskMapper, TestTime.DEFAULT_CLOCK);
    }

    @Test
    void create_shouldSetStatusAndCreatedAt_andSaveTask() {
        TaskRequestDto defaultRequestDto = TaskRequestDtoTestBuilder.aRequestDto().build();

        Task taskBeforeSave = TaskTestBuilder.aTask()
                .withoutId()
                .withStatus(null)
                .withCreatedAt(null)
                .build();

        Task taskAfterSave = TaskTestBuilder.aTask()
                .withId(1L)
                .withStatus(TaskStatus.NEW)
                .withCreatedAt(TestTime.DEFAULT_INSTANT)
                .build();

        TaskFullDto responseDto = mock(TaskFullDto.class);

        when(taskMapper.fromTaskRequestDto(defaultRequestDto)).thenReturn(taskBeforeSave);
        when(taskRepository.save(any(Task.class))).thenReturn(taskAfterSave);
        when(taskMapper.toFullDto(taskAfterSave)).thenReturn(responseDto);

        TaskFullDto result = taskService.create(defaultRequestDto);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());

        Task actualTaskForSave = captor.getValue();

        assertThat(actualTaskForSave).isSameAs(taskBeforeSave);
        assertThat(actualTaskForSave.getStatus()).isEqualTo(TaskStatus.NEW);
        assertThat(actualTaskForSave.getCreatedAt()).isEqualTo(TestTime.DEFAULT_INSTANT);
        assertThat(result).isSameAs(responseDto);
    }

    @Test
    void getTask_shouldReturnExistingTask() {
        Task defaultTask = TaskTestBuilder.aTask().build();

        TaskFullDto responseDto = mock(TaskFullDto.class);

        when(taskRepository.findById(defaultTask.getId())).thenReturn(Optional.of(defaultTask));
        when(taskMapper.toFullDto(defaultTask)).thenReturn(responseDto);

        TaskFullDto result = taskService.getTask(defaultTask.getId());

        assertThat(result).isSameAs(responseDto);
        verify(taskRepository).findById(defaultTask.getId());
        verify(taskMapper).toFullDto(defaultTask);
    }

    @Test
    void getTask_shouldThrowNotFoundException_whenTaskDoesNotExist() {
        long taskId = 1L;

        when(taskRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTask(taskId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(String.format(ExceptionMessages.TASK_NOT_FOUND, taskId));
    }

    @Test
    void update_shouldUpdateTitleAndDescription() {
        String oldTitle = "Old Title";
        String oldDescription = "Old Description";
        String newTitle = "New Title";
        String newDescription = "New Description";

        Task taskForUpdate = TaskTestBuilder.aTask()
                .withTitle(oldTitle)
                .withDescription(oldDescription)
                .build();

        TaskRequestDto updateDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle(newTitle)
                .withDescription(newDescription)
                .build();

        TaskFullDto responseDto = mock(TaskFullDto.class);

        when(taskRepository.findById(taskForUpdate.getId())).thenReturn(Optional.of(taskForUpdate));
        when(taskMapper.toFullDto(taskForUpdate)).thenReturn(responseDto);

        TaskFullDto result = taskService.update(taskForUpdate.getId(), updateDto);

        assertThat(taskForUpdate.getTitle()).isEqualTo(newTitle);
        assertThat(taskForUpdate.getDescription()).isEqualTo(newDescription);
        assertThat(result).isSameAs(responseDto);
    }

    @Test
    void update_shouldUpdateTitleOnly_whenDescriptionIsNull() {
        String oldTitle = "Old Title";
        String oldDescription = "Old Description";
        String newTitle = "New Title";

        Task taskForUpdate = TaskTestBuilder.aTask()
                .withTitle(oldTitle)
                .withDescription(oldDescription)
                .build();

        TaskRequestDto updateDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle(newTitle)
                .withDescription(null)
                .build();

        TaskFullDto responseDto = mock(TaskFullDto.class);

        when(taskRepository.findById(taskForUpdate.getId())).thenReturn(Optional.of(taskForUpdate));
        when(taskMapper.toFullDto(taskForUpdate)).thenReturn(responseDto);

        TaskFullDto result = taskService.update(taskForUpdate.getId(), updateDto);

        assertThat(taskForUpdate.getTitle()).isEqualTo(newTitle);
        assertThat(taskForUpdate.getDescription()).isEqualTo(oldDescription);
        assertThat(result).isSameAs(responseDto);
    }

    @Test
    void update_shouldUpdateDescriptionOnly_whenTitleIsNull() {
        String oldTitle = "Old Title";
        String oldDescription = "Old Description";
        String newDescription = "New Description";

        Task taskForUpdate = TaskTestBuilder.aTask()
                .withTitle(oldTitle)
                .withDescription(oldDescription)
                .build();

        TaskRequestDto updateDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle(null)
                .withDescription(newDescription)
                .build();

        TaskFullDto responseDto = mock(TaskFullDto.class);

        when(taskRepository.findById(taskForUpdate.getId())).thenReturn(Optional.of(taskForUpdate));
        when(taskMapper.toFullDto(taskForUpdate)).thenReturn(responseDto);

        TaskFullDto result = taskService.update(taskForUpdate.getId(), updateDto);

        assertThat(taskForUpdate.getTitle()).isEqualTo(oldTitle);
        assertThat(taskForUpdate.getDescription()).isEqualTo(newDescription);
        assertThat(result).isSameAs(responseDto);
    }

    @Test
    void update_shouldNotUpdateAnyField_whenAllDtoFieldsAreNull() {
        String oldTitle = "Old Title";
        String oldDescription = "Old Description";

        Task taskForUpdate = TaskTestBuilder.aTask()
                .withTitle(oldTitle)
                .withDescription(oldDescription)
                .build();

        TaskRequestDto updateDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle(null)
                .withDescription(null)
                .build();

        TaskFullDto responseDto = mock(TaskFullDto.class);

        when(taskRepository.findById(taskForUpdate.getId())).thenReturn(Optional.of(taskForUpdate));
        when(taskMapper.toFullDto(taskForUpdate)).thenReturn(responseDto);

        TaskFullDto result = taskService.update(taskForUpdate.getId(), updateDto);

        assertThat(taskForUpdate.getTitle()).isEqualTo(oldTitle);
        assertThat(taskForUpdate.getDescription()).isEqualTo(oldDescription);
        assertThat(result).isSameAs(responseDto);
    }

    @Test
    void update_shouldThrowNotFoundException_whenTaskDoesNotExist() {
        long taskId = 1L;
        TaskRequestDto defaultRequestDto = TaskRequestDtoTestBuilder.aRequestDto().build();

        when(taskRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(taskId, defaultRequestDto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(String.format(ExceptionMessages.TASK_NOT_FOUND, taskId));
    }

    @Test
    void startTask_shouldStartNewTask() {
        Task taskForStart = TaskTestBuilder.aTask()
                .withStatus(TaskStatus.NEW)
                .build();
        TaskFullDto responseDto = mock(TaskFullDto.class);

        when(taskRepository.findById(taskForStart.getId())).thenReturn(Optional.of(taskForStart));
        when(taskMapper.toFullDto(taskForStart)).thenReturn(responseDto);

        TaskFullDto result = taskService.startTask(taskForStart.getId());

        assertThat(taskForStart.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(result).isSameAs(responseDto);
    }


    @ParameterizedTest(name = "Start task with status {0} should be impossible")
    @EnumSource(
            value = TaskStatus.class,
            names = {"IN_PROGRESS", "DONE"}
    )
    void startTask_shouldThrowConditionsNotMetException_whenTaskStatusIsNotNew(TaskStatus initialStatus) {
        Task task = TaskTestBuilder.aTask()
                .withStatus(initialStatus)
                .build();

        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.startTask(task.getId()))
                .isInstanceOf(ConditionsNotMetException.class)
                .hasMessage(ExceptionMessages.TASK_IS_ALREADY_STARTED_OR_DONE);

        assertThat(task.getStatus()).isEqualTo(initialStatus);
    }

    @Test
    void startTask_shouldThrowNotFoundException_whenTaskDoesNotExist() {
        long taskId = 1L;

        when(taskRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.startTask(taskId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(String.format(ExceptionMessages.TASK_NOT_FOUND, taskId));
    }

    @Test
    void completeTask_shouldCompleteTaskInProgress() {
        Task taskForComplete = TaskTestBuilder.aTask()
                .withStatus(TaskStatus.IN_PROGRESS)
                .build();
        TaskFullDto responseDto = mock(TaskFullDto.class);

        when(taskRepository.findById(taskForComplete.getId())).thenReturn(Optional.of(taskForComplete));
        when(taskMapper.toFullDto(taskForComplete)).thenReturn(responseDto);

        TaskFullDto result = taskService.completeTask(taskForComplete.getId());

        assertThat(taskForComplete.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(result).isSameAs(responseDto);
    }

    @ParameterizedTest(name = "Complete Task with status = {0} should be impossible")
    @EnumSource(
            value = TaskStatus.class,
            names = {"NEW", "DONE"}
    )
    void completeTask_shouldThrowConditionsNotMetException_whenTaskStatusIsNotInProgress(TaskStatus initialStatus) {
        Task task = TaskTestBuilder.aTask()
                .withStatus(initialStatus)
                .build();

        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.completeTask(task.getId()))
                .isInstanceOf(ConditionsNotMetException.class)
                .hasMessage(ExceptionMessages.TASK_IS_NOT_IN_PROGRESS);

        assertThat(task.getStatus()).isEqualTo(initialStatus);
    }

    @Test
    void completeTask_shouldThrowNotFoundException_whenTaskDoesNotExist() {
        long taskId = 1L;

        when(taskRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.completeTask(taskId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(String.format(ExceptionMessages.TASK_NOT_FOUND, taskId));
    }

    @Test
    void deleteTask_shouldDeleteExistingTask() {
        long taskId = 1L;
        int rowsDeleted = 1;

        when(taskRepository.deleteTaskById(taskId)).thenReturn(rowsDeleted);

        taskService.deleteTask(taskId);

        verify(taskRepository).deleteTaskById(taskId);
    }

    @Test
    void deleteTask_shouldThrowNotFoundException_whenTaskDoesNotExist() {
        long taskId = 1L;
        int rowsDeleted = 0;

        when(taskRepository.deleteTaskById(taskId)).thenReturn(rowsDeleted);

        assertThatThrownBy(() -> taskService.deleteTask(taskId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(String.format(ExceptionMessages.TASK_NOT_FOUND, taskId));
    }

    @Test
    void getAllTasks_shouldFindByStatus_whenStatusProvided() {
        TaskSortField sortBy = TaskSortField.CREATED_AT;
        TaskStatus status = TaskStatus.IN_PROGRESS;
        SortMode sortMode = SortMode.DESC;
        int page = 0;
        int size = 10;

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortMode.getDirection(), sortBy.getProperty())
        );

        Page<Task> tasksPage = Page.empty(pageable);
        when(taskRepository.findByStatus(status, pageable)).thenReturn(tasksPage);

        taskService.getAllTasks(sortBy, sortMode, status, page, size);

        verify(taskRepository).findByStatus(status, pageable);
        verify(taskRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getAllTasks_shouldFindAllTasks_whenStatusIsNotProvided() {
        TaskSortField sortBy = TaskSortField.CREATED_AT;
        TaskStatus status = null;
        SortMode sortMode = SortMode.DESC;
        int page = 0;
        int size = 10;

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortMode.getDirection(), sortBy.getProperty())
        );

        Page<Task> tasksPage = Page.empty(pageable);
        when(taskRepository.findAll(pageable)).thenReturn(tasksPage);

        taskService.getAllTasks(sortBy, sortMode, status, page, size);

        verify(taskRepository).findAll(pageable);
        verify(taskRepository, never()).findByStatus(nullable(TaskStatus.class), any(Pageable.class));
    }
}
