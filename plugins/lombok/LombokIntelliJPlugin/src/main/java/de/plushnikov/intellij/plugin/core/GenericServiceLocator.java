package de.plushnikov.intellij.plugin.core;

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class GenericServiceLocator {
  private static final Logger log = Logger.getInstance(GenericServiceLocator.class.getName());

  private GenericServiceLocator() {
  }

  public static <T> T locate(final Class<T> clazz) {
    final List<T> services = locateAll(clazz);
    return services.isEmpty() ? (T) null : services.get(0);
  }

  public static <T> List<T> locateAll(final Class<T> clazz) {
    ServiceLoader<T> loader = ServiceLoader.load(clazz, clazz.getClassLoader());

    final List<T> services = new ArrayList<T>();
    for (T service : loader) {
      try {
        services.add(service);
      } catch (Exception ex) {
        log.warn(ex);
      }
    }
    return services;

  }
}
