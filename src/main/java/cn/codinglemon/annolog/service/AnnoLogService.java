package cn.codinglemon.annolog.service;

import cn.codinglemon.annolog.bean.LogDTO;

/**
 * 用其他service继承AnnoLogService,来实现具体的存入数据库的相关操作
 * author zry
 * date 2021-12-20 20:06
 */
public interface AnnoLogService {

    /**
     * 将log存入数据库
     * param logDTO
     */
    Object saveLog(LogDTO logDTO);
}
