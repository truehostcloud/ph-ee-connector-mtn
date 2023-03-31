package org.mifos.connector.mtn.Utility;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConfigurationProperties(prefix = "mtnrw")
@Data
public class MtnProps {
    private String authHost;
    private String clientKey;
    private String clientSecret;
    private String apiHost;
    private String environment;
    private String subscriptionKey;
    private String callBack;
}
