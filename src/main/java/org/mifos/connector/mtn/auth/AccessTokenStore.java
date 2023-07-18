package org.mifos.connector.mtn.auth;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Class that holds the access token.
 */
@Component
public class AccessTokenStore {

    public String accessToken;
    public LocalDateTime expiresOn;

    public AccessTokenStore() {
        this.expiresOn = LocalDateTime.now();
        System.out.println("ACCESS TOKEN STORE CREATED!");
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public LocalDateTime getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(int expiresIn) {
        this.expiresOn = LocalDateTime.now().plusSeconds(expiresIn);
    }

    /**
     * Checks if the token is still valid.
     *
     * @param dateTime
     *            the date to check time against
     * @return boolean
     */
    public boolean isValid(LocalDateTime dateTime) {
        if (dateTime.isBefore(expiresOn)) {
            return true;
        } else {
            return false;
        }
    }
}
