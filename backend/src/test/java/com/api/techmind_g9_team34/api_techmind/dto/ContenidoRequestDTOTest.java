package com.api.techmind_g9_team34.api_techmind.dto;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContenidoRequestDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void deberiaFallarCuandoTituloEstaVacio() {
        ContenidoRequestDTO dto = new ContenidoRequestDTO("", "Texto con al menos veinte caracteres.");
        Set<ConstraintViolation<ContenidoRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("titulo"));
    }

    @Test
    void deberiaFallarCuandoTextoEstaVacio() {
        ContenidoRequestDTO dto = new ContenidoRequestDTO("Título válido", "");
        Set<ConstraintViolation<ContenidoRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("texto"));
    }

    @Test
    void deberiaFallarCuandoTextoEsMenorDe20Caracteres() {
        ContenidoRequestDTO dto = new ContenidoRequestDTO("Título válido", "corto");
        Set<ConstraintViolation<ContenidoRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("texto"));
    }

    @Test
    void deberiaFallarCuandoTituloExcede200Caracteres() {
        String tituloLargo = "a".repeat(201);
        ContenidoRequestDTO dto = new ContenidoRequestDTO(tituloLargo, "Texto con al menos veinte caracteres.");
        Set<ConstraintViolation<ContenidoRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("titulo"));
    }

    @Test
    void deberiaPasarConDatosValidos() {
        ContenidoRequestDTO dto = new ContenidoRequestDTO(
                "Introducción a Spring Boot",
                "En este contenido se presentan los conceptos básicos para la creación de APIs REST."
        );
        Set<ConstraintViolation<ContenidoRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }
}