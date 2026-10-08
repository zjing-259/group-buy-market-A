package cn.bugstack.test.v2;

import cn.bugstack.v2.V2LegacyHttpGuard;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class V2LegacyHttpGuardTest {
    @Test public void oldTradeAndDccNeedConfiguredTokenWhenV2IsEnabled() throws Exception {
        V2LegacyHttpGuard guard=new V2LegacyHttpGuard();
        ReflectionTestUtils.setField(guard,"token","private-legacy-token");
        ReflectionTestUtils.setField(guard,"v2Enabled",true);
        for (String path:new String[]{"/api/v1/gbm/trade/lock-market-pay-order","/api/v1/gbm/dcc/update_config"}) {
            MockHttpServletRequest missing=new MockHttpServletRequest("POST",path);
            MockHttpServletResponse denied=new MockHttpServletResponse();
            MockFilterChain stopped=new MockFilterChain();
            guard.doFilter(missing,denied,stopped);
            assertEquals(403,denied.getStatus()); assertNull(stopped.getRequest());

            MockHttpServletRequest authorized=new MockHttpServletRequest("POST",path);
            authorized.addHeader("X-Legacy-Token","private-legacy-token");
            MockHttpServletResponse allowed=new MockHttpServletResponse();
            MockFilterChain continued=new MockFilterChain();
            guard.doFilter(authorized,allowed,continued);
            if (path.contains("lock-market-pay-order")) {
                assertEquals(410,allowed.getStatus()); assertNull(continued.getRequest());
            } else {
                assertEquals(200,allowed.getStatus()); assertSame(authorized,continued.getRequest());
            }
        }
        MockHttpServletRequest prefixed=new MockHttpServletRequest("GET","/shop/api/v1/gbm/dcc/update_config");
        prefixed.setContextPath("/shop");
        MockHttpServletResponse denied=new MockHttpServletResponse();
        guard.doFilter(prefixed,denied,new MockFilterChain());
        assertEquals(403,denied.getStatus());
        ReflectionTestUtils.setField(guard,"legacyNewOrdersEnabled",true);
        MockHttpServletRequest transition=new MockHttpServletRequest("POST","/api/v1/gbm/trade/lock-market-pay-order");
        transition.addHeader("X-Legacy-Token","private-legacy-token");
        MockFilterChain transitionChain=new MockFilterChain();
        guard.doFilter(transition,new MockHttpServletResponse(),transitionChain);
        assertSame(transition,transitionChain.getRequest());
        ReflectionTestUtils.setField(guard,"legacyNewOrdersEnabled",false);
        ReflectionTestUtils.setField(guard,"v2Enabled",false);
        MockFilterChain beforeCutover=new MockFilterChain();
        guard.doFilter(transition,new MockHttpServletResponse(),beforeCutover);
        assertSame(transition,beforeCutover.getRequest());
    }
}
