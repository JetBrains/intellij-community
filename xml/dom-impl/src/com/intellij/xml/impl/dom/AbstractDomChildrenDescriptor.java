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
package com.intellij.xml.impl.dom;

import com.intellij.pom.PomTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.impl.AttributeChildDescriptionImpl;
import com.intellij.util.xml.reflect.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class AbstractDomChildrenDescriptor implements XmlElementDescriptor {
  protected final DomManager myManager;

  protected AbstractDomChildrenDescriptor(DomManager manager) {
    myManager = manager;
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(final XmlTag context) {
    final DomElement domElement = myManager.getDomElement(context);
    if (domElement == null) return EMPTY_ARRAY;

    List<XmlElementDescriptor> xmlElementDescriptors = new ArrayList<>();

    for (DomCollectionChildDescription childrenDescription : domElement.getGenericInfo().getCollectionChildrenDescriptions()) {
      xmlElementDescriptors.add(new DomElementXmlDescriptor(childrenDescription, myManager));
    }

    for (DomFixedChildDescription childrenDescription : domElement.getGenericInfo().getFixedChildrenDescriptions()) {
      xmlElementDescriptors.add(new DomElementXmlDescriptor(childrenDescription, myManager));
    }

    final List<? extends CustomDomChildrenDescription> customs = domElement.getGenericInfo().getCustomNameChildrenDescription();

    for (final CustomDomChildrenDescription custom : customs) {
      final CustomDomChildrenDescription.TagNameDescriptor tagNameDescriptor = custom.getTagNameDescriptor();
      if (tagNameDescriptor == null) continue;
      final XmlTag xmlTag = domElement.getXmlTag();
      for (final EvaluatedXmlName name : tagNameDescriptor.getCompletionVariants(domElement)) {
        AbstractDomChildrenDescriptor descriptor = new AbstractDomChildrenDescriptor(myManager) {
          @Override
          public String getDefaultName() {
            final String ns = xmlTag != null ? name.getNamespace(xmlTag, (XmlFile)xmlTag.getContainingFile()) : null;
            if (ns != null) {
              final String prefix = xmlTag.getPrefixByNamespace(ns);
              if (prefix != null) {
                return prefix + ":" + name.getXmlName().getLocalName();
              }
            }
            return name.getXmlName().getLocalName();
          }

          @Override
          @Nullable
          public PsiElement getDeclaration() {
            final PomTarget target = tagNameDescriptor.findDeclaration(domElement, name);
            return target == null ? null : PomService.convertToPsi(context.getProject(), target);
          }
        };
        xmlElementDescriptors.add(descriptor);
      }

      xmlElementDescriptors.add(new AnyXmlElementDescriptor(this, getNSDescriptor()));
    }

    return xmlElementDescriptors.toArray(new XmlElementDescriptor[xmlElementDescriptors.size()]);
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  @Nullable
  public XmlElementDescriptor getElementDescriptor(@NotNull final XmlTag childTag, @Nullable XmlTag contextTag) {
    DomElement domElement = myManager.getDomElement(childTag);
    if (domElement == null) {
      domElement = myManager.getDomElement(contextTag);
      if (domElement != null) {
        AbstractDomChildrenDescription description = myManager.findChildrenDescription(childTag, domElement);
        if (description instanceof DomChildrenDescription) {
          return new DomElementXmlDescriptor((DomChildrenDescription)description, myManager);
        }
      }
      return null;
    }

    final DomElement parent = domElement.getParent();
    if (parent == null) return new DomElementXmlDescriptor(domElement);

    final AbstractDomChildrenDescription description = domElement.getChildDescription();
    if (description instanceof CustomDomChildrenDescription) {
      final DomElement finalDomElement = domElement;
      return new AbstractDomChildrenDescriptor(myManager) {
        @Override
        public String getDefaultName() {
          return finalDomElement.getXmlElementName();
        }

        @Override
        @Nullable
        public PsiElement getDeclaration() {
          final PomTarget target = ((CustomDomChildrenDescription)description).getTagNameDescriptor().findDeclaration(finalDomElement);
          if (target == description) {
            return childTag;
          }
          return target == null ? null : PomService.convertToPsi(childTag.getProject(), target);
        }

      };
    }
    if (!(description instanceof DomChildrenDescription)) return null;

    return new DomElementXmlDescriptor((DomChildrenDescription)description, myManager);
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(final @Nullable XmlTag context) {
    if (context == null) return XmlAttributeDescriptor.EMPTY;

    DomElement domElement = myManager.getDomElement(context);
    if (domElement == null) return XmlAttributeDescriptor.EMPTY;

    final List<? extends DomAttributeChildDescription> descriptions = domElement.getGenericInfo().getAttributeChildrenDescriptions();
    List<XmlAttributeDescriptor> descriptors = new ArrayList<>();

    for (DomAttributeChildDescription description : descriptions) {
      descriptors.add(new DomAttributeXmlDescriptor(description, myManager.getProject()));
    }
    List<? extends CustomDomChildrenDescription> customs = domElement.getGenericInfo().getCustomNameChildrenDescription();
    for (CustomDomChildrenDescription custom : customs) {
      CustomDomChildrenDescription.AttributeDescriptor descriptor = custom.getCustomAttributeDescriptor();
      if (descriptor != null) {
        for (EvaluatedXmlName variant : descriptor.getCompletionVariants(domElement)) {
          AttributeChildDescriptionImpl childDescription = new AttributeChildDescriptionImpl(variant.getXmlName(), String.class);
          descriptors.add(new DomAttributeXmlDescriptor(childDescription, myManager.getProject()));
        }
      }
    }
    return descriptors.toArray(new XmlAttributeDescriptor[descriptors.size()]);
  }

  @Override
  @Nullable
  public XmlAttributeDescriptor getAttributeDescriptor(final String attributeName, final @Nullable XmlTag context) {
    DomElement domElement = myManager.getDomElement(context);
    if (domElement == null) return null;

    for (DomAttributeChildDescription description : domElement.getGenericInfo().getAttributeChildrenDescriptions()) {
      if (attributeName.equals(DomAttributeXmlDescriptor.getQualifiedAttributeName(context, description.getXmlName()))) {
        return new DomAttributeXmlDescriptor(description, myManager.getProject());
      }
    }
    return null;
  }

  @Override
  @Nullable
  public XmlAttributeDescriptor getAttributeDescriptor(final XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return new XmlNSDescriptor() {
      @Override
      @Nullable
      public XmlElementDescriptor getElementDescriptor(@NotNull final XmlTag tag) {
        throw new UnsupportedOperationException("Method getElementDescriptor not implemented in " + getClass());
      }

      @Override
      @NotNull
      public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument document) {
        throw new UnsupportedOperationException("Method getRootElementsDescriptors not implemented in " + getClass());
      }

      @Override
      @Nullable
      public XmlFile getDescriptorFile() {
        return null;
      }

      @Override
      @Nullable
      public PsiElement getDeclaration() {
        throw new UnsupportedOperationException("Method getDeclaration not implemented in " + getClass());
      }

      @Override
      @NonNls
      public String getName(final PsiElement context) {
        throw new UnsupportedOperationException("Method getName not implemented in " + getClass());
      }

      @Override
      @NonNls
      public String getName() {
        throw new UnsupportedOperationException("Method getName not implemented in " + getClass());
      }

      @Override
      public void init(final PsiElement element) {
        throw new UnsupportedOperationException("Method init not implemented in " + getClass());
      }

      @Override
      public Object[] getDependences() {
        throw new UnsupportedOperationException("Method getDependences not implemented in " + getClass());
      }
    };
  }

  @Override
  public int getContentType() {
    return CONTENT_TYPE_UNKNOWN;
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public void init(final PsiElement element) {
    throw new UnsupportedOperationException("Method init not implemented in " + getClass());
  }

  @Override
  public Object[] getDependences() {
    throw new UnsupportedOperationException("Method getDependences not implemented in " + getClass());
  }

  @Override
  @NonNls
  public String getName() {
    return getDefaultName();
  }

  @Override
  public String getQualifiedName() {
    return getDefaultName();
  }

  @Override
  public String getName(PsiElement context) {
    return getDefaultName();
  }
}
