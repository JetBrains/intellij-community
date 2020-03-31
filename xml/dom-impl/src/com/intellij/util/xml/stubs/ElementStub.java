/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  public ObjectStubSerializer<?,?> getStubType() {
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
