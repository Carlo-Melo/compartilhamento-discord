package br.com.compartilhamentodiscord.integration.livekit;

import java.util.concurrent.TimeUnit;
import io.livekit.server.RoomServiceClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LiveKitProperties.class)
public class LiveKitConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "livekit", name = "enabled", havingValue = "true")
    LiveKitGateway liveKitGateway(LiveKitProperties properties) {
        properties.validateEnabledConfiguration();
        long timeoutMillis = properties.getRequestTimeout().toMillis();
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
        RoomServiceClient rooms = RoomServiceClient.createClient(properties.getApiUrl().toString(),
                properties.getApiKey(), properties.getApiSecret(), () -> http, false);
        return new LiveKitGateway(rooms, properties);
    }
}
