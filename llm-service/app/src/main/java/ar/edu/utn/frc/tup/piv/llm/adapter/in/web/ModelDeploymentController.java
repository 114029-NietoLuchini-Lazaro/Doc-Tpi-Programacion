package ar.edu.utn.frc.tup.piv.llm.adapter.in.web;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ModelDeploymentRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ModelDeploymentRepository.ModelDeploymentSummary;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security.CourseAuthorization;
import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security.GoldenSetAuthorization;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModelDeploymentController {
  private final ModelDeploymentRepository repository;
  private final GoldenSetAuthorization authorization;
  private final CourseAuthorization courseAuthorization;
  private final ProviderRegistry providers;

  public ModelDeploymentController(ModelDeploymentRepository repository,
      GoldenSetAuthorization authorization, CourseAuthorization courseAuthorization,
      ProviderRegistry providers) {
    this.repository = repository;
    this.authorization = authorization;
    this.courseAuthorization = courseAuthorization;
    this.providers = providers;
  }

  @GetMapping("/api/llm/courses/{courseId}/model-deployments")
  public ModelDeploymentPage listForCourse(@PathVariable UUID courseId, @RequestHeader HttpHeaders headers) {
    var actor = authorization.require(headers);
    courseAuthorization.requireTeacher(courseId, actor, headers);
    return new ModelDeploymentPage(repository.listEnabledDeployments());
  }

  @GetMapping("/api/llm/admin/model-adapters")
  public ModelAdapterPage listAdapters(@RequestHeader HttpHeaders headers) {
    authorization.require(headers);
    var deployments = repository.listEnabledDeployments();
    return new ModelAdapterPage(providers.descriptors().stream()
        .map(provider -> new ModelAdapterSummary(provider.key(), provider.displayName(),
            provider.adapterVersion(), provider.capabilities(),
            deployments.stream().filter(d -> provider.key().equals(d.provider())).toList()))
        .toList());
  }

  public record ModelDeploymentPage(List<ModelDeploymentSummary> items) {}
  public record ModelAdapterPage(List<ModelAdapterSummary> items) {}
  public record ModelAdapterSummary(String providerKey, String displayName, String adapterVersion,
                                    ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCapabilities capabilities,
                                    List<ModelDeploymentSummary> deployments) {}
}
