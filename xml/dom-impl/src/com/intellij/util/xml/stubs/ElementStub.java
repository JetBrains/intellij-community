// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.Stub;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ElementStub extends DomStub {
  private final List<Stub> myChildren = new SmartList<>();
  private final int myIndex;
  private final boolean myCustom;

  @Nullable
  private final String myElementClass;
  private final String myValue;

  public ElementStub(@Nullable ElementStub parent,
                     @NotNull String name,
                     @Nullable String namespace,
                     int index,
                     boolean custom,
                     @Nullable String elementClass,
                     @NotNull String value) {
    super(parent, name, namespace);
    myIndex = index;
    myCustom = custom;
    myElementClass = elementClass;
    myValue = value;
  }

  void addChild(Stub child) {
    myChildren.add(child);
  }

  @NotNull
  @Override
  public List<? extends Stub> getChildrenStubs() {
    return myChildren;
  }

  @Override
  public ObjectStubSerializer<?, Stub> getStubType() {
    return DomElementTypeHolder.ElementStubSerializer;
  }

  @Override
  public String toString() {
    String key = getNamespaceKey();
    return (StringUtil.isEmpty(key) ? getName() : key + ":" + getName()) +
           (StringUtil.isEmpty(getValue()) ? "" : ":" + getValue());
  }

  @Override
  public boolean isCustom() {
    return myCustom;
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Nullable
  String getElementClass() {
    return myElementClass;
  }

  @NotNull
  public String getValue() {
    return myValue;
  }
}
