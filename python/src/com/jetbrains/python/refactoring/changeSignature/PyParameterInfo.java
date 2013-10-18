package com.jetbrains.python.refactoring.changeSignature;

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

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  public String getOldName() {
    return myOldName;
  }

  @Override
  public int getOldIndex() {
    return myOldIndex;
  }

  @Nullable
  @Override
  public String getDefaultValue() {
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
}
