package cn.bugstack.v2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Prevent old HTTP trade and DCC endpoints from bypassing v2 caller authentication. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name="ab.v2.legacy-http-protection-enabled",havingValue="true",matchIfMissing=true)
public class V2LegacyHttpGuard extends OncePerRequestFilter {
    @Value("${ab.v2.legacy-http-token:}") private String token;
    @Value("${ab.v2.enabled:false}") private boolean v2Enabled;
    @Value("${ab.v2.legacy-new-orders-enabled:false}") private boolean legacyNewOrdersEnabled;

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
                                              FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI();
        String context=request.getContextPath();
        if (!context.isEmpty() && path.startsWith(context)) path=path.substring(context.length());
        if (!path.startsWith("/api/v1/gbm/")) {
            chain.doFilter(request,response); return;
        }
        String supplied=request.getHeader("X-Legacy-Token");
        if (token.isEmpty() || supplied==null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN); return;
        }
        if (v2Enabled && !legacyNewOrdersEnabled && path.equals("/api/v1/gbm/trade/lock-market-pay-order")) {
            response.sendError(HttpServletResponse.SC_GONE); return;
        }
        chain.doFilter(request,response);
    }
}
