package ar.edu.utn.frc.tup.piv.llm.moderation.api;

import ar.edu.utn.frc.tup.piv.llm.moderation.api.dto.ModerationDecisionRequest;
import ar.edu.utn.frc.tup.piv.llm.moderation.api.dto.ModerationDecisionResponse;
import ar.edu.utn.frc.tup.piv.llm.moderation.api.mapper.ModerationApiMapper;
import ar.edu.utn.frc.tup.piv.llm.moderation.application.dto.ModerationDecisionCommand;
import ar.edu.utn.frc.tup.piv.llm.moderation.application.port.ModerationDecisionUseCase;
import ar.edu.utn.frc.tup.piv.llm.moderation.domain.ModerationDecision;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el endpoint de decisión de moderación síncrono (LLM-S11-H01).
 * Responde en POST /moderation/v1/decisions y ${app.api.private-path}/moderation/v1/decisions.
 */
@RestController
public class ModerationDecisionController {

    private final ModerationGatewayAuthorization authorization;
    private final ModerationDecisionUseCase useCase;

    public ModerationDecisionController(
            ModerationGatewayAuthorization authorization,
            ModerationDecisionUseCase useCase) {
        this.authorization = authorization;
        this.useCase = useCase;
    }

    @PostMapping({"/moderation/v1/decisions", "${app.api.private-path:/api/llm}/moderation/v1/decisions"})
    public ResponseEntity<ModerationDecisionResponse> decide(
            @Valid @RequestBody ModerationDecisionRequest request,
            @RequestHeader(required = false) HttpHeaders headers) {
        // Valida token técnico/Gateway con scope 'moderation:decide'
        authorization.requireScope(headers);

        ModerationDecisionCommand command = ModerationApiMapper.toCommand(request);
        ModerationDecision decision = useCase.decide(command);

        return ResponseEntity.ok(ModerationApiMapper.toResponse(decision));
    }
}
