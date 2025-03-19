// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */

public class PyParameterInfo implements ParameterInfo {

  private final int myOldIndex;
  private String myName = "";
  private String myOldName = "";
  private String myDefaultValue = null;
  private boolean myDefaultInSignature = false;

  public PyParameterInfo(int index) {
    myOldIndex = index;
  }

  public PyParameterInfo(int oldIndex, String name, @Nullable String defaultValue, boolean defaultInSignature) {
    myOldIndex = oldIndex;
    myName = name;
    myOldName = name;
    myDefaultValue = defaultValue;
    myDefaultInSignature = defaultInSignature;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  public @NotNull String getOldName() {
    return myOldName;
  }

  @Override
  public int getOldIndex() {
    return myOldIndex;
  }

  @Override
  public @Nullable String getDefaultValue() {
    return myDefaultValue;
  }

  @Override
  public void setName(String name) {
    myName = name;
  }

  public void setDefaultValue(String defaultValue) {
    myDefaultValue = defaultValue;
  }

  @Override
  public String getTypeText() {
    return "";
  }

  @Override
  public boolean isUseAnySingleVariable() {
    return false;
  }

  @Override
  public void setUseAnySingleVariable(boolean b) {
    throw new UnsupportedOperationException();
  }

  public boolean getDefaultInSignature() {
    return myDefaultInSignature;
  }

  public void setDefaultInSignature(boolean defaultInSignature) {
    myDefaultInSignature = defaultInSignature;
  }

  public boolean isRenamed() {
    return !myOldName.equals(myName);
  }

  @Override
  public String toString() {
    final String defaultValueType = myDefaultInSignature ? "signature" : "call";
    return "PyParameterInfo(" +
           (isNew() ? "<new>" : "oldIndex: " + myOldIndex) + ", " +
           "name: " + (isRenamed() ? myOldName + " -> " + myName : myName) + ", " +
           "default: " + (StringUtil.isEmpty(myDefaultValue) ? "<empty>" : "'" + myDefaultValue + "' (" + defaultValueType + ")") +
           ")";
  }
}
