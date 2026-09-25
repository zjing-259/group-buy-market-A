/**
 * 全局静态配置 —— 后端接口、演示数据、文案、图片等。
 * 动态价格/团数据一律来自后端 query_group_buy_market_config，不在此写死。
 */
window.APP_CONFIG = {
  // 后端地址：dev 端口 8091（见 group-buy-market-app application-dev.yml）
  apiBase: 'http://localhost:8091',

  // 接口路径
  path: {
    queryGroupBuyMarketConfig: '/api/v1/gbm/index/query_group_buy_market_config',
    lockMarketPayOrder:      '/api/v1/gbm/trade/lock-market-pay-order',
    settlementMarketPayOrder: '/api/v1/gbm/trade/settlement_market_pay_order'
  },

  // 商品定位参数（与种子数据一致：source=s01 channel=c01 goodsId=9890001）
  source: 's01',
  channel: 'c01',
  goodsId: '9890001',

  // 锁单必需的非空回调地址（后端 lock 会校验非空；只是占位，不真正回调）
  notifyUrl: 'http://127.0.0.1:8091/api/v1/test/group_buy_notify',

  // 演示账号：必须携带人群标签 RQ_KJHKL98UU78H66554GFDV（scope 1,2），否则会被拦截返回 0001
  defaultUserId: 'xiaofuge',
  users: [
    { id: 'xiaofuge', name: '小傅哥', tag: '团长',  color: '#e02e24' },
    { id: 'liergou',  name: '李二狗', tag: '学员',  color: '#f08c00' },
    { id: 'xfg01',    name: '学员01', tag: '老手', color: '#00a862' },
    { id: 'xfg02',    name: '学员02', tag: '路人', color: '#4a7dff' },
    { id: 'xfg03',    name: '学员03', tag: '路人', color: '#9c5cff' },
    { id: 'xfg04',    name: '学员04', tag: '新人', color: '#00a862' },
    { id: 'xfg05',    name: '学员05', tag: '新人', color: '#f08c00' },
    { id: 'xfg06',    name: '学员06', tag: '新人', color: '#4a7dff' },
    { id: 'xfg07',    name: '学员07', tag: '新人', color: '#e02e24' },
    { id: 'xfg08',    name: '学员08', tag: '新人', color: '#9c5cff' },
    { id: 'xfg09',    name: '学员09', tag: '新人', color: '#00a862' }
  ],

  // 轮播：本地 3 张不同商品图（已下载到 img/，可替换为任意图片）
  carouselImages: [
    'img/mybatis-01.png',
    'img/mybatis-02.png',
    'img/mybatis-03.png'
  ],

  // 商品名称/副标题兜底（后端 Goods 未返回 goodsName 时使用；建议后端补 goodsName 字段）
  productName: '手写MyBatis：渐进式源码实践',
  productSubtitle: '正品保障 · 3 人成团即发货 · 赠送配套源码与视频',

  // 促销横幅文案占位（渲染时以真实优惠数据覆盖）
  promoText: '',

  // 领券中心（纯前端演示，不做扣减）
  coupons: [
    { amount: '3',  title: '满30减3',   cond: '全店通用' },
    { amount: '10', title: '满100减10', cond: '拼团专用' }
  ],

  // 商品详情要点
  details: [
    '小傅哥《手写MyBatis：渐进式源码实践》',
    '从 XML 解析、SQL 会话到插件机制，逐步带你实现一个属于自己的 MyBatis',
    '附赠源码工程 + 视频讲解，边写边学，拒绝死记硬背',
    '全程 Java 原生代码，无第三方框架，适合有一定基础的同学'
  ],

  // 轮播自动播放间隔(ms)
  carouselInterval: 3200,

  // 静默自动刷新间隔(ms)：让倒计时/成团进度不刷新页面也能走动
  refreshInterval: 15000,

  // 单次购买 vs 拼团提示（后端无单独购买链路）
  singleBuyTip: '该活动商品仅支持拼团购买，开团或参团后享受拼团价'
};
