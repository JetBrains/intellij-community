package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
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

    enum ReferenceType {
      ElementReference, AttributeReference, GroupReference, AttributeGroupReference, TypeReference
    }
    
    private ReferenceType myType;
    
    TypeOrElementOrAttributeReference(PsiElement element) {
      this(element,new TextRange(1,element.getTextLength()-1));
    }

    TypeOrElementOrAttributeReference(PsiElement element, TextRange range) {
      myElement = element;
      myRange   = range;
      
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(myElement, XmlAttribute.class);
      final XmlTag tag = attribute.getParent();
      final String localName = tag.getLocalName();
      final String attributeLocalName = attribute.getLocalName();

      if ("ref".equals(attributeLocalName) || "substitutionGroup".equals(attributeLocalName)) {
        if (localName.equals("group")) {
          myType = ReferenceType.GroupReference;
        } else if (localName.equals("attributeGroup")) {
          myType = ReferenceType.AttributeGroupReference;
        } else if ("element".equals(localName)) {
          myType = ReferenceType.ElementReference;
        } else if ("attribute".equals(localName)) {
          myType = ReferenceType.AttributeReference;
        }
      } else if ("type".equals(attributeLocalName) || 
                 "base".equals(attributeLocalName) ||
                 "memberTypes".equals(attributeLocalName)
                ) {
        myType = ReferenceType.TypeReference;
      }
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return myRange;
    }

    @Nullable
    public PsiElement resolve() {
      final XmlTag tag = PsiTreeUtil.getParentOfType(myElement, XmlTag.class);
      if (tag == null) return null;
      
      String canonicalText = getCanonicalText();
      XmlNSDescriptorImpl nsDescriptor = getDescriptor(tag);

      if (nsDescriptor != null) {
        
        switch(myType) {
          case GroupReference: return nsDescriptor.findGroup(canonicalText);
          case AttributeGroupReference: return nsDescriptor.findAttributeGroup(canonicalText);
          case ElementReference: {
            XmlElementDescriptor descriptor = nsDescriptor.getElementDescriptor(
              XmlUtil.findLocalNameByQualifiedName(canonicalText),
              tag.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(canonicalText)),
              new HashSet<XmlNSDescriptorImpl>(),
              true
            );

            return descriptor != null ? descriptor.getDeclaration(): null;
          }
          case AttributeReference: {
            final String prefixByQualifiedName = XmlUtil.findPrefixByQualifiedName(canonicalText);
            final String localNameByQualifiedName = XmlUtil.findLocalNameByQualifiedName(canonicalText);
            XmlAttributeDescriptor descriptor = nsDescriptor.getAttribute(
              localNameByQualifiedName,
              tag.getNamespaceByPrefix(prefixByQualifiedName)
            );

            if (descriptor != null) return descriptor.getDeclaration();
            
            if ("xml".equals(prefixByQualifiedName) &&
                ( "lang".equals(localNameByQualifiedName) ||
                  "base".equals(localNameByQualifiedName) ||
                  "space".equals(localNameByQualifiedName) 
                )
               ) {
              return myElement; // for compatibility
            }
            
            return null;
          }
          case TypeReference: {
            TypeDescriptor typeDescriptor = nsDescriptor.getTypeDescriptor(canonicalText,tag);
            if (typeDescriptor instanceof ComplexTypeDescriptor) {
              return ((ComplexTypeDescriptor)typeDescriptor).getDeclaration();
            } else if (typeDescriptor instanceof TypeDescriptor) {
              return myElement;
            }
          }
        }
      }

      return null;
    }

    private XmlNSDescriptorImpl getDescriptor(final XmlTag tag) {
      XmlDocument document = ((XmlFile)myElement.getContainingFile()).getDocument();
      XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
      if (nsDescriptor == null) nsDescriptor = tag.getNSDescriptor(tag.getNamespace(), true);
      
      return nsDescriptor instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)nsDescriptor:null;
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

    static class CompletionProcessor implements PsiElementProcessor<XmlTag> {
      List<String> myElements = new ArrayList<String>(1);
      String namespace;
      XmlTag tag;
      
      public boolean execute(final XmlTag element) {
        String name = element.getAttributeValue("name");
        final String prefixByNamespace = tag.getPrefixByNamespace(namespace);
        if (prefixByNamespace != null && prefixByNamespace.length() > 0) {
          name = prefixByNamespace + ":" + name;
        }
        myElements.add( name );
        return true;
      }
    }
    
    public Object[] getVariants() {
      final XmlTag tag = PsiTreeUtil.getParentOfType(myElement, XmlTag.class);
      if (tag == null) return null;
      
      String[] tagNames = null;

      switch (myType) {
        case GroupReference:
          tagNames = new String[] {"group"};
          break;
        case AttributeGroupReference:
          tagNames = new String[] {"attributeGroup"};
          break;
        case AttributeReference:
          tagNames = new String[] {"attribute"};
          break;
        case ElementReference:
          tagNames = new String[] {"element"};
          break;
        case TypeReference:
          tagNames = new String[] {"simpleType","complexType"};
          break;
      }
      
      CompletionProcessor processor = new CompletionProcessor();
      processor.tag = tag;
      HashSet<String> visitedNamespaces = new HashSet<String>(1);

      for(String namespace:tag.knownNamespaces()) {
        final XmlNSDescriptor nsDescriptor = tag.getNSDescriptor(namespace, true);

        if (nsDescriptor instanceof XmlNSDescriptorImpl) {
          processNamespace(namespace, processor, nsDescriptor, tagNames);
          visitedNamespaces.add(namespace);
        }
      }
      
      XmlDocument document = ((XmlFile)myElement.getContainingFile()).getDocument();
      final XmlTag rootTag = document.getRootTag();
      final String namespace = rootTag != null ? rootTag.getAttributeValue("targetNamespace") : "";
      
      if (!visitedNamespaces.contains(namespace)) {
        XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
        processNamespace(
          namespace,
          processor,
          nsDescriptor,
          tagNames
        );
      }
      
      return processor.myElements.toArray(new String[processor.myElements.size()]);
    }

    private void processNamespace(final String namespace,
                                  final CompletionProcessor processor,
                                  final XmlNSDescriptor nsDescriptor,
                                  final String[] tagNames) {
      processor.namespace = namespace;

      ((XmlNSDescriptorImpl)nsDescriptor).processTagsInNamespace(
        nsDescriptor.getDescriptorFile().getDocument(),
        tagNames,
        processor
      );
    }

    public boolean isSoft() {
      return false;
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
