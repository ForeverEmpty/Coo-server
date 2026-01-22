package org.foreverempty.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.annotation.PrivacyField;

import java.lang.reflect.Field;

@Slf4j
public class PrivacyUtils {
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
