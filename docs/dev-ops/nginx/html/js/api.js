/**
 * 后端 HTTP 封装：query_group_buy_market_config / lock-market-pay-order / settlement_market_pay_order
 * 统一返回 { code, info, data }，网络异常/非 JSON 统一抛错。
 */
window.API = (function () {
  const cfg = window.APP_CONFIG;

  function http(url, body) {
    return fetch(cfg.apiBase + url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (res) {
      if (!res.ok) {
        throw new Error('HTTP ' + res.status);
      }
      return res.json();
    }).catch(function (err) {
      // 网络错误 / 后端未启动 / JSON 解析失败
      throw new Error('无法连接后端(' + cfg.apiBase + ')，请确认服务已启动: ' + (err && err.message ? err.message : err));
    });
  }

  /**
   * 1) 查询拼团营销配置（商品 + 我参与的团 + 随机他人团 + 统计）
   * @param {string} userId
   */
  function queryGroupBuyMarketConfig(userId) {
    return http(cfg.path.queryGroupBuyMarketConfig, {
      userId: userId,
      source: cfg.source,
      channel: cfg.channel,
      goodsId: cfg.goodsId
    });
  }

  /**
   * 2) 锁单（开团 teamId 传 null / undefined；参团传该团 teamId）
   * @param {object} p { userId, activityId, teamId, outTradeNo }
   */
  function lockMarketPayOrder(p) {
    return http(cfg.path.lockMarketPayOrder, {
      userId: p.userId,
      source: cfg.source,
      channel: cfg.channel,
      goodsId: cfg.goodsId,
      activityId: p.activityId,
      teamId: p.teamId || null,
      outTradeNo: p.outTradeNo,
      notifyUrl: cfg.notifyUrl
    });
  }

  /**
   * 3) 结算（模拟支付成功 → 入团/成团判定）
   * outTradeTime 用 ISO-8601（Jackson 默认可解析）
   */
  function settlementMarketPayOrder(p) {
    return http(cfg.path.settlementMarketPayOrder, {
      source: cfg.source,
      channel: cfg.channel,
      userId: p.userId,
      outTradeNo: p.outTradeNo,
      outTradeTime: new Date().toISOString()
    });
  }

  /**
   * 生成幂等外部交易单号（列 out_trade_no varchar(12)，必须 ≤12 位）
   */
  function newOutTradeNo() {
    // Date.now().toString(36) ≈ 8 位(毫秒级) + 4 位随机，共 12 位，含 FU 前缀风格改为纯字母数字
    return ('f' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5)).slice(0, 12);
  }

  return {
    queryGroupBuyMarketConfig: queryGroupBuyMarketConfig,
    lockMarketPayOrder: lockMarketPayOrder,
    settlementMarketPayOrder: settlementMarketPayOrder,
    newOutTradeNo: newOutTradeNo,
    isOk: function (r) { return r && r.code === '0000'; }
  };
})();
