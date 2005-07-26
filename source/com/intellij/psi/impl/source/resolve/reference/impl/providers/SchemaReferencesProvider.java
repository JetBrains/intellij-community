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
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.schema.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;

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
        newElementName.substring(newElementName.indexOf(':') + 1)
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

  static class TypeOrElementOrAttributeReference implements PsiReference {
    private PsiElement myElement;
    private TextRange myRange;

    TypeOrElementOrAttributeReference(PsiElement element) {
      this(element,new TextRange(1,element.getTextLength()-1));
    }

    TypeOrElementOrAttributeReference(PsiElement element, TextRange range) {
      myElement = element;
      myRange   = range;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return myRange;
    }

    @Nullable
    public PsiElement resolve() {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(myElement, XmlAttribute.class);
      final XmlTag tag = attribute.getParent();
      XmlDocument document = ((XmlFile)tag.getContainingFile()).getDocument();

      String canonicalText = getCanonicalText();
      XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
      if (nsDescriptor == null) nsDescriptor = tag.getNSDescriptor(tag.getNamespace(), true);

      if (nsDescriptor instanceof XmlNSDescriptorImpl) {
        XmlNSDescriptorImpl xmlNSDescriptor = ((XmlNSDescriptorImpl)nsDescriptor);

        final String localName = tag.getLocalName();
        final String attributeLocalName = attribute.getLocalName();

        if ("ref".equals(attributeLocalName) || "substitutionGroup".equals(attributeLocalName)) {
          if (localName.equals("group")) {
            return xmlNSDescriptor.findGroup(canonicalText);
          } else if (localName.equals("attributeGroup")) {
            return xmlNSDescriptor.findAttributeGroup(canonicalText);
          } else if ("element".equals(localName)) {
            XmlElementDescriptor descriptor = xmlNSDescriptor.getElementDescriptor(
              XmlUtil.findLocalNameByQualifiedName(canonicalText),
              tag.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(canonicalText)),
              new HashSet<XmlNSDescriptorImpl>(),
              true
            );

            return descriptor != null ? descriptor.getDeclaration(): null;
          } else if ("attribute".equals(localName)) {
            XmlAttributeDescriptor descriptor = xmlNSDescriptor.getAttribute(
              XmlUtil.findLocalNameByQualifiedName(canonicalText),
              tag.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(canonicalText))
            );

            return descriptor != null ? descriptor.getDeclaration(): null;
          }
        } else if ("type".equals(attributeLocalName) || 
                   "base".equals(attributeLocalName) ||
                   "memberTypes".equals(attributeLocalName)
                  ) {
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
      return text.substring(myRange.getStartOffset(),myRange.getEndOffset());
    }

    public PsiElement handleElementRename(String _newElementName) throws IncorrectOperationException {
      final String canonicalText = getCanonicalText();
      final String newElementName = canonicalText.substring(0,canonicalText.indexOf(':') + 1) + _newElementName;

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

  public PsiReference[] getReferencesByElement(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof XmlAttribute &&
        "name".equals(((XmlAttribute)parent).getName())
       ) {
      return new PsiReference[] { new NameReference(element) };
    } else if (parent instanceof XmlAttribute &&
               "memberTypes".equals(((XmlAttribute)parent).getName())
              ) {
      final List<PsiReference> result = new ArrayList<PsiReference>(1);
      final String text = element.getText();
      int lastIndex = 1;
      int index = text.indexOf(' ');

      while(index != -1) {
        result.add( new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, index) ) );
        lastIndex = index + 1;
        index = text.indexOf(' ',lastIndex);
      }

      result.add( new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, text.length() - 1) ) );
      
      return result.toArray(new PsiReference[result.size()]);
    } else {
      return new PsiReference[] { new TypeOrElementOrAttributeReference(element) };
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
