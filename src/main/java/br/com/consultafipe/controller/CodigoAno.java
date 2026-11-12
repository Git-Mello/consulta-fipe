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

/** Código de ano/combustível da FIPE: ano com 4 ou 5 dígitos, hífen e código do combustível (ex.: {@code 2018-5}). */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "\\d{4,5}-\\d{1,2}")
@ReportAsSingleViolation
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodigoAno {

    String message() default "deve estar no formato ano-combustível (ex.: 2018-5)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
