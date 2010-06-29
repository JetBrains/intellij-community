/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class DomElementXmlDescriptor implements XmlElementDescriptor {
  private final DomChildrenDescription myChildrenDescription;
  private final DomManager myManager;
  @NotNull private final DomElement mySomeElement;

  public DomElementXmlDescriptor(@NotNull final DomElement domElement) {
    myChildrenDescription = new MyRootDomChildrenDescription(domElement);

    myManager = domElement.getManager();
    mySomeElement = domElement;
  }

  public DomElementXmlDescriptor(@NotNull final DomChildrenDescription childrenDescription, @NotNull DomElement someElement) {
    myChildrenDescription = childrenDescription;
    myManager = someElement.getManager();
    mySomeElement = someElement;
  }

  public String getQualifiedName() {
    return myChildrenDescription.getXmlElementName();
  }

  public String getDefaultName() {
    return myChildrenDescription.getXmlElementName();
  }

  public XmlElementDescriptor[] getElementsDescriptors(final XmlTag context) {
    DomElement domElement = myManager.getDomElement(context);
    if (domElement == null) return EMPTY_ARRAY;

    List<XmlElementDescriptor> xmlElementDescriptors = new ArrayList<XmlElementDescriptor>();

    for (DomCollectionChildDescription childrenDescription : domElement.getGenericInfo().getCollectionChildrenDescriptions()) {
      xmlElementDescriptors.add(new DomElementXmlDescriptor(childrenDescription, domElement));
    }

    for (DomFixedChildDescription childrenDescription : domElement.getGenericInfo().getFixedChildrenDescriptions()) {
      xmlElementDescriptors.add(new DomElementXmlDescriptor(childrenDescription, domElement));
    }

    CustomDomChildrenDescription customDescription = domElement.getGenericInfo().getCustomNameChildrenDescription();
    if (customDescription != null) {
      xmlElementDescriptors.add(new AnyXmlElementDescriptor(this, getNSDescriptor()));
    }

    return xmlElementDescriptors.toArray(new XmlElementDescriptor[xmlElementDescriptors.size()]);
  }

  @Nullable
  public XmlElementDescriptor getElementDescriptor(@NotNull final XmlTag childTag, @Nullable XmlTag contextTag) {
    DomElement domElement = myManager.getDomElement(childTag);
    if (domElement == null) {
      domElement = myManager.getDomElement(contextTag);
      if (domElement != null) {
        AbstractDomChildrenDescription description = myManager.findChildrenDescription(childTag, domElement);
        if (description instanceof DomChildrenDescription) {
          return new DomElementXmlDescriptor((DomChildrenDescription)description, domElement);
        }
      }
      return null;
    }

    final DomElement parent = domElement.getParent();
    if (parent == null) return new DomElementXmlDescriptor(domElement);

    AbstractDomChildrenDescription description = domElement.getChildDescription();
    if (description instanceof CustomDomChildrenDescription) return new AnyXmlElementDescriptor(this, getNSDescriptor());
    if (!(description instanceof DomChildrenDescription)) return null;

    return new DomElementXmlDescriptor((DomChildrenDescription)description, parent);
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(final @Nullable XmlTag context) {
    if (context == null) return XmlAttributeDescriptor.EMPTY;

    DomElement domElement = myManager.getDomElement(context);
    if (domElement == null) return XmlAttributeDescriptor.EMPTY;

    final List<? extends DomAttributeChildDescription> descriptions = domElement.getGenericInfo().getAttributeChildrenDescriptions();
    List<XmlAttributeDescriptor> descriptors = new ArrayList<XmlAttributeDescriptor>();

    for (DomAttributeChildDescription description : descriptions) {
      descriptors.add(new DomAttributeXmlDescriptor(description));
    }

    return descriptors.toArray(new XmlAttributeDescriptor[descriptors.size()]);
  }

  @Nullable
  public XmlAttributeDescriptor getAttributeDescriptor(final String attributeName, final @Nullable XmlTag context) {
    DomElement domElement = myManager.getDomElement(context);
    if (domElement == null) return null;

    for (DomAttributeChildDescription description : domElement.getGenericInfo().getAttributeChildrenDescriptions()) {
      if (attributeName.equals(DomAttributeXmlDescriptor.getQualifiedAttributeName(context, description.getXmlName()))) {
        return new DomAttributeXmlDescriptor(description);
      }
    }
    return null;
  }

  @Nullable
  public XmlAttributeDescriptor getAttributeDescriptor(final XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
  }

  public XmlNSDescriptor getNSDescriptor() {
    return new XmlNSDescriptor() {
      @Nullable
      public XmlElementDescriptor getElementDescriptor(@NotNull final XmlTag tag) {
        throw new UnsupportedOperationException("Method getElementDescriptor not implemented in " + getClass());
      }

      @NotNull
      public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument document) {
        throw new UnsupportedOperationException("Method getRootElementsDescriptors not implemented in " + getClass());
      }

      @Nullable
      public XmlFile getDescriptorFile() {
        return null;
      }

      public boolean isHierarhyEnabled() {
        throw new UnsupportedOperationException("Method isHierarhyEnabled not implemented in " + getClass());
      }

      @Nullable
      public PsiElement getDeclaration() {
        throw new UnsupportedOperationException("Method getDeclaration not implemented in " + getClass());
      }

      @NonNls
      public String getName(final PsiElement context) {
        throw new UnsupportedOperationException("Method getName not implemented in " + getClass());
      }

      @NonNls
      public String getName() {
        throw new UnsupportedOperationException("Method getName not implemented in " + getClass());
      }

      public void init(final PsiElement element) {
        throw new UnsupportedOperationException("Method init not implemented in " + getClass());
      }

      public Object[] getDependences() {
        throw new UnsupportedOperationException("Method getDependences not implemented in " + getClass());
      }
    };
  }

  public int getContentType() {
    throw new UnsupportedOperationException("Method getContentType not implemented in " + getClass());
  }

  @Nullable
  public PsiElement getDeclaration() {
    final DomElement declaration = myChildrenDescription.getUserData(DomExtension.KEY_DECLARATION);

    if (declaration != null) return declaration.getXmlElement();

    return PomService.convertToPsi(myManager.getProject(), myChildrenDescription);
  }

  @NonNls
  public String getName(final PsiElement context) {
    final String name = getDefaultName();
    if (context instanceof XmlTag) {
      XmlTag tag = (XmlTag)context;
      final PsiFile file = tag.getContainingFile();
      DomElement element = myManager.getDomElement(tag);
      if (element == null && tag.getParentTag() != null) {
        element = myManager.getDomElement(tag.getParentTag());
      }
      if (element != null && file instanceof XmlFile && !(myChildrenDescription instanceof MyRootDomChildrenDescription)) {
        final String namespace = DomService.getInstance().getEvaluatedXmlName(element).evaluateChildName(myChildrenDescription.getXmlName()).getNamespace(tag, (XmlFile)file);
        if (!tag.getNamespaceByPrefix("").equals(namespace)) {
          final String s = tag.getPrefixByNamespace(namespace);
          if (StringUtil.isNotEmpty(s)) {
            return s + ":" + name;
          }
        }
      }
    }

    return name;
  }

  @NonNls
  public String getName() {
    return getDefaultName();
  }

  public void init(final PsiElement element) {
    throw new UnsupportedOperationException("Method init not implemented in " + getClass());
  }

  public Object[] getDependences() {
    throw new UnsupportedOperationException("Method getDependences not implemented in " + getClass());
  }

  private static class MyRootDomChildrenDescription implements DomChildrenDescription {
    private final DomElement myDomElement;

    public MyRootDomChildrenDescription(final DomElement domElement) {
      myDomElement = domElement;
    }

    public String getName() {
      return getXmlElementName();
    }

    public boolean isValid() {
      return true;
    }

    public void navigate(boolean requestFocus) {
    }

    public boolean canNavigate() {
      return false;
    }

    public boolean canNavigateToSource() {
      return false;
    }

    @NotNull
    public XmlName getXmlName() {
      throw new UnsupportedOperationException("Method getXmlName not implemented in " + getClass());
    }

    @NotNull
    public String getXmlElementName() {
      return myDomElement.getXmlElementName();
    }

    @NotNull
      public String getCommonPresentableName(@NotNull final DomNameStrategy strategy) {
      throw new UnsupportedOperationException("Method getCommonPresentableName not implemented in " + getClass());
    }

    @NotNull
      public String getCommonPresentableName(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getCommonPresentableName not implemented in " + getClass());
    }

    @NotNull
      public List<? extends DomElement> getValues(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getValues not implemented in " + getClass());
    }

    @NotNull
      public List<? extends DomElement> getStableValues(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getStableValues not implemented in " + getClass());
    }

    @NotNull
      public Type getType() {
      throw new UnsupportedOperationException("Method getType not implemented in " + getClass());
    }

    @NotNull
      public DomNameStrategy getDomNameStrategy(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getDomNameStrategy not implemented in " + getClass());
    }

    public <T> T getUserData(final Key<T> key) {
      return null;
    }

    @Nullable
      public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
          throw new UnsupportedOperationException("Method getAnnotation not implemented in " + getClass());
        }
  }

}
