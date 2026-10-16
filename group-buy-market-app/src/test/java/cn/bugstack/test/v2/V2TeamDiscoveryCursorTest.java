package cn.bugstack.test.v2;
import cn.bugstack.api.v2.*;
import cn.bugstack.v2.*;
import com.fasterxml.jackson.databind.*;
import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class V2TeamDiscoveryCursorTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test public void goldenVectorMatchesAndRoundTrips() throws Exception {
        JsonNode vectors=json.readTree(Paths.get("../docs/schemas/team-discovery-v1.examples.json").toFile());
        TeamDiscoveryRequest q=json.treeToValue(vectors.get("request"),TeamDiscoveryRequest.class);
        JsonNode p=vectors.get("cursorPayload");String encoded=V2TeamDiscoveryCursor.encode(q,p.path("lastEndTime").asText(),p.path("lastTeamId").asText());
        assertEquals(vectors.get("cursorEncoded").asText(),encoded);q.setCursor(encoded);assertEquals(p.path("lastTeamId").asText(),V2TeamDiscoveryCursor.decode(q).teamId);
    }
    @Test public void malformedDuplicateNoncanonicalAndOversizedCursorsAreRejected() {
        TeamDiscoveryRequest q=new TeamDiscoveryRequest();
        for(String s:Arrays.asList("","A","@@@","AA=",Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{(byte)255}),
                Base64.getUrlEncoder().withoutPadding().encodeToString("{\"cursorVersion\":1,\"cursorVersion\":1}".getBytes(StandardCharsets.UTF_8)),String.join("",Collections.nCopies(1025,"A")))){
            q.setCursor(s);try{V2TeamDiscoveryCursor.decode(q);fail("Accepted malformed cursor");}catch(V2Failure e){assertEquals("0002",e.getCode());}
        }
    }
    @Test public void identifierBoundaryRejectsUnicodeWhitespaceAndUtf16Overflow() {
        for(String s:Arrays.asList("\u00a0owner","owner\u2003","owner\n",String.join("",Collections.nCopies(33,"\ud83d\ude00")))) {
            try{V2TeamDiscoveryCursor.id(s,64);fail("Accepted invalid id");}catch(V2Failure e){assertEquals("0002",e.getCode());}
        }
    }
    @Test public void oversizedCombinedUtf8CursorIsRejectedRatherThanReturningAnUnusablePosition() {
        TeamDiscoveryRequest q=new TeamDiscoveryRequest();q.setSource("td01");q.setChannel("web");q.setActivityId("1");q.setProductVersion("1");
        String id=String.join("",Collections.nCopies(64,"\u7528"));q.setUserId(id);q.setGoodsId(id);
        try{V2TeamDiscoveryCursor.encode(q,"2026-10-14T00:00:00.000Z",id);fail("Oversized cursor returned");}catch(V2Failure e){assertEquals("0002",e.getCode());}
    }
}
