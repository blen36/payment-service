package com.innowise.paymentservice.client;

import com.innowise.paymentservice.exception.PaymentApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class RandomNumberClient {
    private final RestClient client;
    public RandomNumberClient(@Qualifier("randomRestClient") RestClient client) { this.client = client; }

    public int nextNumber() {
        try {
            String body = client.get().uri(u -> u.path("/integers/")
                    .queryParam("num", 1).queryParam("min", 1).queryParam("max", 100)
                    .queryParam("col", 1).queryParam("base", 10).queryParam("format", "plain")
                    .queryParam("rnd", "new").build()).accept(MediaType.TEXT_PLAIN)
                    .retrieve().body(String.class);
            int number = Integer.parseInt(body == null ? "" : body.trim());
            if (number < 1 || number > 100) throw new NumberFormatException("Out of range");
            return number;
        } catch (RestClientException | NumberFormatException ex) {
            // An unavailable provider is not a declined payment; do not invent a random result.
            throw PaymentApiException.upstream("Random number provider is unavailable or returned an invalid response");
        }
    }
}
