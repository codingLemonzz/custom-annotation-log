package cn.codinglemon.annolog.annotation;

import java.lang.annotation.*;

/**
 * author zry
 * date 2021-12-20 16:59
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AnnoLogs {
    AnnoLog[]value();
}
