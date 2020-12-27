package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.Field;

/**
 * @author Plushnikov Michail
 */
public final class ReflectionUtil {
  private static final Logger LOG = Logger.getInstance(ReflectionUtil.class.getName());

  public static <T, R> void setFinalFieldPerReflection(Class<T> clazz, T instance, Class<R> fieldClass, R newValue) {
    try {
      for (Field field : clazz.getDeclaredFields()) {
        if (field.getType().equals(fieldClass)) {
          field.setAccessible(true);
          field.set(instance, newValue);
          break;
        }
      }
    } catch (IllegalArgumentException | IllegalAccessException x) {
      LOG.error(x);
    }
  }
}
