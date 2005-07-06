package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 4, 2005
 * Time: 3:58:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class SchemaReferencesProvider implements PsiReferenceProvider {
  static class NameReference implements PsiReference {
    private PsiElement myElement;

    NameReference(PsiElement element) {
      myElement = element;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return new TextRange(1,myElement.getTextLength()-1);
    }

    @Nullable
    public PsiElement resolve() {
      return myElement.getParent().getParent();
    }

    public String getCanonicalText() {
      String text = myElement.getText();
      return text.substring(1,text.length()- 1);
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement).handleContentChange(
        myElement,
        getRangeInElement(),
        newElementName
      );
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    public Object[] getVariants() {
      return new Object[0];
    }

    public boolean isSoft() {
      return true;
    }
  }
  
  static class TypeOrElementReference implements PsiReference {
    private PsiElement myElement;

    TypeOrElementReference(PsiElement element) {
      myElement = element;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return new TextRange(1,myElement.getTextLength()-1);
    }

    @Nullable
    public PsiElement resolve() {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(myElement, XmlAttribute.class);
      final XmlTag tag = attribute.getParent();
      XmlDocument document = ((XmlFile)tag.getContainingFile()).getDocument();
      
      String canonicalText = getCanonicalText();
      final String namespaceByPrefix = tag.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(canonicalText));
      XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
      if (namespaceByPrefix != null) {
        XmlNSDescriptor namespaceDescriptor = tag.getNSDescriptor(namespaceByPrefix, true);
        if (namespaceDescriptor != null) nsDescriptor = namespaceDescriptor;
      }
      
      if (nsDescriptor instanceof XmlNSDescriptorImpl) {
        XmlNSDescriptorImpl xmlNSDescriptor = ((XmlNSDescriptorImpl)nsDescriptor);
        
        final String localName = tag.getLocalName();
        
        if ("ref".equals(attribute.getLocalName())) {
          if (localName.equals("group")) {
            return ((XmlNSDescriptorImpl)nsDescriptor).findGroup(canonicalText);
          } else if (localName.equals("attributeGroup")) {
            return ((XmlNSDescriptorImpl)nsDescriptor).findAttributeGroup(canonicalText);
          } else {
            XmlElementDescriptor descriptor = xmlNSDescriptor.getElementDescriptor(
              XmlUtil.findLocalNameByQualifiedName(canonicalText),
              namespaceByPrefix,
              new HashSet<XmlNSDescriptorImpl>(),
              true
            );
  
            return descriptor != null ? descriptor.getDeclaration(): null;
          }
        } else {
          TypeDescriptor typeDescriptor = ((XmlNSDescriptorImpl)nsDescriptor).getTypeDescriptor(canonicalText,tag);
          if (typeDescriptor instanceof ComplexTypeDescriptor) {
            return ((ComplexTypeDescriptor)typeDescriptor).getDeclaration();
          }
        }
      }

      return null;
    }

    public String getCanonicalText() {
      String text = myElement.getText();
      return text.substring(1,text.length()- 1);
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return null;
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    public Object[] getVariants() {
      return new Object[0];
    }

    public boolean isSoft() {
      return true;
    }
  }

  public PsiReference[] getReferencesByElement(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof XmlAttribute &&
        "name".equals(((XmlAttribute)parent).getName())
       ) {
      return new PsiReference[] { new NameReference(element) };
    } else {
      return new PsiReference[] { new TypeOrElementReference(element) };
    }
  }

  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
