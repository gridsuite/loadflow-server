package org.gridsuite.loadflow.server.config;

import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RabbitConsumerConfig {
    @Bean
    public ListenerContainerCustomizer<MessageListenerContainer> customizer() {
        AtomicInteger index = new AtomicInteger();
        return (container, destination, group) -> {
            if (container instanceof SimpleMessageListenerContainer smlc && Objects.equals(destination, "loadflow.run.loadflowGroup")) {
                smlc.setConsumerArguments(Map.of("x-priority", index.getAndIncrement()));
            }
        };
    }
}
