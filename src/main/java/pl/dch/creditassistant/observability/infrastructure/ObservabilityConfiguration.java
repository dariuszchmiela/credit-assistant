package pl.dch.creditassistant.observability.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRecorder;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRepository;
import pl.dch.creditassistant.observability.application.AiCostCalculator;
import pl.dch.creditassistant.observability.application.AiInteractionRecorder;
import pl.dch.creditassistant.observability.application.AiInteractionRepository;
import pl.dch.creditassistant.observability.application.ToolInvocationRecorder;
import pl.dch.creditassistant.observability.application.ToolInvocationRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObservabilityProperties.class)
class ObservabilityConfiguration {

    @Bean
    AiCostCalculator aiCostCalculator(ObservabilityProperties properties) {
        return new AiCostCalculator(
                properties.cost().inputPerMillionTokens(),
                properties.cost().outputPerMillionTokens()
        );
    }

    @Bean
    AiInteractionRecorder aiInteractionRecorder(
            AiInteractionRepository aiInteractionRepository,
            AiCostCalculator aiCostCalculator
    ) {
        return new AiInteractionRecorder(aiInteractionRepository, aiCostCalculator);
    }

    @Bean
    AdvisorInteractionRecorder advisorInteractionRecorder(AdvisorInteractionRepository advisorInteractionRepository) {
        return new AdvisorInteractionRecorder(advisorInteractionRepository);
    }

    @Bean
    ToolInvocationRecorder toolInvocationRecorder(ToolInvocationRepository toolInvocationRepository) {
        return new ToolInvocationRecorder(toolInvocationRepository);
    }
}
