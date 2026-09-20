package ar.edu.utn.frc.tup.piv.llm.adapter.out.ai;

import ar.edu.utn.frc.tup.piv.llm.application.port.out.ModelInvocationPort;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationUnavailableException;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationRequest;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.FunctionModelConfigRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.InferenceSettings;
import ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runtime port implementation delegated to the provider Strategy registry.
 */
@Component
public class LangChain4jModelAdapter implements ModelInvocationPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jModelAdapter.class);

    private final ProviderCredentialRepository deployments;
    private final FunctionModelConfigRepository configs;
    private final ProviderInvocationGateway gateway;

    public LangChain4jModelAdapter(ProviderCredentialRepository deployments, FunctionModelConfigRepository configs, ProviderInvocationGateway gateway) {
        this.deployments = deployments;
        this.configs = configs;
        this.gateway = gateway;
    }

    @Override
    public ModelInvocationResult invoke(ModelInvocationRequest request) {
        var config = configs.find(request.function()).filter(FunctionModelConfigRepository.Config::enabled)
            .orElseThrow(() -> new ModelInvocationUnavailableException("La función no tiene modelo habilitado"));
        var deployment = deployments.forId(config.modelDeploymentId())
            .orElseThrow(() -> new ModelInvocationUnavailableException("El deployment asignado no está disponible"));
        var credential = deployments.get(deployment.credentialId()).filter(value -> "ACTIVE".equals(value.state()))
            .orElseThrow(() -> new ModelInvocationUnavailableException("La credencial del deployment no está activa"));
        try {
            var reply = gateway.invoke(credential, deployment.modelId(), request.systemPrompt() + "\n\n" + request.userPrompt(), new InferenceSettings("runtime-v2", null, null, null, null, false, 512), request.timeout());
            return new ModelInvocationResult(reply.text(), deployment.providerKey(), deployment.modelId());
        } catch (ProviderException exception) {
            LOGGER.warn("Falla del provider al invocar function={} provider={} deployment={} model={} code={}",
                request.function(), deployment.providerKey(), deployment.id(), deployment.modelId(), exception.code(), exception);
            throw new ModelInvocationUnavailableException("El proveedor de modelos no pudo responder", exception);
        }
    }

    @Override
    public String provider() {
        return "deployment-selected";
    }

    @Override
    public String model() {
        return "deployment-selected";
    }
}
