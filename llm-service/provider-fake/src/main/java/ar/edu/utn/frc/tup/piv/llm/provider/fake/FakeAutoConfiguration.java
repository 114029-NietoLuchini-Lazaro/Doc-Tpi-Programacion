package ar.edu.utn.frc.tup.piv.llm.provider.fake;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = "llm.provider.fake",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class FakeAutoConfiguration {
  @Bean
  FakeProviderAdapter fakeProviderAdapter() {
    return new FakeProviderAdapter();
  }
}
