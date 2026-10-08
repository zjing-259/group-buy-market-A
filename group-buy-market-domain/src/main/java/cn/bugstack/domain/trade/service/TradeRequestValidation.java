package cn.bugstack.domain.trade.service;

import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;

/** Lock and settlement must use the same exact, unmodified business identifiers. */
public final class TradeRequestValidation {
    private TradeRequestValidation() { }

    public static void callbackUrl(String value) {
        required(value, 512);
        try {
            java.net.URI uri = new java.net.URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) ||
                    uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null ||
                    uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Invalid callback URL");
            }
        } catch (java.net.URISyntaxException | IllegalArgumentException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
    }

    public static void required(String value, int max) {
        if (StringUtils.isBlank(value) || value.length() > max || !value.equals(value.trim())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
    }
}
