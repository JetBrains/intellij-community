package com.intellij.xml.impl.dom;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
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

  public DomElementXmlDescriptor(@NotNull final DomElement domElement) {
    myChildrenDescription = new MyRootDomChildrenDescription(domElement);

    myManager = domElement.getManager();

  }

  public DomElementXmlDescriptor(@NotNull final DomChildrenDescription childrenDescription, @NotNull DomManager manager) {
    myChildrenDescription = childrenDescription;
    myManager = manager;
  }

  public String getQualifiedName() {
    return myChildrenDescription.getXmlName().getLocalName();
  }

  public String getDefaultName() {
    return myChildrenDescription.getXmlName().getLocalName();
  }

  public XmlElementDescriptor[] getElementsDescriptors(final XmlTag context) {
    DomElement domElement = myManager.getDomElement(context);
    if (domElement == null) return EMPTY_ARRAY;

    List<XmlElementDescriptor> xmlElementDescriptors = new ArrayList<XmlElementDescriptor>();

    for (DomCollectionChildDescription childrenDescription : domElement.getGenericInfo().getCollectionChildrenDescriptions()) {
      xmlElementDescriptors.add(new DomElementXmlDescriptor(childrenDescription, myManager));
    }

    for (DomFixedChildDescription childrenDescription : domElement.getGenericInfo().getFixedChildrenDescriptions()) {
      xmlElementDescriptors.add(new DomElementXmlDescriptor(childrenDescription, myManager));
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
          return new DomElementXmlDescriptor((DomChildrenDescription)description, myManager);
        }
      }

      return null;
    }

    final DomElement parent = domElement.getParent();
    if (parent == null) return new DomElementXmlDescriptor(domElement);

    AbstractDomChildrenDescription description = domElement.getChildDescription();
    if (!(description instanceof DomChildrenDescription)) return null;

    return new DomElementXmlDescriptor((DomChildrenDescription)description, myManager);
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

    final DomAttributeChildDescription childDescription = domElement.getGenericInfo().getAttributeChildDescription(attributeName);
    if (childDescription != null) return new DomAttributeXmlDescriptor(childDescription);
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

    return new FakePsiElement() {
      public PsiFile getContainingFile() {
        return null;
      }

      public PsiElement getParent() {
        return null;
      }
    };
  }

  @NonNls
  public String getName(final PsiElement context) {
    return getDefaultName();
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

    @NotNull
    public XmlName getXmlName() {
      throw new UnsupportedOperationException("Method getXmlName not implemented in " + getClass());
    }

    @NotNull
    public String getXmlElementName() {
      throw new UnsupportedOperationException("Method getXmlElementName not implemented in " + getClass());
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
