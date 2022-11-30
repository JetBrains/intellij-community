// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.ObjectStubBase;
import com.intellij.psi.stubs.Stub;
import com.intellij.util.SmartList;
import com.intellij.util.xml.EvaluatedXmlNameImpl;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.CollectionElementInvocationHandler;
import com.intellij.util.xml.impl.DomChildDescriptionImpl;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class DomStub extends ObjectStubBase<DomStub> {
  @NotNull private final String myName;
  @NotNull private final String myLocalName;
  @Nullable private final String myNamespace;
  private DomInvocationHandler myHandler;

  DomStub(DomStub parent, @NotNull String name, @Nullable String namespace) {
    super(parent);
    myNamespace = namespace;
    if (parent != null) {
      ((ElementStub)parent).addChild(this);
    }
    myName = name;
    myLocalName = StringUtil.getShortName(myName, ':');
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public String getNamespaceKey() {
    return myNamespace;
  }

  public boolean matches(XmlName name) {
    return name.getLocalName().equals(myLocalName) &&
           StringUtil.notNullize(name.getNamespaceKey()).equals(getNamespaceKey());
  }

  public List<DomStub> getChildrenByName(XmlName xmlName) {
    final List<? extends Stub> stubs = getChildrenStubs();
    if (stubs.isEmpty()) {
      return Collections.emptyList();
    }

    final List<DomStub> result = new SmartList<>();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = stubs.size(); i < size; i++) {
      final Stub stub = stubs.get(i);
      if (stub instanceof DomStub && ((DomStub)stub).matches(xmlName)) {
        result.add((DomStub)stub);
      }
    }
    return result;
  }

  @Nullable
  public AttributeStub getAttributeStub(final XmlName name) {
    final List<? extends Stub> stubs = getChildrenStubs();
    if (stubs.isEmpty()) {
      return null;
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = stubs.size(); i < size; i++) {
      final Stub stub = stubs.get(i);
      if (stub instanceof AttributeStub &&
          ((AttributeStub)stub).getName().equals(name.getLocalName())) {
        return (AttributeStub)stub;
      }
    }
    return null;
  }

  @Nullable
  public ElementStub getElementStub(String name, int index) {
    List<? extends Stub> stubs = getChildrenStubs();
    int i = 0;
    for (Stub stub : stubs) {
      if (stub instanceof ElementStub && name.equals(((ElementStub)stub).getName()) && i++ == index) {
        return (ElementStub)stub;
      }
    }
    return null;
  }

  public synchronized DomInvocationHandler getOrCreateHandler(DomChildDescriptionImpl description, DomManagerImpl manager) {
    if (myHandler == null) {
      XmlName name = description.getXmlName();
      EvaluatedXmlNameImpl evaluatedXmlName = EvaluatedXmlNameImpl.createEvaluatedXmlName(name, name.getNamespaceKey(), true);
      myHandler = new CollectionElementInvocationHandler(evaluatedXmlName, description, manager, (ElementStub)this);
    }
    return myHandler;
  }

  public DomInvocationHandler getHandler() {
    return myHandler;
  }

  public void setHandler(DomInvocationHandler handler) {
    myHandler = handler;
  }

  public boolean isCustom() {
    return false;
  }

  public abstract int getIndex();

}
