package com.karfidov.simpletasker.backend.task.integration;

import com.jayway.jsonpath.JsonPath;
import com.karfidov.simpletasker.backend.error.reasons_and_messages.ExceptionMessages;
import com.karfidov.simpletasker.backend.error.reasons_and_messages.ExceptionReasons;
import com.karfidov.simpletasker.backend.task.builder.TaskTestBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.*;


@AutoConfigureMockMvc
@Transactional
public class TaskIT extends AbstractIntegrationTest {
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
        int expectedAmountOfCreatedTasks = 1;

        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.description").value(expectedDescription))
                .andExpect(jsonPath("$.status").value(TaskStatus.NEW.name()))
                .andReturn();

        Long idFromResponse = Long.parseLong(
                JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString()
        );

        taskRepository.flush();

        List<Task> tasks = taskRepository.findAll();
        assertThat(tasks).hasSize(expectedAmountOfCreatedTasks);

        Task savedTask = tasks.getFirst();

        assertThat(savedTask.getTitle()).isEqualTo(expectedTitle);
        assertThat(savedTask.getDescription()).isEqualTo(expectedDescription);
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.NEW);
        assertThat(savedTask.getId()).isEqualTo(idFromResponse);
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
    void create_shouldTrimTitle_whenTitleContainsSpacesAtStartAndAtEnd() throws Exception {
        TaskRequestDto validRequestDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle("  Do something    ")
                .withDescription("Test Description")
                .build();

        String expectedTitle = "Do something";
        String expectedDescription = "Test Description";

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.description").value(expectedDescription))
                .andExpect(jsonPath("$.status").value(TaskStatus.NEW.name()));
    }

    @Test
    void create_shouldCreateTask_whenTitleIs127() throws Exception {
        String validTitle = "a".repeat(127);
        String expectedDescription = "Test Description";

        TaskRequestDto validRequestDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle(validTitle)
                .withDescription(expectedDescription)
                .build();

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.title").value(validTitle))
                .andExpect(jsonPath("$.description").value(expectedDescription))
                .andExpect(jsonPath("$.status").value(TaskStatus.NEW.name()));
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
                .withoutId()
                .withTitle("Old Title")
                .withDescription("Old Description")
                .build();

        TaskRequestDto validUpdateDto = TaskRequestDtoTestBuilder.aRequestDto()
                .withTitle("New Title")
                .withDescription("New Description")
                .build();

        String expectedTitle = validUpdateDto.getTitle();
        String expectedDescription = validUpdateDto.getDescription();
        int expectedAmountOfCreatedTasks = 1;

        taskRepository.save(existingTask);
        long taskId = existingTask.getId();

        MvcResult result = mockMvc.perform(patch("/api/v1/tasks/{id}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.description").value(expectedDescription))
                .andExpect(jsonPath("$.status").value(TaskStatus.NEW.name()))
                .andReturn();

        Long idFromResponse = Long.parseLong(
                JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString()
        );

        taskRepository.flush();

        List<Task> tasks = taskRepository.findAll();
        assertThat(tasks).hasSize(expectedAmountOfCreatedTasks);

        Task savedTask = tasks.getFirst();

        assertThat(savedTask.getTitle()).isEqualTo(expectedTitle);
        assertThat(savedTask.getDescription()).isEqualTo(expectedDescription);
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.NEW);
        assertThat(savedTask.getId()).isEqualTo(idFromResponse);
    }

    @Test
    void getAllTasks_shouldReturnCorrectPageResponse_whenSortModeSortOrderPageNumberAndSizeProvided() throws Exception {

        List<Task> existingTasks10 = createTasksInChronologicalOrder(
                TaskStatus.NEW,
                TaskStatus.IN_PROGRESS,
                TaskStatus.DONE,
                TaskStatus.NEW,
                TaskStatus.IN_PROGRESS,
                TaskStatus.DONE,
                TaskStatus.NEW,
                TaskStatus.IN_PROGRESS,
                TaskStatus.DONE,
                TaskStatus.NEW
        );

        existingTasks10.sort(Comparator.comparing(Task::getCreatedAt).reversed());

        taskRepository.saveAllAndFlush(existingTasks10);

        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("sortMode", "ASC");
        queryParams.add("page", "2");
        queryParams.add("size", "3");

        int expectedPageNumber = 2; //starts from 0 -> 0, 1, 2, 3 ...
        int expectedAmountOfObjectsAtThePage = 3;
        long expectedTotalElements = existingTasks10.size();
        int expectedTotalPages = 4;

        mockMvc.perform(get("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParams(queryParams))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(expectedPageNumber))
                .andExpect(jsonPath("$.size").value(expectedAmountOfObjectsAtThePage))
                .andExpect(jsonPath("$.totalElements").value(expectedTotalElements))
                .andExpect(jsonPath("$.totalPages").value(expectedTotalPages))
                .andExpect(jsonPath("$.items", hasSize(expectedAmountOfObjectsAtThePage)))
                .andExpect(jsonPath("$.items[0].id").isNumber())
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value(Instant.parse("2026-01-01T12:00:00Z")
                                .plus(6 * 10, ChronoUnit.MINUTES)
                                .toString()))
                .andExpect(jsonPath("$.items[*].title", contains(
                        "Test Title 7", "Test Title 8", "Test Title 9"
                )));
    }

    @Test
    void getAllTasks_shouldReturnCorrectPageResponse_whenStatusFilterIsProvidedAndDefaultSizePageSortModeSortField() throws Exception {

        List<Task> existingTasks20_InProgress6 = createTasksInChronologicalOrder(
                TaskStatus.NEW,
                TaskStatus.NEW,
                TaskStatus.NEW,
                TaskStatus.IN_PROGRESS,
                TaskStatus.IN_PROGRESS,
                TaskStatus.IN_PROGRESS,
                TaskStatus.IN_PROGRESS,
                TaskStatus.NEW,
                TaskStatus.NEW,
                TaskStatus.NEW,
                TaskStatus.IN_PROGRESS,
                TaskStatus.DONE,
                TaskStatus.IN_PROGRESS,
                TaskStatus.DONE,
                TaskStatus.DONE,
                TaskStatus.DONE,
                TaskStatus.DONE,
                TaskStatus.DONE,
                TaskStatus.DONE,
                TaskStatus.DONE
        );

        taskRepository.saveAllAndFlush(existingTasks20_InProgress6);

        int expectedPageNumber = 0; //starts from 0 -> 0, 1, 2, 3 ...
        int expectedPageSize = 10;
        long expectedTotalElements = 6L;
        int expectedTotalPages = 1;

        mockMvc.perform(get("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(expectedPageNumber))
                .andExpect(jsonPath("$.size").value(expectedPageSize))
                .andExpect(jsonPath("$.totalElements").value(expectedTotalElements))
                .andExpect(jsonPath("$.totalPages").value(expectedTotalPages))
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.items[*].status", everyItem(is("IN_PROGRESS"))))
                .andExpect(jsonPath("$.items[*].title", contains(
                        "Test Title 13", "Test Title 11", "Test Title 7",
                        "Test Title 6", "Test Title 5", "Test Title 4"
                )));

    }

    private List<Task> createTasksInChronologicalOrder(TaskStatus... statuses) {
        Instant baseTime = Instant.parse("2026-01-01T12:00:00Z");
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            Task task = TaskTestBuilder.aTask()
                    .withoutId()
                    .withTitle("Test Title " + (i + 1))
                    .withDescription("Test Description " + (i + 1))
                    .withCreatedAt(baseTime.plus(i * 10L, ChronoUnit.MINUTES))
                    .withStatus(statuses[i])
                    .build();
            tasks.add(task);
        }
        return tasks;
    }
}
