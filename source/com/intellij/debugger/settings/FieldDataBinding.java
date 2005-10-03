/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.settings;

import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Field;

import com.intellij.debugger.DebuggerBundle;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 12, 2005
 */
public abstract class FieldDataBinding implements DataBinding{

  private final String myFieldName;

  protected FieldDataBinding(@NonNls String fieldName) {
    myFieldName = fieldName;
  }

  public final void loadData(Object from) {
    try {
      final Field field = findField(from);
      doLoadData(from, field);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }


  public final void saveData(Object to) {
    try {
      final Field field = findField(to);
      doSaveData(to, field);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public boolean isModified(Object obj) {
    try {
      final Field field = findField(obj);
      return isModified(obj, field);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  protected abstract void doLoadData(Object from, Field field) throws IllegalAccessException;

  protected abstract void doSaveData(Object to, Field field) throws IllegalAccessException;

  protected abstract boolean isModified(Object obj, Field field) throws IllegalAccessException;

  private Field findField(Object from) {
    final Class objectClass = Object.class;
    for (Class aClass = from.getClass(); !aClass.equals(objectClass); aClass = aClass.getSuperclass()) {
      try {
        final Field field = aClass.getDeclaredField(myFieldName);
        field.setAccessible(true);
        return field;
      }
      catch (NoSuchFieldException e) {
        // ignored, just continue
      }
    }
    throw new RuntimeException(DebuggerBundle.message("error.field.not.found.in.class", myFieldName, from.getClass().getName()));
  }
}
