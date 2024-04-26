// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
          public @Nullable PsiElement getDeclaration() {
            final PomTarget target = tagNameDescriptor.findDeclaration(domElement, name);
            return target == null ? null : PomService.convertToPsi(context.getProject(), target);
          }
        };
        xmlElementDescriptors.add(descriptor);
      }

      xmlElementDescriptors.add(new AnyXmlElementDescriptor(this, getNSDescriptor()));
    }

    return xmlElementDescriptors.toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  public @Nullable XmlElementDescriptor getElementDescriptor(final @NotNull XmlTag childTag, @Nullable XmlTag contextTag) {
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
        public @Nullable PsiElement getDeclaration() {
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

    final List<? extends DomAttributeChildDescription<?>> descriptions = domElement.getGenericInfo().getAttributeChildrenDescriptions();
    List<XmlAttributeDescriptor> descriptors = new ArrayList<>();

    for (DomAttributeChildDescription<?> description : descriptions) {
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
    return descriptors.toArray(XmlAttributeDescriptor.EMPTY);
  }

  @Override
  public @Nullable XmlAttributeDescriptor getAttributeDescriptor(final String attributeName, final @Nullable XmlTag context) {
    DomElement domElement = myManager.getDomElement(context);
    if (domElement == null) return null;

    for (DomAttributeChildDescription<?> description : domElement.getGenericInfo().getAttributeChildrenDescriptions()) {
      if (attributeName.equals(DomAttributeXmlDescriptor.getQualifiedAttributeName(context, description.getXmlName()))) {
        return new DomAttributeXmlDescriptor(description, myManager.getProject());
      }
    }
    return null;
  }

  @Override
  public @Nullable XmlAttributeDescriptor getAttributeDescriptor(final XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return new XmlNSDescriptor() {
      @Override
      public @Nullable XmlElementDescriptor getElementDescriptor(final @NotNull XmlTag tag) {
        throw new UnsupportedOperationException("Method getElementDescriptor not implemented in " + getClass());
      }

      @Override
      public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(final @Nullable XmlDocument document) {
        throw new UnsupportedOperationException("Method getRootElementsDescriptors not implemented in " + getClass());
      }

      @Override
      public @Nullable XmlFile getDescriptorFile() {
        return null;
      }

      @Override
      public @Nullable PsiElement getDeclaration() {
        throw new UnsupportedOperationException("Method getDeclaration not implemented in " + getClass());
      }

      @Override
      public @NonNls String getName(final PsiElement context) {
        throw new UnsupportedOperationException("Method getName not implemented in " + getClass());
      }

      @Override
      public @NonNls String getName() {
        throw new UnsupportedOperationException("Method getName not implemented in " + getClass());
      }

      @Override
      public void init(final PsiElement element) {
        throw new UnsupportedOperationException("Method init not implemented in " + getClass());
      }

      @Override
      public Object @NotNull [] getDependencies() {
        throw new UnsupportedOperationException("Method getDependencies not implemented in " + getClass());
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
  public Object @NotNull [] getDependencies() {
    throw new UnsupportedOperationException("Method getDependencies not implemented in " + getClass());
  }

  @Override
  public @NonNls String getName() {
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
