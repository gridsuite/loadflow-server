package org.gridsuite.loadflow.server.service;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LoadFlowMessageListener implements ChannelAwareMessageListener {
    private final LoadFlowWorkerService loadFlowWorkerService;

    public LoadFlowMessageListener(LoadFlowWorkerService loadFlowWorkerService) {
        this.loadFlowWorkerService = loadFlowWorkerService;
    }

    @Override
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            // payload
            String payload = new String(
                message.getBody(),
                StandardCharsets.UTF_8
            );

            // headers
            org.springframework.messaging.Message<String> springMessage = MessageBuilder
                .withPayload(payload)
                .copyHeaders(message.getMessageProperties().getHeaders())
                .build();

            loadFlowWorkerService.consumeRun().accept(springMessage);

            channel.basicAck(tag, false);
        } catch (Exception e) {

            channel.basicReject(tag, false);

            throw e;
        }

    }
}
