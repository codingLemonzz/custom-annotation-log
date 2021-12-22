package cn.codinglemon.annolog.bean;

import lombok.Data;

import java.util.Date;

/**
 * author zry
 * date 2021-12-20 18:44
 */
@Data
public class LogDTO {

    /**
     * 日志id,由UUID生成，唯一
     */
    private String logId;
    /**
     *唯一业务ID，可以是订单ID，可以是用户ID等，支持spEL表达式(必须)
     */
    private String uuId;
    /**
     * 业务类型（必须）
     */
    private String serviceType;
    /**
     * 错误时的信息
     */
    private String exception;
    /**
     * 操作时间
     */
    private Date operateDate;
    /**
     * 日志生成是否成功
     */
    private Boolean success;
    /**
     * 需要传递的其他数据，支持（spEL表达式）
     */
    private String massage;
    /**
     * 自定义标签
     */
    private String tag;
    /**
     * 返回的数据
     */
    private String returnStr;
}
