package com.localllm.correction;

import com.localllm.correction.core.Pipeline;
import com.localllm.correction.io.PipelineFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CorrectionApplication {
    public static void main(String[] args) {
        SpringApplication.run(CorrectionApplication.class, args);
    }

    @Bean
    Pipeline pipeline() {
        return PipelineFactory.fromEnvironment();
    }
}
