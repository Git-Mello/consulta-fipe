package br.com.consultafipe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConsultaFipeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultaFipeApplication.class, args);
    }
}
