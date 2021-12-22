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
