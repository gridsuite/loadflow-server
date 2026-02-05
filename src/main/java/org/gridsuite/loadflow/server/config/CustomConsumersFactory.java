package org.gridsuite.loadflow.server.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.gridsuite.loadflow.server.service.LoadFlowMessageListener;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CustomConsumersFactory {

    private final ConnectionFactory connectionFactory;
    private final LoadFlowMessageListener loadflowMessageListener;

    private final List<SimpleMessageListenerContainer> containers = new ArrayList<>();

    public CustomConsumersFactory(
        ConnectionFactory connectionFactory,
        LoadFlowMessageListener loadflowMessageListener
    ) {
        this.connectionFactory = connectionFactory;
        this.loadflowMessageListener = loadflowMessageListener;
    }

    @Bean
    public Queue loadflowRunQueue() {
        return QueueBuilder.durable("loadflowGroup").build();
    }

    @Bean
    public TopicExchange loadflowExchange() {
        return new TopicExchange("loadflow.run", true, false);
    }

    @Bean
    Binding loadflowRunBinding(Queue loadflowRunQueue,
                               TopicExchange loadflowExchange) {
        return BindingBuilder
            .bind(loadflowRunQueue)
            .to(loadflowExchange)
            .with("#");
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory cf) {
        return new RabbitAdmin(cf);
    }

    @PostConstruct
    public void start() {
        SimpleMessageListenerContainer consumerContainer =
            new SimpleMessageListenerContainer(connectionFactory);

        consumerContainer.setQueueNames("loadflowGroup");
        consumerContainer.setConcurrentConsumers(1);
        consumerContainer.setPrefetchCount(1);
        consumerContainer.setBeanName("run-loadflow1");
        consumerContainer.setMessageListener(loadflowMessageListener);
        consumerContainer.setConsumerArguments(Map.of("x-priority", 3));
        consumerContainer.setAutoStartup(false);
        consumerContainer.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        containers.add(consumerContainer);

        consumerContainer =
            new SimpleMessageListenerContainer(connectionFactory);

        consumerContainer.setQueueNames("loadflowGroup");
        consumerContainer.setConcurrentConsumers(1);
        consumerContainer.setPrefetchCount(1);
        consumerContainer.setBeanName("run-loadflow2");
        consumerContainer.setMessageListener(loadflowMessageListener);
        consumerContainer.setConsumerArguments(Map.of("x-priority", 2));
        consumerContainer.setAutoStartup(false);
        consumerContainer.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        containers.add(consumerContainer);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startContainers() {
        containers.forEach(SimpleMessageListenerContainer::start);
    }

    @PreDestroy
    public void stop() {
        containers.forEach(SimpleMessageListenerContainer::stop);
    }
}
