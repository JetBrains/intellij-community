/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.reflect.DomChildDescription;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public abstract class DomChildDescriptionImpl implements DomChildDescription {
  private final String myTagName;
  private final Type myType;

  protected DomChildDescriptionImpl(final String tagName, final Type type) {
    myTagName = tagName;
    myType = type;
  }

  public String getTagName() {
    return myTagName;
  }

  public Type getType() {
    return myType;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DomChildDescriptionImpl that = (DomChildDescriptionImpl)o;

    if (myTagName != null ? !myTagName.equals(that.myTagName) : that.myTagName != null) return false;
    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myTagName != null ? myTagName.hashCode() : 0);
    result = 29 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
