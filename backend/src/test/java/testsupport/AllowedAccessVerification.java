package testsupport;

import br.com.compartilhamentodiscord.service.access.AccessVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Explicit test-only replacement for the unavailable Discord/session verification boundary. */
@Configuration(proxyBeanMethods = false)
public class AllowedAccessVerification {
    @Bean @Primary AccessVerifier testVerifier() { return grant -> AccessVerifier.Result.ALLOWED; }
}
