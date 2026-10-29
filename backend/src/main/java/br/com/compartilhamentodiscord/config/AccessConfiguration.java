package br.com.compartilhamentodiscord.config;

import br.com.compartilhamentodiscord.service.access.AccessVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AccessConfiguration {
    @Bean
    @ConditionalOnMissingBean(AccessVerifier.class)
    AccessVerifier unavailableAccessVerifier() {
        return grant -> AccessVerifier.Result.UNAVAILABLE;
    }
}
