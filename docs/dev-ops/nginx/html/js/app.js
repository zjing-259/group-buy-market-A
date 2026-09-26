/**
 * 拼团 H5 主逻辑 —— 按草图三页重构：
 *   1) 登录页（用户名/密码/登录）
 *   2) 商品页（轮播 + 标题 + 大促横幅 + 拼单列表 + 底部「单独购买/开团购买」）
 *   3) 支付弹框（请扫码支付 + 二维码 + 取消支付/支付完成）
 * 纯 JS，无依赖。脚本加载顺序：config.js → api.js → app.js
 */
(function () {
  'use strict';

  var cfg = window.APP_CONFIG;
  var API = window.API;

  /* ---------------- 状态 ---------------- */
  var state = {
    user: null,          // {id,name,tag,color}
    goods: null,         // 后端 Goods
    stat: null,          // 后端 TeamStatistic
    myTeam: null,        // 我参与的进行中团
    teams: [],           // 全部进行中团（我的置顶 + 他人）
    pendingPay: null,    // 待支付锁单 {outTradeNo, orderId, amount, mode}
    busy: false
  };

  /* ---------------- 工具 ---------------- */
  function $(id) { return document.getElementById(id); }

  function fmt(n) {
    if (n === null || n === undefined || n === '') return '0';
    var x = Number(n);
    return Number.isFinite(x) ? (Number.isInteger(x) ? String(x) : x.toFixed(2)) : '0';
  }

  function esc(s) {
    return String(s === null || s === undefined ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function userNameById(id) {
    var hit = cfg.users.filter(function (u) { return u.id === id; })[0];
    return hit ? hit.name : (id || '？');
  }

  function toMs(v) {
    if (v === null || v === undefined) return NaN;
    if (typeof v === 'number') return v;
    var t = Date.parse(String(v));
    return isNaN(t) ? NaN : t;
  }

  function pad(n) { return n < 10 ? '0' + n : '' + n; }
  function cdText(ms) {
    if (ms <= 0) return '00:00:00';
    var s = Math.floor(ms / 1000);
    return pad(Math.floor(s / 3600)) + ':' + pad(Math.floor((s % 3600) / 60)) + ':' + pad(s % 60);
  }

  // 判定团是否已失效（后端随机团偶会带出已过期的脏数据，前端兜底过滤）
  function isExpiredTeam(t) {
    if (t.validTimeCountdown === '已结束' || t.validTimeCountdown === '无效的时间') return true;
    var endMs = toMs(t.validEndTime);
    return !isNaN(endMs) && endMs <= Date.now();
  }

  var toastTimer = null;
  function toast(msg, type) {
    var el = $('toast');
    el.textContent = msg;
    el.className = 'toast' + (type === 'ok' ? ' toast--ok' : (type === 'err' ? ' toast--err' : ''));
    el.hidden = false;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.hidden = true; }, 2200);
  }

  function showModal(id) { $(id).hidden = false; }
  function hideModal(id) { $(id).hidden = true; }

  /* ---------------- 业务错误码文案 ---------------- */
  var CODE_TEXT = {
    '0002': '请求参数不完整',
    'E0002': '该商品暂无拼团营销活动',
    'E0006': '来晚了，这个团已满员',
    'E0103': '你已参与过该活动，每人限购 1 份'
  };
  function codeText(res) {
    if (!res) return '网络异常';
    var map = CODE_TEXT[res.code];
    if (map) return map;
    if (res.info && res.info !== '成功') return res.info;
    if (res.code === '0001') return '服务开小差了，请确认后端已重启，或换一个演示账号';
    return '未知错误(' + (res.code || '-') + ')';
  }

  /* ================= 启动 ================= */
  function boot() {
    renderCarousel();
    renderCoupons();
    renderDetail();
    bindEvents();
    showLogin();
  }

  /* ---------------- 登录（草图2） ---------------- */
  function showLogin(prefill) {
    $('appView').hidden = true;
    $('buybar').hidden = true;
    $('loginView').hidden = false;
    $('loginUser').value = prefill || '';
    $('loginPass').value = '';
  }

  function doLogin() {
    var u = $('loginUser').value.trim();
    if (!u) { toast('请输入用户名', 'err'); return; }
    // 密码仅演示，后端无鉴权；此处不校验
    var hit = cfg.users.filter(function (x) { return x.id === u; })[0];
    state.user = hit || { id: u, name: u, tag: '', color: '#e02e24' };
    $('userChipName').textContent = state.user.name;
    $('userChipAvatar').textContent = u.charAt(0).toUpperCase();
    $('loginView').hidden = true;
    $('appView').hidden = false;
    $('buybar').hidden = false;
    toast('欢迎，' + state.user.name);
    refresh(true);
  }

  /* ---------------- 轮播 ---------------- */
  function renderCarousel() {
    var track = $('carouselTrack'), dots = $('carouselDots');
    var imgs = cfg.carouselImages;
    track.innerHTML = imgs.map(function (src, i) {
      return '<div class="carousel__slide"><img src="' + esc(src) + '" alt="商品图 ' + (i + 1) + '" /></div>';
    }).join('');
    dots.innerHTML = imgs.map(function (_, i) { return '<i data-i="' + i + '"></i>'; }).join('');

    var cur = 0, timer = null;
    function go(i) {
      cur = (i + imgs.length) % imgs.length;
      track.style.transform = 'translateX(-' + (cur * 100) + '%)';
      var ds = dots.querySelectorAll('i');
      for (var j = 0; j < ds.length; j++) ds[j].className = (j === cur) ? 'on' : '';
      $('carouselCounter').textContent = (cur + 1) + ' / ' + imgs.length;
    }
    function auto() {
      if (timer) clearInterval(timer);
      timer = setInterval(function () { go(cur + 1); }, cfg.carouselInterval);
    }
    dots.addEventListener('click', function (e) {
      var t = e.target;
      if (t.tagName === 'I') { go(Number(t.getAttribute('data-i'))); auto(); }
    });
    go(0); auto();
  }

  function renderCoupons() {
    $('couponRow').innerHTML = cfg.coupons.map(function (c) {
      return '<div class="coupon">' +
        '<div class="coupon__left"><b><em>¥</em>' + esc(c.amount) + '</b></div>' +
        '<div class="coupon__right"><div class="coupon__title">' + esc(c.title) + '</div>' +
        '<div class="coupon__cond">' + esc(c.cond) + '</div></div></div>';
    }).join('');
  }
  function renderDetail() {
    $('detailList').innerHTML = cfg.details.map(function (d) { return '<li>' + esc(d) + '</li>'; }).join('');
  }

  /* ================= 数据加载 ================= */
  function refresh(isInitial) {
    API.queryGroupBuyMarketConfig(state.user.id).then(function (res) {
      if (!API.isOk(res)) {
        if (isInitial) fatal(codeText(res));
        else toast(codeText(res), 'err');
        return;
      }
      var d = res.data || {};
      state.goods = d.goods || null;
      state.stat = d.teamStatistic || {};
      if (!state.goods) { fatal('后端未返回商品配置'); return; }
      renderGoods();
      renderTeamList(d.teamList || []);
      $('loadFail').hidden = true;
      $('page').hidden = false;
      $('buybar').hidden = false;
    }).catch(function (err) {
      if (isInitial) fatal(err.message);
      else toast(err.message, 'err');
    });
  }

  function fatal(msg) {
    $('loadFailText').textContent = msg;
    $('loadFail').hidden = false;
    $('page').hidden = true;
    $('buybar').hidden = true;
  }

  /* ---------------- 商品信息 ---------------- */
  function renderGoods() {
    var g = state.goods, st = state.stat;
    var pay = Number(g.payPrice || 0), origin = Number(g.originalPrice || 0);
    var saved = Math.max(0, origin - pay);

    $('gPrice').textContent = fmt(pay);
    $('gOrigin').textContent = fmt(origin);
    $('gGroupTag').textContent = g.target + '人团';
    $('gSave').textContent = saved > 0 ? '直降 ¥' + fmt(saved) : '';
    $('gTitle').textContent = g.goodsName || cfg.productName || g.activityName || '拼团商品';
    $('gSub').innerHTML = '正品保障 · ' + (g.target || '-') + ' 人成团即发货 · 赠送配套源码与视频';
    $('gSold').textContent = fmt(st.allTeamUserCount);
    $('gTeamTotal').textContent = '共 ' + fmt(st.allTeamCount) + ' 团';

    // 大促横幅
    var promo = $('promo');
    if (saved > 0) {
      $('promoText').textContent = '直降 ¥' + fmt(saved) + '，' + fmt(st.allTeamUserCount) + ' 人已抢，参与马上开团';
      promo.hidden = false;
    } else promo.hidden = true;

    // 底部栏价格
    $('bbPrice').textContent = fmt(pay);
    $('bbOrigin').textContent = fmt(origin);
  }

  /* ---------------- 拼单列表（草图1） ---------------- */
  function renderTeamList(teamList) {
    var myTeam = null, teams = [];
    teamList.forEach(function (t) {
      if (isExpiredTeam(t)) return;               // 过滤已失效团
      if (t.userId === state.user.id) myTeam = t; // 我的团置顶标记
      else teams.push(t);
    });
    state.myTeam = myTeam;
    state.teams = teams;

    var list = $('groupList'), empty = $('groupEmpty'), more = $('teamMore');
    if (teams.length === 0 && !myTeam) {
      list.innerHTML = '';
      empty.hidden = false;
      more.textContent = '等你开团';
    } else {
      empty.hidden = true;
      more.textContent = '共 ' + (myTeam ? teams.length + 1 : teams.length) + ' 个团';
      var html = '';
      if (myTeam) html += teamItemHtml(myTeam, true);
      html += teams.map(function (t) { return teamItemHtml(t, false); }).join('');
      list.innerHTML = html;
    }
    bindTeamButtons();
    startTicker();
  }

  function teamItemHtml(t, isMine) {
    var target = Number(t.targetCount) || 1;
    var done = Number(t.completeCount) || 0;
    var complete = done >= target;
    var need = Math.max(0, target - done);
    var leaderName = userNameById(t.userId);
    var cd = cdText(toMs(t.validEndTime) - Date.now());

    var statusText = complete ? '已成团' : '拼单即将结束';
    var leaderHtml = isMine
      ? '团长：<b>我</b> · ' + done + '/' + target + ' 人'
      : '团长：<b>' + esc(leaderName) + '</b> · ' + done + '/' + target + ' 人';

    var btnText, disabled = false, attr = '';
    if (complete) { btnText = '已满员'; disabled = true; }
    else if (isMine) { btnText = '我的团'; disabled = true; }
    else { btnText = '立即抢单'; attr = ' data-team="' + esc(t.teamId) + '"'; }

    return '<li class="group-item">' +
      '<div class="group-item__info">' +
        '<div class="group-item__status">' + statusText + '</div>' +
        '<div class="group-item__leader">' + leaderHtml + '</div>' +
      '</div>' +
      '<div class="group-item__side">' +
        '<div class="group-item__cd" data-end="' + toMs(t.validEndTime) + '">' + cd + '</div>' +
        '<button class="group-item__cta"' + (disabled ? ' disabled' : '') + attr + '>' + btnText + '</button>' +
      '</div>' +
    '</li>';
  }

  function bindTeamButtons() {
    Array.prototype.forEach.call(document.querySelectorAll('#groupList .group-item__cta'), function (btn) {
      btn.onclick = function () {
        if (state.busy) return;
        var teamId = btn.getAttribute('data-team');
        if (teamId) startBuy(teamId);
      };
    });
  }

  var ticker = null;
  function startTicker() {
    if (ticker) return;
    ticker = setInterval(function () {
      var nodes = document.querySelectorAll('.group-item__cd');
      for (var i = 0; i < nodes.length; i++) {
        var ms = Number(nodes[i].getAttribute('data-end')) - Date.now();
        nodes[i].textContent = cdText(ms);
      }
    }, 1000);
  }

  /* ================= 下单 ================= */
  function guardCanBuy() {
    if (!state.goods || !state.goods.activityId) { toast('商品数据未就绪', 'err'); return false; }
    return true;
  }

  // 单独购买：后端无单独购买链路，仅提示（草图保留按钮）
  function doSingle() {
    toast(cfg.singleBuyTip);
  }

  // 开团购买（teamId = null）
  function doOpen() {
    if (!guardCanBuy()) return;
    if (state.myTeam) { toast('你已有进行中的团，可切换账号来参你的团', 'err'); return; }
    startBuy(null);
  }

  // teamId 为空=开新团，否则=参团
  function startBuy(teamId) {
    if (!guardCanBuy()) return;
    if (teamId && state.myTeam) { toast('你已有进行中的团，请先完成或切换账号', 'err'); return; }
    state.busy = true;
    setLockBusy(true);
    var outTradeNo = API.newOutTradeNo();

    API.lockMarketPayOrder({
      userId: state.user.id,
      activityId: state.goods.activityId,
      teamId: teamId,
      outTradeNo: outTradeNo
    }).then(function (res) {
      state.busy = false;
      setLockBusy(false);
      if (!API.isOk(res)) { toast(codeText(res), 'err'); return; }
      state.pendingPay = {
        outTradeNo: outTradeNo,
        orderId: (res.data && res.data.orderId) || outTradeNo,
        amount: state.goods.payPrice,
        mode: teamId ? 'join' : 'open'
      };
      $('payAmount').textContent = fmt(state.pendingPay.amount);
      drawQr('payQr', outTradeNo);
      showModal('payModal');
    }).catch(function (err) {
      state.busy = false;
      setLockBusy(false);
      toast(err.message, 'err');
    });
  }

  function setLockBusy(b) {
    $('buyGroup').disabled = b;
    $('buySingle').disabled = b;
    $('buyGroup').classList.toggle('buybtn--busy', b);
  }

  // 伪二维码：以 outTradeNo 为种子画一个二维码样式的占位图
  function drawQr(canvasId, seed) {
    var cv = $(canvasId), ctx = cv.getContext('2d');
    var n = 25, s = cv.width / n;
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, cv.width, cv.height);
    ctx.fillStyle = '#222';
    // 简易确定性随机
    var rnd = 0;
    for (var i = 0; i < seed.length; i++) rnd = (rnd * 31 + seed.charCodeAt(i)) >>> 0;
    function r() { rnd = (rnd * 1103515245 + 12345) >>> 0; return rnd / 4294967296; }
    for (var y = 0; y < n; y++) {
      for (var x = 0; x < n; x++) {
        if (inFinder(x, y, n)) continue;
        if (r() > 0.52) ctx.fillRect(x * s, y * s, s, s);
      }
    }
    drawFinder(ctx, 0, 0, s);
    drawFinder(ctx, n - 7, 0, s);
    drawFinder(ctx, 0, n - 7, s);
  }
  function inFinder(x, y, n) {
    return (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7);
  }
  function drawFinder(ctx, x0, y0, s) {
    ctx.fillStyle = '#222'; ctx.fillRect(x0 * s, y0 * s, 7 * s, 7 * s);
    ctx.fillStyle = '#fff'; ctx.fillRect((x0 + 1) * s, (y0 + 1) * s, 5 * s, 5 * s);
    ctx.fillStyle = '#222'; ctx.fillRect((x0 + 2) * s, (y0 + 2) * s, 3 * s, 3 * s);
  }

  /* ---------------- 支付完成 → 结算 ---------------- */
  function doSettle() {
    if (!state.pendingPay) return;
    var pp = state.pendingPay;
    var doneBtn = $('payDone');
    doneBtn.classList.add('busy'); doneBtn.disabled = true;

    API.settlementMarketPayOrder({
      userId: state.user.id,
      outTradeNo: pp.outTradeNo
    }).then(function (res) {
      doneBtn.classList.remove('busy'); doneBtn.disabled = false;
      if (!API.isOk(res)) {
        toast(codeText(res), 'err');
        hideModal('payModal');
        state.pendingPay = null;
        refresh(false);
        return;
      }
      hideModal('payModal');
      state.pendingPay = null;

      API.queryGroupBuyMarketConfig(state.user.id).then(function (res2) {
        if (API.isOk(res2)) {
          var d = res2.data || {};
          state.goods = d.goods || state.goods;
          state.stat = d.teamStatistic || state.stat;
          renderGoods();
          renderTeamList(d.teamList || []);
        }
        var my = state.myTeam;
        var done = my ? Number(my.completeCount || 0) : 0;
        var target = my ? Number(my.targetCount || 1) : 1;
        var complete = !!(my && done >= target);
        var teamId = (res.data && res.data.teamId) || (my && my.teamId);

        if (pp.mode === 'join') {
          $('resultTitle').textContent = complete ? '拼单成功 · 已成团' : '拼单成功';
          $('resultEmoji').textContent = complete ? '🎉' : '🛒';
          $('resultMsg').textContent = complete ? '团已凑齐，等待商家发货' : '已加入 TA 的团，还差 ' + Math.max(0, target - done) + ' 人成团';
        } else {
          $('resultTitle').textContent = complete ? '开团成功 · 已成团' : '开团成功';
          $('resultEmoji').textContent = complete ? '🎉' : '🚩';
          $('resultMsg').textContent = complete
            ? '团已凑齐，等待商家发货'
            : '团长你好！还差 ' + Math.max(0, target - done) + ' 人成团，切换账号来参你的团吧';
        }
        $('resultTeam').textContent = teamId ? '团号：#' + teamId : '';
        showModal('resultModal');
      });
    }).catch(function (err) {
      doneBtn.classList.remove('busy'); doneBtn.disabled = false;
      toast(err.message, 'err');
    });
  }

  /* ================= 事件 ================= */
  function bindEvents() {
    // 登录
    $('loginBtn').addEventListener('click', doLogin);
    $('loginUser').addEventListener('keydown', function (e) { if (e.key === 'Enter') doLogin(); });
    $('loginPass').addEventListener('keydown', function (e) { if (e.key === 'Enter') doLogin(); });

    // 切换用户：回到登录页
    $('userChip').addEventListener('click', function () { showLogin(state.user ? state.user.id : ''); });

    // 底部按钮
    $('buySingle').addEventListener('click', doSingle);
    $('buyGroup').addEventListener('click', doOpen);

    // 支付
    $('payCancel').addEventListener('click', function () {
      hideModal('payModal');
      state.pendingPay = null;
      toast('已取消，订单未支付');
    });
    $('payDone').addEventListener('click', doSettle);

    // 重试
    $('retryBtn').addEventListener('click', function () { refresh(true); });

    // 点遮罩/关闭按钮关弹窗
    document.addEventListener('click', function (e) {
      var closer = e.target.closest('[data-close]');
      if (closer) hideModal(closer.getAttribute('data-close'));
    });

    // 静默自动刷新（倒计时/成团进度）
    setInterval(function () {
      var anyOpen = !$('payModal').hidden || !$('resultModal').hidden || !$('loginView').hidden;
      if (!anyOpen && !state.busy && state.goods) {
        API.queryGroupBuyMarketConfig(state.user.id).then(function (res) {
          if (API.isOk(res)) {
            var d = res.data || {};
            state.goods = d.goods || state.goods;
            state.stat = d.teamStatistic || state.stat;
            renderGoods();
            renderTeamList(d.teamList || []);
          }
        }).catch(function () { /* 静默失败 */ });
      }
    }, cfg.refreshInterval);
  }

  /* ---------------- 启动 ---------------- */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
