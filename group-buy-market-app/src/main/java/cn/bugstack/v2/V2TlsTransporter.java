package cn.bugstack.v2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslHandler;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.*;
import org.apache.dubbo.remoting.api.pu.PortUnificationTransporter;
import org.apache.dubbo.remoting.transport.ChannelHandlerAdapter;
import java.lang.reflect.Method;
import java.util.List;

/** Enforce encrypted first bytes before Dubbo 3.2's port-unification plaintext fallback. */
public class V2TlsTransporter implements PortUnificationTransporter {
    private final PortUnificationTransporter delegate;
    public V2TlsTransporter(PortUnificationTransporter delegate) {this.delegate=delegate;}
    @Override public org.apache.dubbo.remoting.api.pu.AbstractPortUnificationServer bind(URL url,ChannelHandler handler) throws RemotingException {
        if (!url.getParameter("ssl-enabled",false)) return delegate.bind(url,handler);
        // Install the gate synchronously on channel activation, before any preface can be decoded.
        // Triple schedules business calls on its own executor after protocol decoding.
        return delegate.bind(url.addParameter("dispatcher","direct"),new ChannelHandlerAdapter() {
            @Override public void connected(Channel channel) throws RemotingException {
                try {
                    // NettyChannel is package-private in 3.2.16; its public accessor requires reflective access.
                    Method accessor=channel.getClass().getMethod("getNioChannel"); accessor.setAccessible(true);
                    io.netty.channel.Channel netty=(io.netty.channel.Channel)accessor.invoke(channel);
                    netty.pipeline().addFirst("v2-require-tls",new RequireTls());
                } catch (Exception unsupported) {
                    channel.close(); throw new RemotingException(channel,"TLS_GATE_INSTALL_FAILED",unsupported);
                }
                handler.connected(channel);
            }
            @Override public void disconnected(Channel channel) throws RemotingException {handler.disconnected(channel);}
            @Override public void sent(Channel channel,Object value) throws RemotingException {handler.sent(channel,value);}
            @Override public void received(Channel channel,Object value) throws RemotingException {handler.received(channel,value);}
            @Override public void caught(Channel channel,Throwable error) throws RemotingException {handler.caught(channel,error);}
        });
    }
    @Override public org.apache.dubbo.remoting.api.connection.AbstractConnectionClient connect(URL url,ChannelHandler handler)
            throws RemotingException {return delegate.connect(url,handler);}

    public static class RequireTls extends ByteToMessageDecoder {
        @Override protected void decode(ChannelHandlerContext context,ByteBuf bytes,List<Object> output) {
            if (bytes.readableBytes()<5) return;
            if (!SslHandler.isEncrypted(bytes)) {context.close(); return;}
            // Native Dubbo/Netty SSL processing validates the complete handshake and client certificate.
            context.pipeline().remove(this);
        }
    }
}
