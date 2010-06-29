package com.intellij.xml.impl.dom;

import com.intellij.pom.PomNamedTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.reflect.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
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

    final CustomDomChildrenDescription customDescription = domElement.getGenericInfo().getCustomNameChildrenDescription();
    if (customDescription != null) {
      for (final EvaluatedXmlName name : customDescription.getTagNameDescriptor().getCompletionVariants(domElement)) {
        xmlElementDescriptors.add(new AbstractDomChildrenDescriptor(myManager) {
          @Override
          public String getDefaultName() {
            return name.getXmlName().getLocalName();
          }

          @Override
          @Nullable
          public PsiElement getDeclaration() {
            final PomNamedTarget target = customDescription.getTagNameDescriptor().findDeclaration(name);
            return target == null ? null : PomService.convertToPsi(context.getProject(), target);
          }

        });
      }

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
          final PomNamedTarget target = ((CustomDomChildrenDescription)description).getTagNameDescriptor().findDeclaration(finalDomElement);
          return target == null ? null : PomService.convertToPsi(childTag.getProject(), target);
        }

      };
    }
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

  public void init(final PsiElement element) {
    throw new UnsupportedOperationException("Method init not implemented in " + getClass());
  }

  public Object[] getDependences() {
    throw new UnsupportedOperationException("Method getDependences not implemented in " + getClass());
  }

  @NonNls
  public String getName() {
    return getDefaultName();
  }

  public String getQualifiedName() {
    return getDefaultName();
  }

  @Override
  public String getName(PsiElement context) {
    return getDefaultName();
  }
}
