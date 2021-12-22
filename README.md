同学们好，最近在思考如何在面试中体现自己的项目亮点时，看到了3y大佬的文章，文章链接如下：
> [Java3y——如何使用注解优雅的记录操作日志](https://mp.weixin.qq.com/s/Ucu2eVML2C4cAu100z_YSw)

那么本着知其然也要知其所以然的态度，我想着自己能不能实现通过注解来实现记录操作日志呢？

在自己一番尝试之后，最终实现了出来！因此这篇文章用来介绍我自己实现的注解式日志记录！

> 部分文章内容参考和借用了3y大佬这篇文章中的内容，望悉知。

# 1. 为什么要记录操作日志？

我们首先要清楚，操作日志是用来干什么的。

操作日志主要是指对某个对象进行新增操作或者修改操作后**记录下这个新增或者修改**，操作日志要求可读性比较强，因为它主要是给用户看的，比如订单的物流信息，用户需要知道在什么时间发生了什么事情。

例如基本在所有的后台系统中，都会有日志来记录每个人的操作历史：

![image.png](http://www.codinglemon.cn/upload/2021/12/image-f2edc09e276b4828b619a97b6521c403.png)

所以，对于操作日志，最基本的要求是记录：

> **某人 ——> 在某个时间 ——> 调用了某个接口 ——> 传入了某些参数 ——> 接口返回了某些数据**

那么根据抽象原则，我们可以使用一种统一的方式去管理我们的操作日志。

# 2. 常见的操作日志实现方式

在小型项目中，这种日志记录的操作通常会以提供一个接口或整个日志记录Service来实现。那么放到多人共同开发的项目中，除了封装一个方法，还有什么更好的办法来统一实现操作日志的记录？下面就要讨论下在Java中，常见的操作日志实现方式。

当你需要给一个大型系统从头到尾加上操作日志，那么除了上述的手动处理方式，也有很多种整体设计方案：

## 2.1 使用Canal监听数据库记录操作日志

Canal应运而生，它通过伪装成数据库的从库，读取主库发来的binlog，用来实现数据库增量订阅和消费业务需求。这里可以看蛮三刀大佬的这篇文章：

[阿里开源MySQL中间件Canal快速入门](https://mp.weixin.qq.com/s?__biz=MzU1NTA0NTEwMg==&mid=2247484273&idx=1&sn=7fec41a40e763df094c0dd675330808a&chksm=fbdb1af0ccac93e676c2a0c6aeb1ff3edfe43b30969a7c1bbe19ccf7270acd6e41e6812caf0d&token=541499407&lang=zh_CN&scene=21#wechat_redirect)

这个方式有点是和业务逻辑完全分离，缺点也很大，需要使用到MySQL的Binlog，向DBA申请就有点困难。如果涉及到修改第三方接口，那么就无法监听别人的数据库了。所以调用RPC接口时，就需要额外的在业务代码中增加记录代码，破坏了“和业务逻辑完全分离”这个基本原则，局限性大。

## 2.2 通过日志文件的方式记录

```java
log.info("订单已经创建，订单编号:{}", orderNo)
log.info("修改了订单的配送地址：从“{}”修改到“{}”， "金灿灿小区", "银盏盏小区")
```

这种方式，需要手动的设定好操作日志和其他日志的区别，**比如给操作日志单独的Logger。** 并且，对于操作人的记录，需要在函数中额外的写入请求的上下文中。**后期这种日志还需要在SLS等日志系统中做额外的抽取。**

## 2.3 通过 LogUtil 的方式记录日志

```java
LogUtil.log(orderNo, "订单创建", "小明")
LogUtil.log(orderNo, "订单创建，订单号"+"NO.11089999",  "小明")
String template = "用户%s修改了订单的配送地址：从“%s”修改到“%s”"
LogUtil.log(orderNo, String.format(tempalte, "小明", "金灿灿小区", "银盏盏小区"),  "小明")
```

这种方式会导致业务的逻辑比较繁杂，最后导致 LogUtils.logRecord() 方法的调用存在于很多业务的代码中，而且类似 getLogContent() 这样的方法也散落在各个业务类中，对于代码的可读性和可维护性来说是一个灾难。

## 2.4 方法注解实现操作日志

我们可以在注解的操作日志上记录固定文案，这样业务逻辑和业务代码可以做到解耦，让我们的业务代码变得纯净起来。这也是本篇文章我们需要实现的部分。

```java
@GetMapping(value = "/page")
@AnnoLog(uuId = "#name",serviceType = "查询列表",massage = "{name:#name,index:#index,size:#size}")
    public ResponseBean selectPageByName(@RequestParam(value = "name",required = false)String name,
                                         @RequestParam(value = "index")Integer index,
                                         @RequestParam(value = "size")Integer size)
```

# 3.实现自定义注解记录操作日志

如果对如何实现不感兴趣的可以直接看第4节：<a href="#1">如何在自己的项目中使用</a>

## 3.1 操作日志实体类LogDTO

在实现自定义注解之前，我们需要先想一想，记录操作日志时我们需要记录哪些信息。

这里我定义了一个实体类，具体的实现如下：

```java
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
```

## 3.2 自定义注解 AnnoLog

下面是实现自定义注解的代码：基本上每个注解都有对应的解释：

```java
package cn.codinglemon.annolog.annotation;

import java.lang.annotation.*;

/**
 * author zry
 * date 2021-12-20 16:01
 */
//注解作用的目标
@Target({ElementType.METHOD})  
//注解的保留位置,这种类型的Annotations将被JVM保留,所以他们能在运行时被JVM或其他使用反射机制的代码所读取和使用
@Retention(RetentionPolicy.RUNTIME) 
//该注解将被包含在javadoc中 
@Documented  
//表明该注解可以进行重复标注
@Repeatable(AnnoLogs.class) 
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
```

为了实现一个方法上可以进行多个注解标注，添加了对应的多标注interface

```java
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
```

这里可以看到，注解是只有接口，没有对应的实现的，具体的实现是在Spring AOP的切片中。

## 3.3 AOP 实现注解功能类 AnnoLogAspect

下面是利用Spring AOP实现具体的操作日志记录功能，具体实现流程如下：

1. 在contorller或者service方法执行前，也就是@Before注释的方法下，将方法传入的自定义参数记录，这里使用了spEL的方式记录传入方法的参数。将对应的实体对象logDTO存入ThreadLocal的数组中（这里使用ThreadLocal是为了实现多线程处理注解）
> 不了解spEL的可以看一下这篇文章：[SpEL表达式总结](https://www.jianshu.com/p/e0b50053b5d3)

2. 在方法执行完毕后，将返回的结果存入刚才ThreadLocal中的List数组的logDTO对象中。也就是@Around实现的方法中。


代码如下：

```java
package cn.codinglemon.annolog.aop;

import cn.codinglemon.annolog.annotation.AnnoLog;
import cn.codinglemon.annolog.bean.LogDTO;
import cn.codinglemon.annolog.service.AnnoLogService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.NamedThreadLocal;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * author zry
 * date 2021-12-20 16:37
 */
@Component
@Aspect
@Slf4j
public class AnnoLogAspect {

    @Autowired
    private AnnoLogService annoLogService;

    /**
     * 创建ExpressionParser解析表达式
     */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    private static final ThreadLocal<List<LogDTO>> LOGDTO_THREAD_LOCAL = new NamedThreadLocal<>("ThreadLocal logDTOList");

    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Before(value = "@annotation(cn.codinglemon.annolog.annotation.AnnoLog) || @annotation(cn.codinglemon.annolog.annotation.AnnoLogs)")
    public void before(JoinPoint joinPoint){
        try{
        List<LogDTO> logDTOList = new ArrayList<>();
        LOGDTO_THREAD_LOCAL.set(logDTOList);

        Object[] arguments = joinPoint.getArgs();
        //获得方法对象
        Method method = getMethod(joinPoint);
        //获取方法的批量注解
        AnnoLog[] annotations = method.getAnnotationsByType(AnnoLog.class);
        for(AnnoLog annotation: annotations){
            LogDTO logDTO = new LogDTO();
            logDTOList.add(logDTO);
            String uuIdSpEL = annotation.uuId();
            String massageSpEL = annotation.massage();
            String uuId = uuIdSpEL;
            String massage = massageSpEL;

            try{
                String[] params = discoverer.getParameterNames(method);
                EvaluationContext context = new StandardEvaluationContext();
                if(params != null){
                    for (int len = 0; len <params.length ; len++) {
                        context.setVariable(params[len],arguments[len]);
                    }
                }

                // uuId 处理：直接传入字符串会抛出异常，写入默认传入的字符串
                if (StringUtils.isNotBlank(uuIdSpEL)) {
                    //表达式放置
                    Expression bizIdExpression = parser.parseExpression(uuId);
                    //执行表达式，默认容器是spring本身的容器：ApplicationContext
                    uuId = bizIdExpression.getValue(context, String.class);
                }
                // massage 处理，写入默认传入的字符串
                if (StringUtils.isNotBlank(massageSpEL)) {
                    Expression msgExpression = parser.parseExpression(massageSpEL);
                    Object msgObj = msgExpression.getValue(context, Object.class);
                    massage = JSON.toJSONString(msgObj, SerializerFeature.WriteMapNullValue);
                }
            }catch (Exception e){
                log.error("SystemLogAspect doBefore error",e);
            }finally {
                logDTO.setLogId(UUID.randomUUID().toString());
                logDTO.setSuccess(true);
                logDTO.setUuId(uuId);
                logDTO.setServiceType(annotation.serviceType());
                logDTO.setOperateDate(new Date());
                logDTO.setMassage(massage);
                logDTO.setTag(annotation.tag());
            }
        }

        } catch (Exception e){
            log.error("SystemLogAspect doBefore error",e);
        }
    }

    private Method getMethod(JoinPoint joinPoint){
        Method method = null;
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Object target = joinPoint.getTarget();
        try {
            method = target.getClass().getMethod(methodSignature.getName(),methodSignature.getParameterTypes());
        } catch (NoSuchMethodException e) {
            log.error("SystemLogAspect getMethod error",e);
        }
        return method;
    }

    @Around(value = "@annotation(cn.codinglemon.annolog.annotation.AnnoLog) || @annotation(cn.codinglemon.annolog.annotation.AnnoLogs)")
    public Object doAround(ProceedingJoinPoint pjp) throws Throwable{
        Object result;
        try {
            result = pjp.proceed();
            // logDTO写入返回值信息 若方法抛出异常，则不会走入下方逻辑
            List<LogDTO> logDTOList = LOGDTO_THREAD_LOCAL.get();
            String returnStr = JSON.toJSONString(result);
            logDTOList.forEach(logDTO -> logDTO.setReturnStr(returnStr));
        }catch (Throwable throwable){
            // logDTO写入异常信息
            List<LogDTO> logDTOList = LOGDTO_THREAD_LOCAL.get();
            logDTOList.forEach(logDTO -> {
                logDTO.setSuccess(false);
                logDTO.setException(throwable.getMessage());
            });
            throw throwable;
        } finally {
            // logDTO发送至数据管道
            List<LogDTO> logDTOList = LOGDTO_THREAD_LOCAL.get();
            logDTOList.forEach(logDTO -> {
                try {
                    annoLogService.saveLog(logDTO);
                } catch (Throwable throwable) {
                    log.error("logRecord send message failure", throwable);
                }
            });
            LOGDTO_THREAD_LOCAL.remove();
        }
        return result;
    }
}
```

## 3.4 存入数据的接口类 AnnoLogService

那么操作日志应该存在哪里呢？每个项目可能都不同，最简单的当然是直接存入MYSQL中。但是很多项目并不是，因为记录操作日志是一个与主线业务无关的操作，可能有的单独用一个另外一个服务去存入；可能有的是丢到消息中间件比如RoketMQ、RabbitMQ中......

所以这里并没有实现具体的存入操作日志的方法，而是定义的AnnoLogService，只需要你自己去实现AnnoLogService接口，就可以实现具体的存入操作日志数据的行为。**实现了记录与存取的解耦。**

AnnoLogService具体代码如下：

```java
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
```

## 3.5 其他

其实到这里，所有功能代码都已经完成了，为了保证打包成jar包后可以直接被其他项目调用，这里还有一些其他代码，也罗列如下：

AnnoLogAutoConfig类：

```java
package cn.codinglemon.annolog.config;

import org.springframework.context.annotation.ComponentScan;

/**
 * author zry
 * date 2021-12-22 9:54
 */
@ComponentScan("cn.codinglemon.annolog")
public class AnnoLogAutoConfig {
}
```

resource文件夹下创建META-INF，创建配置文件spring.factories

```java
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  cn.codinglemon.annolog.config.AnnoLogAutoConfig
```

另外把完整的pom.xml也放在这里：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.5.1</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>cn.codinglemon</groupId>
    <artifactId>annolog</artifactId>
    <version>1.0.0</version>
    <name>custom-annotation-log</name>
    <description>a project used for custom annotation log</description>
    <properties>
        <java.version>1.8</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.12.0</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.20</version>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.72</version>
        </dependency>
    </dependencies>

    <developers>
        <developer>
            <name>Ruyi Zhong</name>
            <email>zry15671554200@qq.com</email>
            <organization>codinglemon</organization>
        </developer>
    </developers>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
                <version>2.2.1</version>
                <executions>
                    <execution>
                        <id>attach-sources</id>
                        <goals>
                            <goal>jar-no-fork</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <version>2.9.1</version>
                <configuration>
                    <javadocExecutable>${java.home}/../bin/javadoc</javadocExecutable>
                </configuration>
                <executions>
                    <execution>
                        <id>attach-javadocs</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-gpg-plugin</artifactId>
                <version>1.5</version>
                <executions>
                    <execution>
                        <id>sign-artifacts</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>sign</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

</project>
```

<a name="1"></a>
# 4. 如何在自己的项目中使用

## 4.1 下载jar包，放入项目
首先将[annolog-1.0.0.jar](http://www.codinglemon.cn/upload/2021/12/annolog-1.0.0-8889ba88d1db419a86da1f28215e12b1.jar) 下载到本地，在自己项目的根目录下创建名为lib的文件夹，并将jar包放入，如下：

![image.png](http://www.codinglemon.cn/upload/2021/12/image-cce3fae305424cec9a957111b1ff199d.png)

## 4.2 导入pom.xml 
然后在pom.xml中添加两个依赖：

```xml
        <!--引入本地log jar-->
        <dependency>
            <groupId>cn.codinglemon</groupId>
            <artifactId>annolog</artifactId>
            <version>1.0.0</version>
            <scope>system</scope>
            <systemPath>${pom.basedir}/lib/annolog-1.0.0.jar</systemPath>
        </dependency>

        <!--注解所需依赖-->
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.1</version>
        </dependency>
```

## 4.3 实现存入操作日志的方法
然后实现存日志的AnnoLogService方法，我这里只是简单将他打印出来了。代码如下，记得该方法要加@Service注解。

```java
package com.wisdoing.school.config;
import cn.codinglemon.annolog.bean.LogDTO;
import cn.codinglemon.annolog.service.AnnoLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author zry
 * @date 2021-12-20 20:26
 */
@Service
@Slf4j
public class LogServiceImpl implements AnnoLogService {

    @Override
    public Object saveLog(LogDTO logDTO) {
        log.info(logDTO.toString());
        return null;
    }
}
```
## 4.4 在项目中使用
然后我们就可以愉快的在项目中使用注解日志啦！具体操作如下：

```java
    @GetMapping(value = "/page")
    @AnnoLog(uuId = "#userId",serviceType = "查询列表",massage = "{userId:#userId,index:#index,size:#size}",tag = "监控设备页面")
    public ResponseBean selectPage(@RequestParam(value = "userId")Integer userId,
                                         @RequestParam(value = "index")Integer index,
                                         @RequestParam(value = "size")Integer size){
```

其中

- **uuId(必须，支持spEL表达式)**：表示操作日志记录的操作人员、或者订单等的id，这里的id应该保证是唯一的。（因为一个完整的系统中，用户、订单的id肯定要保持唯一）
- **serviceType(必须)**: 表明当前业务的类型
- **massage（非必须，支持spEL表达式）**：需要传递的其他数据，这里主要存放方法传入的参数，这里我将接口传入的三个参数都放进去了。**这里要保证数据是JSON格式的。**
- **tag（非必须）**：自定义标签，这里可以存放需要的一些其他信息

好，到这里就已经大功告成了！我们来看看访问这个接口后日志记录了哪些数据。

```java
2021-12-22 19:17:13.965  INFO 41192 --- [nio-8800-exec-1] cn.codinglemon.demo.config.LogServiceImpl  : LogDTO(logId=2bb62939-8b94-45a5-92a9-bc2daa811eb7, uuId=1, serviceType=查询列表, exception=null, operateDate=Wed Dec 22 19:17:13 CST 2021, success=true, massage={"uuId":"1","index":1,"size":10}, tag=监控设备页面, returnStr={"code":20002,"data":{......})
```

这里可以看到，打印除了logDTO这个对象的：

- **logId**：日志id，用UUID生成，全局唯一
- **uuId**：前端传入的uuid，这里是项目中的唯一id，表明谁访问了这个接口
- **serviceType**：是我们自己传入的业务类型数据
- **exception**：如果接口未出错，为Null；如果出错，会记录错误信息
- **operateDate**：操作日志记录时间
- **success**：为true表示操作日志记录成功
- **massage**：接口传入的数据，这里是我们用spEL实现的自定义的数据
- **tag**：我们在注解中自定义的数据
- **returnStr**：接口返回给前端的数据

**那么有的时候前端传到后端的id是存在header的token中的怎么取到值呢（比如最简单的用JWT）？**

这里其实是spEL的相关知识，可以这么写：

```java
    @AnnoLog(uuId = "#request.getHeader(\"token\")",serviceType = "查询列表",massage = "{uuId:#uuId,index:#index,size:#size}",tag = "监控设备页面")
```

就可以获取到token的值了。

# 5.其他问题

**1. 有项目的完整源码吗？**

有的，已经放在github上了，自己clone下来就可以：

[custom-annnotaion-log](https://github.com/codingLemonzz/custom-annnotaion-log)

方便的话点一个star！谢谢！^ . ^

**2. 自己用有bug或者有其他问题可以问你吗？**

非常欢迎！可以在github中提issue或者添加我的微信号：codinglemon，随时找我咨询！

**3.可以用在自己的项目中或者商用吗？**

当然可以！唯一的小小要求就是希望可以github中给一个star就好！












同学们好，最近在思考如何在面试中体现自己的项目亮点时，看到了3y大佬的文章，文章链接如下：
> [Java3y——如何使用注解优雅的记录操作日志](https://mp.weixin.qq.com/s/Ucu2eVML2C4cAu100z_YSw)

那么本着知其然也要知其所以然的态度，我想着自己能不能实现通过注解来实现记录操作日志呢？

在自己一番尝试之后，最终实现了出来！因此这篇文章用来介绍我自己实现的注解式日志记录！

> 部分文章内容参考和借用了3y大佬这篇文章中的内容，望悉知。

# 1. 为什么要记录操作日志？

我们首先要清楚，操作日志是用来干什么的。

操作日志主要是指对某个对象进行新增操作或者修改操作后**记录下这个新增或者修改**，操作日志要求可读性比较强，因为它主要是给用户看的，比如订单的物流信息，用户需要知道在什么时间发生了什么事情。

例如基本在所有的后台系统中，都会有日志来记录每个人的操作历史：

![image.png](http://www.codinglemon.cn/upload/2021/12/image-f2edc09e276b4828b619a97b6521c403.png)

所以，对于操作日志，最基本的要求是记录：

> **某人 ——> 在某个时间 ——> 调用了某个接口 ——> 传入了某些参数 ——> 接口返回了某些数据**

那么根据抽象原则，我们可以使用一种统一的方式去管理我们的操作日志。

# 2. 常见的操作日志实现方式

在小型项目中，这种日志记录的操作通常会以提供一个接口或整个日志记录Service来实现。那么放到多人共同开发的项目中，除了封装一个方法，还有什么更好的办法来统一实现操作日志的记录？下面就要讨论下在Java中，常见的操作日志实现方式。

当你需要给一个大型系统从头到尾加上操作日志，那么除了上述的手动处理方式，也有很多种整体设计方案：

## 2.1 使用Canal监听数据库记录操作日志

Canal应运而生，它通过伪装成数据库的从库，读取主库发来的binlog，用来实现数据库增量订阅和消费业务需求。这里可以看蛮三刀大佬的这篇文章：

[阿里开源MySQL中间件Canal快速入门](https://mp.weixin.qq.com/s?__biz=MzU1NTA0NTEwMg==&mid=2247484273&idx=1&sn=7fec41a40e763df094c0dd675330808a&chksm=fbdb1af0ccac93e676c2a0c6aeb1ff3edfe43b30969a7c1bbe19ccf7270acd6e41e6812caf0d&token=541499407&lang=zh_CN&scene=21#wechat_redirect)

这个方式有点是和业务逻辑完全分离，缺点也很大，需要使用到MySQL的Binlog，向DBA申请就有点困难。如果涉及到修改第三方接口，那么就无法监听别人的数据库了。所以调用RPC接口时，就需要额外的在业务代码中增加记录代码，破坏了“和业务逻辑完全分离”这个基本原则，局限性大。

## 2.2 通过日志文件的方式记录

```java
log.info("订单已经创建，订单编号:{}", orderNo)
log.info("修改了订单的配送地址：从“{}”修改到“{}”， "金灿灿小区", "银盏盏小区")
```

这种方式，需要手动的设定好操作日志和其他日志的区别，**比如给操作日志单独的Logger。** 并且，对于操作人的记录，需要在函数中额外的写入请求的上下文中。**后期这种日志还需要在SLS等日志系统中做额外的抽取。**

## 2.3 通过 LogUtil 的方式记录日志

```java
LogUtil.log(orderNo, "订单创建", "小明")
LogUtil.log(orderNo, "订单创建，订单号"+"NO.11089999",  "小明")
String template = "用户%s修改了订单的配送地址：从“%s”修改到“%s”"
LogUtil.log(orderNo, String.format(tempalte, "小明", "金灿灿小区", "银盏盏小区"),  "小明")
```

这种方式会导致业务的逻辑比较繁杂，最后导致 LogUtils.logRecord() 方法的调用存在于很多业务的代码中，而且类似 getLogContent() 这样的方法也散落在各个业务类中，对于代码的可读性和可维护性来说是一个灾难。

## 2.4 方法注解实现操作日志

我们可以在注解的操作日志上记录固定文案，这样业务逻辑和业务代码可以做到解耦，让我们的业务代码变得纯净起来。这也是本篇文章我们需要实现的部分。

```java
@GetMapping(value = "/page")
@AnnoLog(uuId = "#name",serviceType = "查询列表",massage = "{name:#name,index:#index,size:#size}")
    public ResponseBean selectPageByName(@RequestParam(value = "name",required = false)String name,
                                         @RequestParam(value = "index")Integer index,
                                         @RequestParam(value = "size")Integer size)
```

# 3.实现自定义注解记录操作日志

如果对如何实现不感兴趣的可以直接看第4节：<a href="#1">如何在自己的项目中使用</a>

## 3.1 操作日志实体类LogDTO

在实现自定义注解之前，我们需要先想一想，记录操作日志时我们需要记录哪些信息。

这里我定义了一个实体类，具体的实现如下：

```java
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
```

## 3.2 自定义注解 AnnoLog

下面是实现自定义注解的代码：基本上每个注解都有对应的解释：

```java
package cn.codinglemon.annolog.annotation;

import java.lang.annotation.*;

/**
 * author zry
 * date 2021-12-20 16:01
 */
//注解作用的目标
@Target({ElementType.METHOD})  
//注解的保留位置,这种类型的Annotations将被JVM保留,所以他们能在运行时被JVM或其他使用反射机制的代码所读取和使用
@Retention(RetentionPolicy.RUNTIME) 
//该注解将被包含在javadoc中 
@Documented  
//表明该注解可以进行重复标注
@Repeatable(AnnoLogs.class) 
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
```

为了实现一个方法上可以进行多个注解标注，添加了对应的多标注interface

```java
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
```

这里可以看到，注解是只有接口，没有对应的实现的，具体的实现是在Spring AOP的切片中。

## 3.3 AOP 实现注解功能类 AnnoLogAspect

下面是利用Spring AOP实现具体的操作日志记录功能，具体实现流程如下：

1. 在contorller或者service方法执行前，也就是@Before注释的方法下，将方法传入的自定义参数记录，这里使用了spEL的方式记录传入方法的参数。将对应的实体对象logDTO存入ThreadLocal的数组中（这里使用ThreadLocal是为了实现多线程处理注解）
> 不了解spEL的可以看一下这篇文章：[SpEL表达式总结](https://www.jianshu.com/p/e0b50053b5d3)

2. 在方法执行完毕后，将返回的结果存入刚才ThreadLocal中的List数组的logDTO对象中。也就是@Around实现的方法中。


代码如下：

```java
package cn.codinglemon.annolog.aop;

import cn.codinglemon.annolog.annotation.AnnoLog;
import cn.codinglemon.annolog.bean.LogDTO;
import cn.codinglemon.annolog.service.AnnoLogService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.NamedThreadLocal;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * author zry
 * date 2021-12-20 16:37
 */
@Component
@Aspect
@Slf4j
public class AnnoLogAspect {

    @Autowired
    private AnnoLogService annoLogService;

    /**
     * 创建ExpressionParser解析表达式
     */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    private static final ThreadLocal<List<LogDTO>> LOGDTO_THREAD_LOCAL = new NamedThreadLocal<>("ThreadLocal logDTOList");

    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Before(value = "@annotation(cn.codinglemon.annolog.annotation.AnnoLog) || @annotation(cn.codinglemon.annolog.annotation.AnnoLogs)")
    public void before(JoinPoint joinPoint){
        try{
        List<LogDTO> logDTOList = new ArrayList<>();
        LOGDTO_THREAD_LOCAL.set(logDTOList);

        Object[] arguments = joinPoint.getArgs();
        //获得方法对象
        Method method = getMethod(joinPoint);
        //获取方法的批量注解
        AnnoLog[] annotations = method.getAnnotationsByType(AnnoLog.class);
        for(AnnoLog annotation: annotations){
            LogDTO logDTO = new LogDTO();
            logDTOList.add(logDTO);
            String uuIdSpEL = annotation.uuId();
            String massageSpEL = annotation.massage();
            String uuId = uuIdSpEL;
            String massage = massageSpEL;

            try{
                String[] params = discoverer.getParameterNames(method);
                EvaluationContext context = new StandardEvaluationContext();
                if(params != null){
                    for (int len = 0; len <params.length ; len++) {
                        context.setVariable(params[len],arguments[len]);
                    }
                }

                // uuId 处理：直接传入字符串会抛出异常，写入默认传入的字符串
                if (StringUtils.isNotBlank(uuIdSpEL)) {
                    //表达式放置
                    Expression bizIdExpression = parser.parseExpression(uuId);
                    //执行表达式，默认容器是spring本身的容器：ApplicationContext
                    uuId = bizIdExpression.getValue(context, String.class);
                }
                // massage 处理，写入默认传入的字符串
                if (StringUtils.isNotBlank(massageSpEL)) {
                    Expression msgExpression = parser.parseExpression(massageSpEL);
                    Object msgObj = msgExpression.getValue(context, Object.class);
                    massage = JSON.toJSONString(msgObj, SerializerFeature.WriteMapNullValue);
                }
            }catch (Exception e){
                log.error("SystemLogAspect doBefore error",e);
            }finally {
                logDTO.setLogId(UUID.randomUUID().toString());
                logDTO.setSuccess(true);
                logDTO.setUuId(uuId);
                logDTO.setServiceType(annotation.serviceType());
                logDTO.setOperateDate(new Date());
                logDTO.setMassage(massage);
                logDTO.setTag(annotation.tag());
            }
        }

        } catch (Exception e){
            log.error("SystemLogAspect doBefore error",e);
        }
    }

    private Method getMethod(JoinPoint joinPoint){
        Method method = null;
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Object target = joinPoint.getTarget();
        try {
            method = target.getClass().getMethod(methodSignature.getName(),methodSignature.getParameterTypes());
        } catch (NoSuchMethodException e) {
            log.error("SystemLogAspect getMethod error",e);
        }
        return method;
    }

    @Around(value = "@annotation(cn.codinglemon.annolog.annotation.AnnoLog) || @annotation(cn.codinglemon.annolog.annotation.AnnoLogs)")
    public Object doAround(ProceedingJoinPoint pjp) throws Throwable{
        Object result;
        try {
            result = pjp.proceed();
            // logDTO写入返回值信息 若方法抛出异常，则不会走入下方逻辑
            List<LogDTO> logDTOList = LOGDTO_THREAD_LOCAL.get();
            String returnStr = JSON.toJSONString(result);
            logDTOList.forEach(logDTO -> logDTO.setReturnStr(returnStr));
        }catch (Throwable throwable){
            // logDTO写入异常信息
            List<LogDTO> logDTOList = LOGDTO_THREAD_LOCAL.get();
            logDTOList.forEach(logDTO -> {
                logDTO.setSuccess(false);
                logDTO.setException(throwable.getMessage());
            });
            throw throwable;
        } finally {
            // logDTO发送至数据管道
            List<LogDTO> logDTOList = LOGDTO_THREAD_LOCAL.get();
            logDTOList.forEach(logDTO -> {
                try {
                    annoLogService.saveLog(logDTO);
                } catch (Throwable throwable) {
                    log.error("logRecord send message failure", throwable);
                }
            });
            LOGDTO_THREAD_LOCAL.remove();
        }
        return result;
    }
}
```

## 3.4 存入数据的接口类 AnnoLogService

那么操作日志应该存在哪里呢？每个项目可能都不同，最简单的当然是直接存入MYSQL中。但是很多项目并不是，因为记录操作日志是一个与主线业务无关的操作，可能有的单独用一个另外一个服务去存入；可能有的是丢到消息中间件比如RoketMQ、RabbitMQ中......

所以这里并没有实现具体的存入操作日志的方法，而是定义的AnnoLogService，只需要你自己去实现AnnoLogService接口，就可以实现具体的存入操作日志数据的行为。**实现了记录与存取的解耦。**

AnnoLogService具体代码如下：

```java
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
```

## 3.5 其他

其实到这里，所有功能代码都已经完成了，为了保证打包成jar包后可以直接被其他项目调用，这里还有一些其他代码，也罗列如下：

AnnoLogAutoConfig类：

```java
package cn.codinglemon.annolog.config;

import org.springframework.context.annotation.ComponentScan;

/**
 * author zry
 * date 2021-12-22 9:54
 */
@ComponentScan("cn.codinglemon.annolog")
public class AnnoLogAutoConfig {
}
```

resource文件夹下创建META-INF，创建配置文件spring.factories

```java
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  cn.codinglemon.annolog.config.AnnoLogAutoConfig
```

另外把完整的pom.xml也放在这里：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.5.1</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>cn.codinglemon</groupId>
    <artifactId>annolog</artifactId>
    <version>1.0.0</version>
    <name>custom-annotation-log</name>
    <description>a project used for custom annotation log</description>
    <properties>
        <java.version>1.8</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.12.0</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.20</version>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.72</version>
        </dependency>
    </dependencies>

    <developers>
        <developer>
            <name>Ruyi Zhong</name>
            <email>zry15671554200@qq.com</email>
            <organization>codinglemon</organization>
        </developer>
    </developers>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
                <version>2.2.1</version>
                <executions>
                    <execution>
                        <id>attach-sources</id>
                        <goals>
                            <goal>jar-no-fork</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <version>2.9.1</version>
                <configuration>
                    <javadocExecutable>${java.home}/../bin/javadoc</javadocExecutable>
                </configuration>
                <executions>
                    <execution>
                        <id>attach-javadocs</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-gpg-plugin</artifactId>
                <version>1.5</version>
                <executions>
                    <execution>
                        <id>sign-artifacts</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>sign</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

</project>
```

<a name="1"></a>
# 4. 如何在自己的项目中使用

## 4.1 下载jar包，放入项目
首先将[annolog-1.0.0.jar](http://www.codinglemon.cn/upload/2021/12/annolog-1.0.0-8889ba88d1db419a86da1f28215e12b1.jar) 下载到本地，在自己项目的根目录下创建名为lib的文件夹，并将jar包放入，如下：

![image.png](http://www.codinglemon.cn/upload/2021/12/image-cce3fae305424cec9a957111b1ff199d.png)

## 4.2 导入pom.xml 
然后在pom.xml中添加两个依赖：

```xml
        <!--引入本地log jar-->
        <dependency>
            <groupId>cn.codinglemon</groupId>
            <artifactId>annolog</artifactId>
            <version>1.0.0</version>
            <scope>system</scope>
            <systemPath>${pom.basedir}/lib/annolog-1.0.0.jar</systemPath>
        </dependency>

        <!--注解所需依赖-->
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.1</version>
        </dependency>
```

## 4.3 实现存入操作日志的方法
然后实现存日志的AnnoLogService方法，我这里只是简单将他打印出来了。代码如下，记得该方法要加@Service注解。

```java
package com.wisdoing.school.config;
import cn.codinglemon.annolog.bean.LogDTO;
import cn.codinglemon.annolog.service.AnnoLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author zry
 * @date 2021-12-20 20:26
 */
@Service
@Slf4j
public class LogServiceImpl implements AnnoLogService {

    @Override
    public Object saveLog(LogDTO logDTO) {
        log.info(logDTO.toString());
        return null;
    }
}
```
## 4.4 在项目中使用
然后我们就可以愉快的在项目中使用注解日志啦！具体操作如下：

```java
    @GetMapping(value = "/page")
    @AnnoLog(uuId = "#userId",serviceType = "查询列表",massage = "{userId:#userId,index:#index,size:#size}",tag = "监控设备页面")
    public ResponseBean selectPage(@RequestParam(value = "userId")Integer userId,
                                         @RequestParam(value = "index")Integer index,
                                         @RequestParam(value = "size")Integer size){
```

其中

- **uuId(必须，支持spEL表达式)**：表示操作日志记录的操作人员、或者订单等的id，这里的id应该保证是唯一的。（因为一个完整的系统中，用户、订单的id肯定要保持唯一）
- **serviceType(必须)**: 表明当前业务的类型
- **massage（非必须，支持spEL表达式）**：需要传递的其他数据，这里主要存放方法传入的参数，这里我将接口传入的三个参数都放进去了。**这里要保证数据是JSON格式的。**
- **tag（非必须）**：自定义标签，这里可以存放需要的一些其他信息

好，到这里就已经大功告成了！我们来看看访问这个接口后日志记录了哪些数据。

```java
2021-12-22 19:17:13.965  INFO 41192 --- [nio-8800-exec-1] cn.codinglemon.demo.config.LogServiceImpl  : LogDTO(logId=2bb62939-8b94-45a5-92a9-bc2daa811eb7, uuId=1, serviceType=查询列表, exception=null, operateDate=Wed Dec 22 19:17:13 CST 2021, success=true, massage={"uuId":"1","index":1,"size":10}, tag=监控设备页面, returnStr={"code":20002,"data":{......})
```

这里可以看到，打印除了logDTO这个对象的：

- **logId**：日志id，用UUID生成，全局唯一
- **uuId**：前端传入的uuid，这里是项目中的唯一id，表明谁访问了这个接口
- **serviceType**：是我们自己传入的业务类型数据
- **exception**：如果接口未出错，为Null；如果出错，会记录错误信息
- **operateDate**：操作日志记录时间
- **success**：为true表示操作日志记录成功
- **massage**：接口传入的数据，这里是我们用spEL实现的自定义的数据
- **tag**：我们在注解中自定义的数据
- **returnStr**：接口返回给前端的数据

**那么有的时候前端传到后端的id是存在header的token中的怎么取到值呢（比如最简单的用JWT）？**

这里其实是spEL的相关知识，可以这么写：

```java
    @AnnoLog(uuId = "#request.getHeader(\"token\")",serviceType = "查询列表",massage = "{uuId:#uuId,index:#index,size:#size}",tag = "监控设备页面")
```

就可以获取到token的值了。

# 5.其他问题

**1. 有项目的完整源码吗？**

有的，已经放在github上了，自己clone下来就可以：

[custom-annnotaion-log](https://github.com/codingLemonzz/custom-annnotaion-log)

方便的话点一个star！谢谢！^ . ^

**2. 自己用有bug或者有其他问题可以问你吗？**

非常欢迎！可以在github中提issue或者添加我的微信号：codinglemon，随时找我咨询！

**3.可以用在自己的项目中或者商用吗？**

当然可以！唯一的小小要求就是希望可以github中给一个star就好！












