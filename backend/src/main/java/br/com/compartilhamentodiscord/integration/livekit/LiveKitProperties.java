package br.com.compartilhamentodiscord.integration.livekit;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secrets intentionally have no defaults and are never included in toString. */
@ConfigurationProperties(prefix = "livekit")
public final class LiveKitProperties {
    private boolean enabled;
    private URI apiUrl = URI.create("http://127.0.0.1:7880");
    private URI browserUrl = URI.create("ws://127.0.0.1:7880");
    private String apiKey;
    private String apiSecret;
    private Duration requestTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getApiUrl() { return apiUrl; }
    public void setApiUrl(URI apiUrl) { this.apiUrl = apiUrl; }
    public URI getBrowserUrl() { return browserUrl; }
    public void setBrowserUrl(URI browserUrl) { this.browserUrl = browserUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

    void validateEnabledConfiguration() {
        if (!enabled || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.length() < 32
                || !validUrl(apiUrl, Set.of("http", "https"))
                || !validUrl(browserUrl, Set.of("ws", "wss"))
                || requestTimeout == null || requestTimeout.compareTo(Duration.ofSeconds(1)) < 0
                || requestTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalStateException("Configuração LiveKit habilitada inválida; confira URLs, credenciais e timeout.");
        }
    }

    private static boolean validUrl(URI url, Set<String> schemes) {
        return url != null && schemes.contains(url.getScheme()) && url.getHost() != null
                && url.getUserInfo() == null && url.getRawQuery() == null && url.getRawFragment() == null;
    }
}
