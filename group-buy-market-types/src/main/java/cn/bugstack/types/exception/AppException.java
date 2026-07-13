package cn.bugstack.types.exception;

import cn.bugstack.types.enums.ResponseCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 5317680961212299217L;

    /** 异常码 */
    private String code;

    /** 异常信息 */
    private String info;

    public AppException(String code) {
        super(code);
        this.code = code;
    }

    public AppException(ResponseCode responseCode){
        super(responseCode.getCode() + " " + responseCode.getInfo());
        this.code = responseCode.getCode();
        this.info = responseCode.getInfo();
    }

    public AppException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public AppException(String code, String message) {
        super(code + " " + message);
        this.code = code;
        this.info = message;
    }

    public AppException(String code, String message, Throwable cause) {
        super(code + " " + message, cause);
        this.code = code;
        this.info = message;
    }

    @Override
    public String toString() {
        return "cn.bugstack.x.api.types.exception.XApiException{" +
                "code='" + code + '\'' +
                ", info='" + info + '\'' +
                '}';
    }

}
