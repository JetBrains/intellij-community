/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;


public interface UserDataHolder {
  <T> T getUserData(Key<T> key);

  <T> void putUserData(Key<T> key, T value);
}