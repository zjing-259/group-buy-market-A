package cn.bugstack.test.v2;

import cn.bugstack.v2.V2TlsTransporter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import org.junit.Test;
import javax.net.ssl.SSLContext;
import static org.junit.Assert.*;

public class V2TlsGateTest {
    @Test public void plaintextPipelineIsClosedBeforeRpcFrames() {
        EmbeddedChannel channel=new EmbeddedChannel(new V2TlsTransporter.RequireTls());
        try {channel.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer("PRI * HTTP/2.0".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            assertFalse(channel.isActive());} finally {channel.finishAndReleaseAll();}
    }
    @Test public void tlsPipelineRetainsHandshakeEnforcement() throws Exception {
        EmbeddedChannel channel=new EmbeddedChannel(new V2TlsTransporter.RequireTls());
        try {channel.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(new byte[]{22,3,3,0,1,0}));
            assertTrue(channel.isActive()); assertNull(channel.pipeline().get(V2TlsTransporter.RequireTls.class));}
        finally {channel.finishAndReleaseAll();}
    }
}
