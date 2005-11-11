/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class CollectionChildDescriptionImpl extends DomChildDescriptionImpl implements DomCollectionChildDescription {
  private final Method myGetterMethod, myAdderMethod, myIndexedAdderMethod;

  public CollectionChildDescriptionImpl(final String tagName,
                                        final Type type,
                                        final Method getterMethod,
                                        final Method adderMethod,
                                        final Method indexedAdderMethod) {
    super(tagName, type);
    myAdderMethod = adderMethod;
    myGetterMethod = getterMethod;
    myIndexedAdderMethod = indexedAdderMethod;
  }

  public Method getAdderMethod() {
    return myAdderMethod;
  }

  public Method getGetterMethod() {
    return myGetterMethod;
  }

  public Method getIndexedAdderMethod() {
    return myIndexedAdderMethod;
  }

  public List<DomElement> getChildren(final DomElement element) {
    try {
      return (List<DomElement>)myGetterMethod.invoke(element);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final CollectionChildDescriptionImpl that = (CollectionChildDescriptionImpl)o;

    if (myAdderMethod != null ? !myAdderMethod.equals(that.myAdderMethod) : that.myAdderMethod != null) return false;
    if (myGetterMethod != null ? !myGetterMethod.equals(that.myGetterMethod) : that.myGetterMethod != null) return false;
    if (myIndexedAdderMethod != null ? !myIndexedAdderMethod.equals(that.myIndexedAdderMethod) : that.myIndexedAdderMethod != null) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + (myGetterMethod != null ? myGetterMethod.hashCode() : 0);
    result = 29 * result + (myAdderMethod != null ? myAdderMethod.hashCode() : 0);
    result = 29 * result + (myIndexedAdderMethod != null ? myIndexedAdderMethod.hashCode() : 0);
    return result;
  }
}
