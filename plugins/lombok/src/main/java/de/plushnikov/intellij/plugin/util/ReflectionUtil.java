package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.Field;

/**
 * @author Plushnikov Michail
 */
public class ReflectionUtil {
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
    } catch (IllegalArgumentException x) {
      LOG.error(x);
    } catch (IllegalAccessException x) {
      LOG.error(x);
    }
  }
}
