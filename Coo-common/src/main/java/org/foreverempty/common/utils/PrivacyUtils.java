package org.foreverempty.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.annotation.PrivacyField;

import java.lang.reflect.Field;

@Slf4j
public class PrivacyUtils {
    /**
     * 隐私字段过滤工具方法
     * <p>
     * 该方法用于根据隐私设置动态过滤VO对象中的敏感字段。通过反射机制，遍历VO对象中所有带有
     * {@link PrivacyField} 注解的字段，根据info对象中对应开关字段的值来决定是否将VO字段设置为null。
     * 当开关字段值为0时，表示该字段为隐私字段，需要被过滤掉（设置为null）。
     * </p>
     * <p>
     * 使用场景：当需要向第三方用户展示用户信息时，根据用户的隐私设置动态隐藏敏感字段。
     * 例如，用户可以设置性别、生日、签名等信息是否公开。
     * </p>
     * <p>
     * 实现原理：
     * 1. 遍历VO对象中所有字段，筛选出带有{@link PrivacyField}注解的字段
     * 2. 获取注解中指定的开关字段名
     * 3. 从info对象中获取对应的开关字段值
     * 4. 如果开关字段值为0，则将VO对象中的对应字段设置为null
     * </p>
     *
     * @param vo   待过滤的VO对象，其字段需使用{@link PrivacyField}注解标记隐私字段
     * @param info 包含隐私设置开关的信息对象，其字段名与{@link PrivacyField}注解的value值对应
     * @throws IllegalArgumentException 如果vo或info为null，则直接返回，不进行处理
     * @throws SecurityException        如果在反射过程中遇到安全权限问题
     * @see PrivacyField
     */
    public static void applyPrivacy(Object vo, Object info) {
        if (vo == null || info == null) return;

        Field[] voFields = vo.getClass().getDeclaredFields();
        for (Field voField : voFields) {
            PrivacyField annotation = voField.getAnnotation(PrivacyField.class);

            if (annotation != null) {
                try {
                    String switchName = annotation.value();
                    Field infoField = info.getClass().getDeclaredField(switchName);
                    infoField.setAccessible(true);
                    Integer isPublic = (Integer) infoField.get(info);

                    if (Integer.valueOf(0).equals(isPublic)) {
                        voField.setAccessible(true);
                        voField.set(vo, null);
                    }
                } catch (Exception e) {
                    log.error("Privacy Filter Error: {}", voField.getName(), e);
                }
            }
        }
    }
}
