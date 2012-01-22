package de.plushnikov.intellij.lombok.util;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.Field;

/**
 * @author Plushnikov Michail
 */
public class ReflectionUtil {
  private static final Logger LOG = Logger.getInstance(ReflectionUtil.class.getName());

  public static <T> void setFinalFieldPerReflection(Class<T> clazz, T instance, String fieldName, Object value) {
    try {
      Field f = clazz.getDeclaredField(fieldName);
      f.setAccessible(true);
      f.set(instance, value);
    } catch (NoSuchFieldException x) {
      LOG.error(x);
    } catch (IllegalArgumentException x) {
      LOG.error(x);
    } catch (IllegalAccessException x) {
      LOG.error(x);
    }
  }
}
