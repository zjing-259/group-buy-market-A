package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.Sku;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ISkuDao {
    Sku querySkuByGoodsId(String goodsId);
}
