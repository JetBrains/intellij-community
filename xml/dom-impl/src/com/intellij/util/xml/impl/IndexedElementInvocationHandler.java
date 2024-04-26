// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.stubs.ElementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.List;

public class IndexedElementInvocationHandler extends DomInvocationHandler {
  private static final Logger LOG = Logger.getInstance(IndexedElementInvocationHandler.class);
  private final int myIndex;

  public IndexedElementInvocationHandler(final EvaluatedXmlName tagName,
                                         final FixedChildDescriptionImpl description,
                                         final int index,
                                         final DomParentStrategy strategy,
                                         final DomManagerImpl manager,
                                         @Nullable ElementStub stub) {
    super(description.getType(), strategy, tagName, description, manager, strategy.isPhysical(), stub);
    myIndex = index;
  }

  public boolean equals(final Object obj) {
    return super.equals(obj) && myIndex == ((IndexedElementInvocationHandler)obj).myIndex;
  }

  public int hashCode() {
    return super.hashCode() * 239 + myIndex;
  }

  @Override
  protected @Nullable String getValue() {
    return myStub == null ? super.getValue() : ((ElementStub)myStub).getValue();
  }

  @Override
  protected XmlElement recomputeXmlElement(final @NotNull DomInvocationHandler parentHandler) {
    final XmlTag tag = parentHandler.getXmlTag();
    if (tag == null) return null;

    final List<XmlTag> tags = DomImplUtil.findSubTags(tag, getXmlName(), parentHandler.getFile());
    if (tags.size() <= myIndex) return null;

    return tags.get(myIndex);
  }

  @Override
  protected XmlTag setEmptyXmlTag() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "write operations should be performed on the DOM having a parent, your DOM may be not very fresh";
    final FixedChildDescriptionImpl description = getChildDescription();
    final XmlFile xmlFile = getFile();
    parent.createFixedChildrenTags(getXmlName(), description, myIndex);
    final List<XmlTag> tags = DomImplUtil.findSubTags(parent.getXmlTag(), getXmlName(), xmlFile);
    if (tags.size() > myIndex) {
      return tags.get(myIndex);
    }

    final XmlTag[] newTag = new XmlTag[1];
    getManager().runChange(() -> {
      try {
        final XmlTag parentTag = parent.getXmlTag();
        newTag[0] = (XmlTag)parentTag.add(parent.createChildTag(getXmlName()));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
    return newTag[0];
  }

  @Override
  public FixedChildDescriptionImpl getChildDescription() {
    return (FixedChildDescriptionImpl)super.getChildDescription();
  }

  @Override
  public void undefineInternal() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "write operations should be performed on the DOM having a parent, your DOM may be not very fresh";
    final XmlTag parentTag = parent.getXmlTag();
    if (parentTag == null) return;

    final EvaluatedXmlName xmlElementName = getXmlName();
    final FixedChildDescriptionImpl description = getChildDescription();

    final int totalCount = description.getCount();

    final List<XmlTag> subTags = DomImplUtil.findSubTags(parentTag, xmlElementName, getFile());
    if (subTags.size() <= myIndex) {
      return;
    }

    XmlTag tag = getXmlTag();
    if (tag == null) return;

    final boolean changing = getManager().setChanging(true);
    try {
      detach();
      if (totalCount == myIndex + 1 && subTags.size() >= myIndex + 1) {
        for (int i = myIndex; i < subTags.size(); i++) {
          subTags.get(i).delete();
        }
      }
      else if (subTags.size() == myIndex + 1) {
        tag.delete();
      } else {
        setXmlElement((XmlTag) tag.replace(parent.createChildTag(getXmlName())));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      getManager().setChanging(changing);
    }
    fireUndefinedEvent();
  }

  @Override
  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final T annotation = getChildDescription().getAnnotation(myIndex, annotationClass);
    if (annotation != null) return annotation;

    return getClassAnnotation(annotationClass);
  }

  @Override
  public final DomElement createPathStableCopy() {
    final DomFixedChildDescription description = getChildDescription();
    final DomElement parentCopy = getParent().createStableCopy();
    return getManager().createStableValue(
      (Factory<DomElement>)() -> parentCopy.isValid() ? description.getValues(parentCopy).get(myIndex) : null);
  }

}
