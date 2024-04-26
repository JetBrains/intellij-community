// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.impl.*;
import com.intellij.xml.util.IncludedXmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class StubParentStrategy implements DomParentStrategy {

  private static final Logger LOG = Logger.getInstance(StubParentStrategy.class);
  protected final DomStub myStub;

  public StubParentStrategy(@NotNull DomStub stub) {
    myStub = stub;
  }

  public static StubParentStrategy createAttributeStrategy(@Nullable AttributeStub stub, final @NotNull DomStub parent) {
    if (stub == null) {
      return new Empty(parent);
    }
    else {
      return new StubParentStrategy(stub) {
        @Override
        public XmlElement getXmlElement() {
          DomInvocationHandler parentHandler = getParentHandler();
          if (parentHandler == null) {
            LOG.error("no parent handler for " + this);
            return null;
          }
          XmlTag tag = parentHandler.getXmlTag();
          if (tag == null) {
            LOG.error("can't find tag for " + parentHandler + "\n" +
                      "parent stub: " + myStub.getParentStub() + "\n" +
                      "parent's children: " + myStub.getParentStub().getChildrenStubs());
            return null;
          }
          return tag.getAttribute(myStub.getName());
        }
      };
    }
  }

  @Override
  public DomInvocationHandler getParentHandler() {
    DomStub parentStub = myStub.getParentStub();
    return parentStub == null ? null : parentStub.getHandler();
  }

  @Override
  public XmlElement getXmlElement() {
    DomStub parentStub = myStub.getParentStub();
    if (parentStub == null) return null;
    List<? extends Stub> children = parentStub.getChildrenStubs();
    if (children.isEmpty()) return null;
    XmlTag parentTag = parentStub.getHandler().getXmlTag();
    if (parentTag == null) return null;

    // for custom elements, namespace information is lost
    // todo: propagate ns info through DomChildDescriptions
    XmlTag[] tags;
    try {
      XmlUtil.BUILDING_DOM_STUBS.set(true);
      tags = parentTag.getSubTags();
    }
    finally {
      XmlUtil.BUILDING_DOM_STUBS.set(false);
    }

    int i = 0;
    String nameToFind = myStub.getName();
    for (XmlTag xmlTag : tags) {
      if (nameToFind.equals(xmlTag.getName()) && !(xmlTag instanceof IncludedXmlTag) && myStub.getIndex() == i++) {
        return xmlTag;
      }
    }
    return null;
  }

  @Override
  public @NotNull DomParentStrategy refreshStrategy(DomInvocationHandler handler) {
    return this;
  }

  @Override
  public @NotNull DomParentStrategy setXmlElement(@NotNull XmlElement element) {
    return new PhysicalDomParentStrategy(element, DomManagerImpl.getDomManager(element.getProject()));
  }

  @Override
  public @NotNull DomParentStrategy clearXmlElement() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "write operations should be performed on the DOM having a parent, your DOM may be not very fresh";
    return new VirtualDomParentStrategy(parent);
  }

  @Override
  public String checkValidity() {
    return null;
  }

  @Override
  public XmlFile getContainingFile(DomInvocationHandler handler) {
    return getParentHandler().getFile();
  }

  @Override
  public boolean isPhysical() {
    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof StubParentStrategy other)) {
      return PhysicalDomParentStrategy.strategyEquals(this, obj);
    }

    if (obj == this) return true;

    if (!other.getClass().equals(getClass())) return false;

    if (!other.myStub.equals(myStub)) return false;

    return Comparing.equal(getContainingFile(myStub.getHandler()),
                           other.getContainingFile(other.myStub.getHandler()));
  }

  public static class Empty extends StubParentStrategy {
    private final DomStub myParent;

    public Empty(DomStub parent) {
      super(parent);
      myParent = parent;
    }

    @Override
    public DomInvocationHandler getParentHandler() {
      return myParent.getHandler();
    }

    @Override
    public XmlElement getXmlElement() {
      return null;
    }

    @Override
    public boolean isPhysical() {
      return false;
    }
  }
}
