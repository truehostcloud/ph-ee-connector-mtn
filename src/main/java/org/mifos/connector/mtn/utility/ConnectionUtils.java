package org.mifos.connector.mtn.utility;

/**
 * Connection utilities.
 */
public class ConnectionUtils {

    /**
     * returns camel dsl for applying connection timeout.
     *
     * @param timeout
     *            timeout value in ms
     * @return a string of timeout with the format needed
     */
    public static String getConnectionTimeoutDsl(final int timeout) {
        String base = "httpClient.connectTimeout={}&httpClient.connectionRequestTimeout={}&httpClient.socketTimeout={}";
        return base.replace("{}", "" + timeout);
    }
}
