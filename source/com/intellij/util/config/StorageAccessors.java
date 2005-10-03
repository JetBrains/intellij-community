/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.config;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;

/**
 * @author dyoma
 */
public class StorageAccessors {
  private final Storage myStorage;

  public StorageAccessors(Storage storage) {
    myStorage = storage;
  }

  public static StorageAccessors createGlobal(@NonNls String prefix) {
    Application application = ApplicationManager.getApplication();
    Storage storage;
    if (application != null) storage = new Storage.PropertiesComponentStorage(prefix + ".");
    else storage = new Storage.MapStorage();
    return new StorageAccessors(storage);
  }

  public float getFloat(@NonNls String id, float defaultValue) {
    String value = myStorage.get(id);
    if (value == null) return defaultValue;
    try {
      return Float.parseFloat(value);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public void setFloat(String id, float value) {
    myStorage.put(id, String.valueOf(value));
  }

  public boolean getBoolean(String id, boolean defaultValue) {
    return Boolean.valueOf(myStorage.get(id));
  }

  public void setBoolean(String id, boolean value) {
    myStorage.put(id, String.valueOf(value));
  }
}
