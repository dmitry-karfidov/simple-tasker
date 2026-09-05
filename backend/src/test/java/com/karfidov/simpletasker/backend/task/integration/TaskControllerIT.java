package com.karfidov.simpletasker.backend.task.integration;

import com.karfidov.simpletasker.backend.error.reasons_and_messages.ExceptionMessages;
import com.karfidov.simpletasker.backend.error.reasons_and_messages.ExceptionReasons;
import com.karfidov.simpletasker.backend.task.builder.TaskTestBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.karfidov.simpletasker.backend.support.AbstractIntegrationTest;
import com.karfidov.simpletasker.backend.task.dto.request.TaskRequestDto;
import com.karfidov.simpletasker.backend.task.builder.TaskRequestDtoTestBuilder;
import com.karfidov.simpletasker.backend.task.model.Task;
import com.karfidov.simpletasker.backend.task.model.TaskStatus;
import com.karfidov.simpletasker.backend.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.*;


@AutoConfigureMockMvc
@Transactional
public class TaskControllerIT extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void create_shouldReturnCreatedTaskAndPersistIt_whenRequestIsValid() throws Exception {
        TaskRequestDto validRequestDto = TaskRequestDtoTestBuilder.aRequestDto().build();
        String expectedTitle = validRequestDto.getTitle();
        String expectedDescription = validRequestDto.getDescription();

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.description").value(expectedDescription))
                .andExpect(jsonPath("$.status").value(TaskStatus.NEW.name()));

        List<Task> tasks = taskRepository.findAll();
        assertThat(tasks).hasSize(1);

        Task savedTask = tasks.getFirst();

        assertThat(savedTask.getTitle()).isEqualTo(expectedTitle);
        assertThat(savedTask.getDescription()).isEqualTo(expectedDescription);
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.NEW);
    }

    @Test
    void create_shouldReturn400_whenTitleIsBlankAfterTrim() throws Exception {
        TaskRequestDto notValidRequestDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle(" ")
                .withDescription("Test Description")
                .build();

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notValidRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.name()))
                .andExpect(jsonPath("$.reason").value(ExceptionReasons.INCORRECT_REQUEST))
                .andExpect(jsonPath("$.message").value(ExceptionMessages.VALIDATION_FAILED))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]", containsString("title")))
                .andExpect(jsonPath("$.errors[0]", containsString("must not be blank")));
    }

    @Test
    void create_shouldReturn400_whenTitleIsLongerThan127() throws Exception {
        String notValidTitle = "a".repeat(128);

        TaskRequestDto notValidRequestDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle(notValidTitle)
                .withDescription("Test Description")
                .build();

        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(notValidRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value(ExceptionReasons.INCORRECT_REQUEST))
                .andExpect(jsonPath("$.message").value(ExceptionMessages.VALIDATION_FAILED))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]", containsString("title")))
                .andExpect(jsonPath("$.errors[0]", containsString("size must be between 0 and 127")));

    }

    @Test
    void update_shouldReturnUpdatedTaskAndPersistIt_whenRequestIsValid() throws Exception {
        Task existingTask = TaskTestBuilder.aTask()
                .withId(null)
                .withTitle("Old Title")
                .withDescription("Old Description")
                .build();

        TaskRequestDto validUpdateDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle("New Title")
                .withDescription("New Description")
                .build();

        String expectedTitle = validUpdateDto.getTitle();
        String expectedDescription = validUpdateDto.getDescription();

        taskRepository.save(existingTask);
        long taskId = existingTask.getId();

        mockMvc.perform(patch("/api/v1/tasks/{id}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.description").value(expectedDescription))
                .andExpect(jsonPath("$.status").value(TaskStatus.NEW.name()));

    }
}
