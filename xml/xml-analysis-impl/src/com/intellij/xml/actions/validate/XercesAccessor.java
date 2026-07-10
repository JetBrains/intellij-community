// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.actions.validate;

import org.apache.xerces.impl.XMLEntityManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Hashtable;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public final class XercesAccessor {
  private static final MethodHandle GET_DECLARED_ENTITIES;

  static {
    try {
      GET_DECLARED_ENTITIES =
        MethodHandles.privateLookupIn(XMLEntityManager.class, MethodHandles.lookup())
          .findVirtual(XMLEntityManager.class, "getDeclaredEntities", MethodType.methodType(Hashtable.class));
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, XMLEntityManager.Entity> getEntities(XMLEntityManager entityManager) {
    try {
      return (Hashtable<String, XMLEntityManager.Entity>) GET_DECLARED_ENTITIES.invoke(entityManager);
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
