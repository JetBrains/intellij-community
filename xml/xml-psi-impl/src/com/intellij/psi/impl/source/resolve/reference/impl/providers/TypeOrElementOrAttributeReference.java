// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.impl.schema.XsdNsDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TypeOrElementOrAttributeReference implements PsiReference {

  public enum ReferenceType {
    ElementReference, AttributeReference, GroupReference, AttributeGroupReference, TypeReference
  }

  @NonNls private static final String TARGET_NAMESPACE = "targetNamespace";

  private final PsiElement myElement;
  private TextRange myRange;
  private String nsPrefix;

  @Nullable
  public ReferenceType getType() {
    return myType;
  }

  public void setNamespacePrefix(String prefix) {
    this.nsPrefix = prefix;
  }

  @Nullable private final ReferenceType myType;

  protected TypeOrElementOrAttributeReference(PsiElement element, TextRange range, @Nullable ReferenceType type) {
    myElement = element;
    myRange   = range;

    assert myRange.getLength() >= 0;

    myType = type;
  }

  TypeOrElementOrAttributeReference(PsiElement element, TextRange range) {
    this(element, range, determineReferenceType(element));
  }

  @Nullable
  private static ReferenceType determineReferenceType(PsiElement element) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
    if (attribute == null) {
      return null;
    }
    final XmlTag tag = attribute.getParent();
    final String localName = tag.getLocalName();
    final String attributeLocalName = attribute.getLocalName();

    if (SchemaReferencesProvider.REF_ATTR_NAME.equals(attributeLocalName) || SchemaReferencesProvider.SUBSTITUTION_GROUP_ATTR_NAME.equals(attributeLocalName)) {
      if (localName.equals(SchemaReferencesProvider.GROUP_TAG_NAME)) {
        return ReferenceType.GroupReference;
      } else if (localName.equals(SchemaReferencesProvider.ATTRIBUTE_GROUP_TAG_NAME)) {
        return ReferenceType.AttributeGroupReference;
      } else if (SchemaReferencesProvider.ELEMENT_TAG_NAME.equals(localName)) {
        return ReferenceType.ElementReference;
      } else if (SchemaReferencesProvider.ATTRIBUTE_TAG_NAME.equals(localName)) {
        return ReferenceType.AttributeReference;
      }
    } else if (SchemaReferencesProvider.TYPE_ATTR_NAME.equals(attributeLocalName) ||
               SchemaReferencesProvider.BASE_ATTR_NAME.equals(attributeLocalName) ||
               SchemaReferencesProvider.MEMBER_TYPES_ATTR_NAME.equals(attributeLocalName) ||
               SchemaReferencesProvider.ITEM_TYPE_ATTR_NAME.equals(attributeLocalName)
              ) {
      return ReferenceType.TypeReference;
    }
    return null;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return myRange;
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    return ResolveCache.getInstance(getElement().getProject())
      .resolveWithCaching(this, (ref, incompleteCode) -> ref.resolveInner(), false, false);
  }

  private PsiElement resolveInner() {
    final XmlTag tag = PsiTreeUtil.getContextOfType(myElement, XmlTag.class, false);
    if (tag == null) return null;

    String canonicalText = getCanonicalText();
    boolean[] redefined = new boolean[1];
    XsdNsDescriptor nsDescriptor = getDescriptor(tag, canonicalText, redefined);

    if (myType != null && nsDescriptor != null) {

      switch(myType) {
        case GroupReference: return nsDescriptor.findGroup(canonicalText);
        case AttributeGroupReference: return nsDescriptor.findAttributeGroup(canonicalText);
        case ElementReference: {
          XmlElementDescriptor descriptor = nsDescriptor.getElementDescriptor(
            XmlUtil.findLocalNameByQualifiedName(canonicalText), getNamespace(tag, canonicalText),
            new HashSet<>(),
            true
          );

          return descriptor != null ? descriptor.getDeclaration(): null;
        }
        case AttributeReference: {
          //final String prefixByQualifiedName = XmlUtil.findPrefixByQualifiedName(canonicalText);
          final String localNameByQualifiedName = XmlUtil.findLocalNameByQualifiedName(canonicalText);
          XmlAttributeDescriptor descriptor = nsDescriptor.getAttribute(
            localNameByQualifiedName,
            getNamespace(tag, canonicalText),
            tag
          );

          if (descriptor != null) return descriptor.getDeclaration();

          return null;
        }
        case TypeReference: {
          TypeDescriptor typeDescriptor = redefined[0] ? nsDescriptor.findTypeDescriptor(XmlUtil.findLocalNameByQualifiedName(canonicalText), "") :
                                                         nsDescriptor.getTypeDescriptor(canonicalText,tag);
          if (typeDescriptor instanceof ComplexTypeDescriptor) {
            return typeDescriptor.getDeclaration();
          } else if (typeDescriptor != null) {
            return myElement;
          }
        }
      }
    }

    return null;
  }

  XsdNsDescriptor getDescriptor(final XmlTag tag, String text, boolean[] redefined) {
    if (myType != ReferenceType.ElementReference &&
        myType != ReferenceType.AttributeReference) {
      final PsiElement parentElement = myElement.getContext();
      final PsiElement grandParentElement = parentElement != null ? parentElement.getParent() : null;
      boolean doRedefineCheck = false;

      if (parentElement instanceof XmlAttribute &&
          grandParentElement instanceof XmlTag
         ) {
        final String attrName = ((XmlAttribute)parentElement).getName();
        final String tagLocalName = ((XmlTag)grandParentElement).getLocalName();

        doRedefineCheck = (SchemaReferencesProvider.REF_ATTR_NAME.equals(attrName) &&
          ( SchemaReferencesProvider.GROUP_TAG_NAME.equals(tagLocalName) ||
            SchemaReferencesProvider.ATTRIBUTE_GROUP_TAG_NAME.equals(tagLocalName)
          )
         ) ||
         ( SchemaReferencesProvider.BASE_ATTR_NAME.equals(attrName) ||
           SchemaReferencesProvider.MEMBER_TYPES_ATTR_NAME.equals(attrName)
         );
      }

      if (doRedefineCheck) {
        XmlNSDescriptorImpl redefinedDescriptor = SchemaReferencesProvider.findRedefinedDescriptor(tag, text);
        if (redefinedDescriptor != null) {
          redefined[0] = true;
          return redefinedDescriptor;
        }
      }
    }

    final String namespace = getNamespace(tag, text);
    XmlNSDescriptor nsDescriptor = tag.getNSDescriptor(namespace,true);

    final PsiFile file = tag.getContainingFile();
    if (!(file instanceof XmlFile)) return null;

    final XmlDocument document = ((XmlFile)file).getDocument();

    if (nsDescriptor == null) { // import
      nsDescriptor = (XmlNSDescriptor)document.getMetaData();
    }

    if (nsDescriptor == null) {
      final XmlNSDescriptor[] descrs = new XmlNSDescriptor[1];

      URLReference.processWsdlSchemas(
        document.getRootTag(),
        xmlTag -> {
          if (namespace.equals(xmlTag.getAttributeValue(TARGET_NAMESPACE))) {
            descrs[0] = (XmlNSDescriptor)xmlTag.getMetaData();
            return false;
          }
          return true;
        }
      );

      if (descrs[0] instanceof XmlNSDescriptorImpl) return (XmlNSDescriptorImpl)descrs[0];
    }

    return nsDescriptor instanceof XsdNsDescriptor ? (XsdNsDescriptor)nsDescriptor:null;
  }

  private static String getNamespace(final XmlTag tag, final String text) {
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(text);
    final String namespaceByPrefix = tag.getNamespaceByPrefix(namespacePrefix);
    if (!namespaceByPrefix.isEmpty()) return namespaceByPrefix;
    final XmlTag rootTag = ((XmlFile)tag.getContainingFile()).getRootTag();

    if (rootTag != null &&
        "schema".equals(rootTag.getLocalName()) &&
        XmlUtil.ourSchemaUrisList.contains(rootTag.getNamespace())) {
      final String targetNS = rootTag.getAttributeValue(TARGET_NAMESPACE);

      if (targetNS != null) {
        final String targetNsPrefix = rootTag.getPrefixByNamespace(targetNS);

        if (namespacePrefix.equals(targetNsPrefix) ||
            (namespaceByPrefix.isEmpty() && targetNsPrefix == null)) {
          return targetNS;
        }
      }
    }
    return namespaceByPrefix;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    final String text = myElement.getText();
    String name = myRange.getEndOffset() <= text.length() ? myRange.substring(text) : "";
    if (!name.isEmpty() && nsPrefix != null && !nsPrefix.isEmpty()) {
      name = nsPrefix + ":" + name;
    }
    return name;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final String canonicalText = getCanonicalText();

    final PsiElement element = ElementManipulators.handleContentChange(myElement, getRangeInElement(), newElementName);
    myRange = new TextRange(myRange.getStartOffset(),myRange.getEndOffset() - (canonicalText.length() - newElementName.length()));
    return element;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myElement.getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  public Object @NotNull [] getVariants() {
    final XmlTag tag = PsiTreeUtil.getContextOfType(myElement, XmlTag.class, true);
    if (tag == null || myType == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;

    return getVariants(tag, myType, nsPrefix);
  }

  public static Object[] getVariants(XmlTag tag, ReferenceType type, String prefix) {
    String[] tagNames = null;

    switch (type) {
      case GroupReference:
        tagNames = new String[] {SchemaReferencesProvider.GROUP_TAG_NAME};
        break;
      case AttributeGroupReference:
        tagNames = new String[] {SchemaReferencesProvider.ATTRIBUTE_GROUP_TAG_NAME};
        break;
      case AttributeReference:
        tagNames = new String[] {SchemaReferencesProvider.ATTRIBUTE_TAG_NAME};
        break;
      case ElementReference:
        tagNames = new String[] {SchemaReferencesProvider.ELEMENT_TAG_NAME};
        break;
      case TypeReference:
        tagNames = new String[] {SchemaReferencesProvider.SIMPLE_TYPE_TAG_NAME, SchemaReferencesProvider.COMPLEX_TYPE_TAG_NAME};
        break;
    }

    final XmlDocument document = ((XmlFile)tag.getContainingFile()).getDocument();
    if (document == null) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    final XmlTag rootTag = document.getRootTag();
    String ourNamespace = rootTag != null ? rootTag.getAttributeValue(TARGET_NAMESPACE) : "";
    if (ourNamespace == null) ourNamespace = "";

    CompletionProcessor processor = new CompletionProcessor(tag, prefix);
    for(String namespace: tag.knownNamespaces()) {
      if (ourNamespace.equals(namespace)) continue;
      final XmlNSDescriptor nsDescriptor = tag.getNSDescriptor(namespace, true);

      if (nsDescriptor instanceof XsdNsDescriptor) {
        processNamespace(namespace, processor, (XsdNsDescriptor)nsDescriptor, tagNames);
      }
    }

    XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
    if (nsDescriptor instanceof XmlNSDescriptorImpl) {
      processNamespace(
        ourNamespace,
        processor,
        (XmlNSDescriptorImpl)nsDescriptor,
        tagNames
      );
    }

    return ArrayUtilRt.toStringArray(processor.myElements);
  }

  private static void processNamespace(final String namespace,
                                final CompletionProcessor processor,
                                final XsdNsDescriptor nsDescriptor,
                                final String[] tagNames) {
    processor.namespace = namespace;

    nsDescriptor.processTagsInNamespace(tagNames, processor);
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  static final ResolveCache.Resolver RESOLVER = (ref, incompleteCode) -> ((TypeOrElementOrAttributeReference)ref).resolveInner();

  private static class CompletionProcessor implements PsiElementProcessor<XmlTag> {
    final List<String> myElements = new ArrayList<>(1);
    String namespace;
    final XmlTag tag;
    private final String prefix;

    CompletionProcessor(XmlTag tag, String prefix) {
      this.tag = tag;
      this.prefix = prefix;
    }

    @Override
    public boolean execute(@NotNull final XmlTag element) {
      String name = element.getAttributeValue(SchemaReferencesProvider.NAME_ATTR_NAME);
      final String prefixByNamespace = tag.getPrefixByNamespace(namespace);
      if (prefixByNamespace != null && !prefixByNamespace.isEmpty() && prefix == null) {
        name = prefixByNamespace + ":" + name;
      }
      myElements.add( name );
      return true;
    }
  }
}
