package club.zhaobin.practice.utils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author zhaobin
 * @date 2021/6/19 22:07
 */
public class ZbApplicationContext {
    private Map<String, Object> beanMap;

    public static ZbApplicationContext initStartWithObject(Class<?> startObjectClass) {
        ZbApplicationContext context = new ZbApplicationContext();
        context.beanMap = new HashMap<>(64);
        initDependentBeans(context.beanMap, startObjectClass);
        return context;
    }

    private static void initDependentBeans(Map<String, Object> beanMap, Class<?> startObjectClass) {
        Object targetObject = null;
        try {
            targetObject = startObjectClass.newInstance();
        } catch (Exception e) {
            System.out.println("init bean:" + startObjectClass.getName() + " failed!");
            e.printStackTrace();
        }
        beanMap.putIfAbsent(startObjectClass.getName(), targetObject);
        // 递归处理startObjectClass的成员bean
        Arrays.stream(startObjectClass.getDeclaredFields())
                .filter(ZbApplicationContext::isSpringBean)
                .map(field -> field.getType())
                .forEach(fieldClass -> initDependentBeans(beanMap, fieldClass));
    }

    private static boolean isSpringBean(Field field) {
        return Arrays.stream(field.getDeclaredAnnotations())
                .anyMatch(annotation -> annotation.annotationType() == Autowired.class);
    }

    private ZbApplicationContext() {
    }

    private ZbApplicationContext(Map<String, Object> beanMap) {
        this.beanMap = beanMap;
    }

    private <T> T getBeanByName(String beanName, Class<T> classType) {
        Object bean = beanMap.get(beanName);
        assert (bean != null);
        return (T) beanMap.get(beanName);
    }

    public static void main(String[] args) {
//        ZbApplicationContext context = ZbApplicationContext.initStartWithObject(ServiceBImpl.class);
//        ServiceBImpl serviceB = context.getBeanByName(ServiceBImpl.class.getName(), ServiceBImpl.class);
//        System.out.println(serviceB);
//        serviceB.printB();
    }
}
