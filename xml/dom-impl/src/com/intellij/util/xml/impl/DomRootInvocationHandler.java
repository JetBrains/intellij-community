// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.stubs.ElementStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DomRootInvocationHandler extends DomInvocationHandler {
  private static final Logger LOG = Logger.getInstance(DomRootInvocationHandler.class);
  private final DomFileElementImpl<?> myParent;

  public DomRootInvocationHandler(final Class aClass,
                                  final RootDomParentStrategy strategy,
                                  @NotNull DomFileElementImpl<?> fileElement,
                                  final @NotNull EvaluatedXmlName tagName,
                                  @Nullable ElementStub stub
  ) {
    super(aClass, strategy, tagName, new AbstractDomChildDescriptionImpl(aClass) {
      @Override
      public @NotNull List<? extends DomElement> getValues(final @NotNull DomElement parent) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int compareTo(final AbstractDomChildDescriptionImpl o) {
        throw new UnsupportedOperationException();
      }
    }, fileElement.getManager(), true, stub);
    myParent = fileElement;
  }

  @Override
  public void undefineInternal() {
    try {
      final XmlTag tag = getXmlTag();
      if (tag != null) {
        deleteTag(tag);
        detach();
        fireUndefinedEvent();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof DomRootInvocationHandler handler)) return false;

    return myParent.equals(handler.myParent);
  }

  @Override
  public int hashCode() {
    return myParent.hashCode();
  }

  @Override
  public boolean exists() {
    return getStub() != null ||
           getXmlElement() != null;
  }

  @Override
  public @NotNull String getXmlElementNamespace() {
    return getXmlName().getNamespace(getFile(), getFile());
  }

  @Override
  protected String checkValidity() {
    final XmlTag tag = (XmlTag)getXmlElement();
    if (tag != null && !tag.isValid()) {
      return "invalid root tag";
    }

    final String s = myParent.checkValidity();
    if (s != null) {
      return "root: " + s;
    }

    return null;
  }

  @Override
  public @NotNull DomFileElementImpl<?> getParent() {
    return myParent;
  }

  @Override
  public DomElement createPathStableCopy() {
    final DomFileElement<?> stableCopy = myParent.createStableCopy();
    return getManager().createStableValue((NullableFactory<DomElement>)() -> stableCopy.isValid() ? stableCopy.getRootElement() : null);
  }

  @Override
  protected XmlTag setEmptyXmlTag() {
    final XmlTag[] result = new XmlTag[]{null};
    getManager().runChange(() -> {
      try {
        final String namespace = getXmlElementNamespace();
        final @NonNls String nsDecl = StringUtil.isEmpty(namespace) ? "" : " xmlns=\"" + namespace + "\"";
        final XmlFile xmlFile = getFile();
        final XmlTag tag = XmlElementFactory.getInstance(xmlFile.getProject()).createTagFromText("<" + getXmlElementName() + nsDecl + "/>");
        result[0] = ((XmlDocument)xmlFile.getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument())).getRootTag();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
    return result[0];
  }

  @Override
  public final @NotNull DomNameStrategy getNameStrategy() {
    final Class<?> rawType = getRawType();
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(rawType, isAttribute());
    if (strategy != null) {
      return strategy;
    }
    return DomNameStrategy.HYPHEN_STRATEGY;
  }


}
