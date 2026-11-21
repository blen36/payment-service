package com.innowise.paymentservice.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtSupportTest {
    static RSAKey key;
    JwtDecoder decoder;
    @BeforeAll static void keys() throws Exception { key = new RSAKeyGenerator(2048).generate(); }
    @BeforeEach void setup() throws Exception {
        decoder = JwtSupport.decoder(NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build(),
                "authentication-service", "java-advanced-api", "access");
    }
    String token(Consumer<JWTClaimsSet.Builder> change, RSAKey signingKey) throws Exception {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("authentication-service").subject("7")
                .audience("java-advanced-api").issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString()).claim("user_id",7L).claim("role","USER").claim("tokenType","access");
        change.accept(claims);
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(signingKey)); return jwt.serialize();
    }
    @Test void acceptsAuthServiceClaims() throws Exception {
        assertThat(decoder.decode(token(c -> {},key)).getSubject()).isEqualTo("7");
    }
    @ParameterizedTest @ValueSource(strings={"refresh","wrong-issuer","wrong-audience","wrong-owner","wrong-role","expired","missing-id"})
    void rejectsInvalidClaims(String kind) throws Exception {
        String raw = token(c -> {
            switch(kind) {
                case "refresh" -> c.claim("tokenType","refresh");
                case "wrong-issuer" -> c.issuer("other");
                case "wrong-audience" -> c.audience("other");
                case "wrong-owner" -> c.claim("user_id",8L);
                case "wrong-role" -> c.claim("role","SUPERUSER");
                case "expired" -> c.expirationTime(Date.from(Instant.now().minusSeconds(60)));
                case "missing-id" -> c.jwtID(null);
                default -> throw new IllegalArgumentException(kind);
            }
        },key);
        assertThatThrownBy(() -> decoder.decode(raw)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsWrongSignature() throws Exception {
        String raw=token(c -> {},new RSAKeyGenerator(2048).generate());
        assertThatThrownBy(() -> decoder.decode(raw)).isInstanceOf(JwtException.class);
    }
}
