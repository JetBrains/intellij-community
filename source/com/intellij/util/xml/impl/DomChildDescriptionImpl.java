/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class DomChildDescriptionImpl implements DomChildrenDescription {
  private final String myTagName;
  private final Type myType;

  protected DomChildDescriptionImpl(final String tagName, final Type type) {
    myTagName = tagName;
    myType = type;
  }

  public String getXmlElementName() {
    return myTagName;
  }

  public List<? extends DomElement> getStableValues(DomElement parent) {
    final List<? extends DomElement> list = getValues(parent);
    final ArrayList<DomElement> result = new ArrayList<DomElement>(list.size());
    for (int i = 0; i < list.size(); i++) {
      final int i1 = i;
      result.add(parent.getManager().createStableValue(new Factory<DomElement>() {
        public DomElement create() {
          return list.get(i1);
        }
      }));
    }
    return result;
  }

  public Type getType() {
    return myType;
  }

  public String getCommonPresentableName(DomElement parent) {
    return getCommonPresentableName(getDomNameStrategy(parent));
  }

  public DomNameStrategy getDomNameStrategy(DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(DomUtil.getRawType(myType), false);
    return strategy == null ? parent.getNameStrategy() : strategy;
  }

  public final DomGenericInfo getChildGenericInfo(Project project) {
    return DomManager.getDomManager(project).getGenericInfo(myType);
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
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
