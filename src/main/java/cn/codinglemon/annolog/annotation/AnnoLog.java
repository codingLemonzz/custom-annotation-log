package cn.codinglemon.annolog.annotation;

import java.lang.annotation.*;

/**
 * author zry
 * date 2021-12-20 16:01
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})  //注解作用的目标
@Retention(RetentionPolicy.RUNTIME)  //注解的保留位置,这种类型的Annotations将被JVM保留,所以他们能在运行时被JVM或其他使用反射机制的代码所读取和使用
@Documented  //该注解将被包含在javadoc中
@Repeatable(AnnoLogs.class) //表明该注解可以进行重复标注
public @interface AnnoLog {

    //业务唯一id
    String uuId();

    //业务类型
    String serviceType();

    //需要传递的其他数据
    String massage() default "";

    //自定义标签
    String tag() default "operation";


}
