/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author peter
 */
public class DomRootInvocationHandler extends DomInvocationHandler<AbstractDomChildDescriptionImpl, ElementStub> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomRootInvocationHandler");
  private final DomFileElementImpl<?> myParent;

  public DomRootInvocationHandler(final Class aClass,
                                  final RootDomParentStrategy strategy,
                                  @NotNull final DomFileElementImpl fileElement,
                                  @NotNull final EvaluatedXmlName tagName,
                                  @Nullable ElementStub stub
  ) {
    super(aClass, strategy, tagName, new AbstractDomChildDescriptionImpl(aClass) {
      @Override
      @NotNull
      public List<? extends DomElement> getValues(@NotNull final DomElement parent) {
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

  public boolean equals(final Object obj) {
    if (!(obj instanceof DomRootInvocationHandler)) return false;

    final DomRootInvocationHandler handler = (DomRootInvocationHandler)obj;
    return myParent.equals(handler.myParent);
  }

  public int hashCode() {
    return myParent.hashCode();
  }

  @Override
  public boolean exists() {
    return getStub() != null ||
           getXmlElement() != null;
  }

  @Override
  @NotNull
  public String getXmlElementNamespace() {
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
  @NotNull
  public DomFileElementImpl getParent() {
    return myParent;
  }

  @Override
  public DomElement createPathStableCopy() {
    final DomFileElement stableCopy = myParent.createStableCopy();
    return getManager().createStableValue(new NullableFactory<DomElement>() {
      @Override
      public DomElement create() {
        return stableCopy.isValid() ? stableCopy.getRootElement() : null;
      }
    });
  }

  @Override
  protected XmlTag setEmptyXmlTag() {
    final XmlTag[] result = new XmlTag[]{null};
    getManager().runChange(new Runnable() {
      @Override
      public void run() {
        try {
          final String namespace = getXmlElementNamespace();
          @NonNls final String nsDecl = StringUtil.isEmpty(namespace) ? "" : " xmlns=\"" + namespace + "\"";
          final XmlFile xmlFile = getFile();
          final XmlTag tag = XmlElementFactory.getInstance(xmlFile.getProject()).createTagFromText("<" + getXmlElementName() + nsDecl + "/>");
          result[0] = ((XmlDocument)xmlFile.getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument())).getRootTag();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    return result[0];
  }

  @Override
  @NotNull
  public final DomNameStrategy getNameStrategy() {
    final Class<?> rawType = getRawType();
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(rawType, isAttribute());
    if (strategy != null) {
      return strategy;
    }
    return DomNameStrategy.HYPHEN_STRATEGY;
  }


}
