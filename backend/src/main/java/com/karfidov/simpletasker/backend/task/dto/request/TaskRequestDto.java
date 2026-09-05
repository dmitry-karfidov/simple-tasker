package com.karfidov.simpletasker.backend.task.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.karfidov.simpletasker.backend.jsonutils.TrimStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public final class TaskRequestDto {

    @JsonDeserialize(using = TrimStringDeserializer.class)
    @NotBlank
    @Size(max = 127)
    private String title;

    @Size(max = 2047)
    private String description;
}
