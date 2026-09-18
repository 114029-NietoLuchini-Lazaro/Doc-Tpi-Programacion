package ar.edu.utn.frc.tup.piv.llm.moderation.infrastructure.messaging;

import ar.edu.utn.frc.tup.piv.llm.moderation.domain.ModerationResolutionDomainEvent;
import ar.edu.utn.frc.tup.piv.llm.moderation.domain.port.ModerationEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adaptador de infraestructura para publicar eventos de dominio de moderación (LLM-S12-H02 / T4).
 * Publica eventos a través de ApplicationEventPublisher de Spring y prepara el canal para Kafka.
 */
@Component
public class ModerationEventPublisher implements ModerationEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(ModerationEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public ModerationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishMessageUnblocked(ModerationResolutionDomainEvent event) {
        log.info("Publicando evento de dominio '{}' [eventId={}, messageId={}, incidentId={}, courseId={}, resolvedBy={}, resolution={}]",
                event.getEventType(),
                event.getEventId(),
                event.getMessageId(),
                event.getIncidentId(),
                event.getCourseId(),
                event.getResolvedBy(),
                event.getResolution());

        applicationEventPublisher.publishEvent(event);
    }
}
