package cn.bugstack.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Additional verification keys remain available during credential rotation and message replay. */
final class V2VerificationKeys {
    private static final ObjectMapper JSON=new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private V2VerificationKeys() { }

    static String resolve(String activeId,String activeSecret,String retainedJson,String requestedId) {
        if (requestedId==null) return null;
        if (requestedId.equals(activeId)) return activeSecret;
        try {
            Map<String,String> retained=JSON.readValue(retainedJson,new TypeReference<Map<String,String>>(){});
            String secret=retained.get(requestedId);
            return secret==null || secret.isEmpty()?null:secret;
        } catch (Exception invalid) {
            throw new IllegalStateException("INVALID_VERIFICATION_KEY_CONFIGURATION");
        }
    }
}
