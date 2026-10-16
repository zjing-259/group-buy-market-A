package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.sql.Timestamp;
import java.util.*;

/** A bounded position, never an authorization token. Every query re-applies trusted scope. */
public final class V2TeamDiscoveryCursor {
    private static final String SORT = "END_TEAM_ASC_V1";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList("cursorVersion","sortVersion",
            "source","channel","userId","goodsId","activityId","productVersion","lastEndTime","lastTeamId"));
    public final Timestamp endTime;
    public final String teamId;
    private V2TeamDiscoveryCursor(Timestamp endTime, String teamId) { this.endTime=endTime; this.teamId=teamId; }

    public static String id(String value, int maximum) {
        V2Format.id(value,maximum);
        if (value.indexOf('\r')>=0 || value.indexOf('\n')>=0 || edgeSpace(value.codePointAt(0))
                || edgeSpace(value.codePointBefore(value.length()))) throw invalid();
        return value;
    }
    private static boolean edgeSpace(int c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }
    private static V2Failure invalid() { return new V2Failure(V2Codes.INVALID_PARAMETER); }
    private static String text(JsonNode n, String key) {
        if (!n.path(key).isTextual()) throw invalid();
        return n.path(key).textValue();
    }
    private static ObjectNode payload(TeamDiscoveryRequest q, String time, String team) {
        // Insertion order is exactly the canonical alphabetical order of the frozen fields.
        ObjectNode n=JSON.createObjectNode();
        n.put("activityId",q.getActivityId()); n.put("channel",q.getChannel()); n.put("cursorVersion",1);
        n.put("goodsId",q.getGoodsId()); n.put("lastEndTime",time); n.put("lastTeamId",team);
        n.put("productVersion",q.getProductVersion()); n.put("sortVersion",SORT);
        n.put("source",q.getSource()); n.put("userId",q.getUserId()); return n;
    }
    public static String encode(TeamDiscoveryRequest q, String time, String team) {
        try {
            byte[] bytes=JSON.writeValueAsBytes(payload(q,time,team));
            if (bytes.length>768) throw invalid();
            String value=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (value.length()>1024) throw invalid();
            return value;
        }
        catch (Exception e) { throw invalid(); }
    }
    public static V2TeamDiscoveryCursor decode(TeamDiscoveryRequest q) {
        String value=q.getCursor(); if (value==null) return null;
        try {
            if (value.length()>1024 || !value.matches("[A-Za-z0-9_-]+")) throw invalid();
            byte[] bytes=Base64.getUrlDecoder().decode(value); if (bytes.length>768) throw invalid();
            String raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode n=JSON.readTree(raw); if (!n.isObject()) throw invalid();
            Set<String> fields=new HashSet<>(); n.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(FIELDS) || !n.path("cursorVersion").isIntegralNumber()
                    || !n.path("cursorVersion").canConvertToInt() || n.path("cursorVersion").intValue()!=1
                    || !SORT.equals(text(n,"sortVersion"))) throw invalid();
            String source=id(text(n,"source"),8), channel=id(text(n,"channel"),8), user=id(text(n,"userId"),64);
            String goods=id(text(n,"goodsId"),64), activity=text(n,"activityId"), version=text(n,"productVersion");
            V2Format.activityId(activity); V2Format.positiveVersion(version);
            String end=text(n,"lastEndTime"), team=id(text(n,"lastTeamId"),64);
            Timestamp time=Timestamp.from(V2Format.instant(end));
            TeamDiscoveryRequest embedded=new TeamDiscoveryRequest(); embedded.setSource(source); embedded.setChannel(channel);
            embedded.setUserId(user); embedded.setGoodsId(goods); embedded.setActivityId(activity); embedded.setProductVersion(version);
            if (!value.equals(encode(embedded,end,team))) throw invalid();
            if (!source.equals(q.getSource()) || !channel.equals(q.getChannel()) || !user.equals(q.getUserId())) throw invalid();
            if (!goods.equals(q.getGoodsId()) || !activity.equals(q.getActivityId()) || !version.equals(q.getProductVersion()))
                throw new V2Failure(V2Codes.TEAM_DISCOVERY_CONTEXT_CHANGED);
            return new V2TeamDiscoveryCursor(time,team);
        } catch (V2Failure known) { throw known; }
        catch (Exception malformed) { throw invalid(); }
    }
}
