package cn.bugstack.infrastructure.typehandler;

import cn.bugstack.domain.activity.model.valobj.DiscountTypeEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DiscountTypeEnumTypeHandler extends BaseTypeHandler<DiscountTypeEnum> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, DiscountTypeEnum parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public DiscountTypeEnum getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int code = rs.getInt(columnName);
        if (rs.wasNull()) {
            return null;
        }
        return DiscountTypeEnum.get(code);
    }

    @Override
    public DiscountTypeEnum getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int code = rs.getInt(columnIndex);
        if (rs.wasNull()) {
            return null;
        }
        return DiscountTypeEnum.get(code);
    }

    @Override
    public DiscountTypeEnum getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int code = cs.getInt(columnIndex);
        if (cs.wasNull()) {
            return null;
        }
        return DiscountTypeEnum.get(code);
    }
}
