package br.com.consultafipe.controller;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Código de marca ou de modelo da FIPE: só dígitos, até 6 (ex.: {@code 21}, {@code 7965}). */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "\\d{1,6}")
@ReportAsSingleViolation
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodigoNumerico {

    String message() default "deve conter apenas números (até 6 dígitos)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
