package io.github.buhuiwandexiaobai.spring.context;

import static java.lang.Thread.currentThread;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * @author zhaobin
 * @date 2021/6/19 22:07
 */
public class ZbApplicationContext {
    private Map<String, Object> beanMap;
    private Multimap<String, Class> classMap;

    public static ZbApplicationContext initStartWithObject(
            Class<?> targetObjectClass, String... packageNames) {
        ZbApplicationContext
                context = new ZbApplicationContext();
        context.beanMap = new HashMap<>();
        context.classMap = ArrayListMultimap.create();
        loadAllClasses(context.classMap, packageNames);
        initDependentBeans(context.beanMap, context.classMap, targetObjectClass);
        return context;
    }

    private static void loadAllClasses(Multimap<String, Class> classMap, String... packageNames) {
        for (String packageName : packageNames) {
            packageName = packageName.replace('.', '/');
            List<Class> classList = loadClassFromPackage(packageName);
            classList.addAll(loadClassFromJar(packageName));
            classList.stream()
                    .filter(clazz -> !clazz.isInterface())
                    .forEach(clazz -> {
                        Arrays.stream(clazz.getInterfaces())
                                .forEach(superClass -> classMap.put(superClass.getName(), clazz));
                    });
        }
    }

    private static void initDependentBeans(Map<String, Object> beanMap, Multimap<String, Class> classMap,
                                           Class<?> targetObjectClass) {
        try {

            final Object targetObject = buildFieldObj(beanMap, classMap, targetObjectClass);
            beanMap.putIfAbsent(targetObjectClass.getName(), targetObject);
            // 递归处理startObjectClass的成员bean
            Arrays.stream(targetObject.getClass().getDeclaredFields())
                    .filter(ZbApplicationContext::isSpringBean)
                    .filter(field -> {
                        try {
                            field.setAccessible(true);
                            return field.get(targetObject) == null;
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        return false;
                    })
                    .peek(field -> {
                        try {
                            writeField(field, targetObject, buildFieldObj(beanMap, classMap, field.getType()));
                        } catch (InstantiationException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    })
                    .map(fieldClass -> fieldClass.getType())
                    .forEach(fieldClass -> initDependentBeans(beanMap, classMap, fieldClass));
        } catch (Exception e) {
            System.out.println("init bean:" + targetObjectClass.getName() + " failed!");
            e.printStackTrace();
        }
    }

    @Nullable
    private static Object buildFieldObj(Map<String, Object> beanMap, Multimap<String, Class> classMap, Class fieldClass)
            throws InstantiationException, IllegalAccessException {
        if (beanMap.containsKey(fieldClass.getName())) {
            return beanMap.get(fieldClass.getName());
        }
        Object instance;
        if (!fieldClass.isInterface()) {
            try {
                instance = fieldClass.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            instance = classMap.get(fieldClass.getName()).stream().findFirst().get()
                    .newInstance();
        }
        beanMap.putIfAbsent(fieldClass.getName(), instance);
        return instance;
    }

    private static boolean isSpringBean(Field field) {
        return Arrays.stream(field.getDeclaredAnnotations())
                .anyMatch(annotation -> annotation.annotationType() == Autowired.class);
    }

    private ZbApplicationContext() {
    }

    public <T> T getBeanByType(Class<T> classType) {
        Object bean = beanMap.get(classType.getName());
        assert (bean != null);
        return (T) bean;
    }

    private static Collection<Class> loadClassFromJar(String packageName) {
        List<Class> list = new ArrayList<>();
        Enumeration<URL> urlEnumeration = null;
        try {
            urlEnumeration = currentThread().getContextClassLoader().getResources(packageName);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        while (urlEnumeration.hasMoreElements()) {
            try {
                URLConnection urlConnection = urlEnumeration.nextElement().openConnection();
                if (!(urlConnection instanceof JarURLConnection)) {
                    continue;
                }
                Enumeration<JarEntry> jarEntries = ((JarURLConnection) urlConnection)
                        .getJarFile()
                        .entries();
                while (jarEntries.hasMoreElements()) {
                    jarEntries.nextElement();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    private static List<Class> loadClassFromPackage(String packageName) {
        List<Class> list = new ArrayList<>();
        Enumeration<URL> urlEnumeration = null;
        try {
            urlEnumeration = currentThread().getContextClassLoader().getResources(packageName);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        while (urlEnumeration.hasMoreElements()) {
            URL url = urlEnumeration.nextElement();
            list.addAll(getClassFromPackage(packageName, url.getPath()));
        }
        return list;
    }

    public static void writeField(Field field, Object targetObj, Object value) {
        field.setAccessible(true);
        try {
            field.set(targetObj, value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static List<Class> getClassFromPackage(String packageName, String packagePath) {
        List<Class> list = new ArrayList<>();
        File packageDir = new File(packagePath);
        if (!packageDir.isDirectory()) {
            return Collections.emptyList();
        }
        Arrays.stream(Optional.ofNullable(packageDir.listFiles(dir -> isClass(dir))).orElse(new File[0]))
                .forEach(file -> list.add(loadClass(packageName, file)));
        Arrays.stream(Optional.ofNullable(packageDir.listFiles(File::isDirectory)).orElse(new File[0]))
                .forEach(dir -> list.addAll(getClassFromPackage(packageName, dir.getAbsolutePath())));
        return list;
    }

    private static Class loadClass(String packageName, File file) {
        String className = file.getAbsolutePath().substring(file.getAbsolutePath().indexOf(packageName));
        try {
            Class<?> aClass = currentThread().getContextClassLoader()
                    .loadClass(className.replace('/', '.').substring(0, className.length() - 6));
            return aClass;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean isClass(File file) {
        return file.isFile() && file.getName().endsWith(".class");
    }
}
