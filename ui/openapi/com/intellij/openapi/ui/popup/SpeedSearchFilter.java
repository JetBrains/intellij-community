/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

public interface SpeedSearchFilter<T> {

  boolean canBeHidden(T value);

  String getIndexedString(T value);

}
