// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class SchemaReferencesProvider extends PsiReferenceProvider {
  private static final @NonNls String VALUE_ATTR_NAME = "value";
  static final @NonNls String NAME_ATTR_NAME = "name";

  static final @NonNls String MEMBER_TYPES_ATTR_NAME = "memberTypes";
  static final @NonNls String ITEM_TYPE_ATTR_NAME = "itemType";
  static final @NonNls String BASE_ATTR_NAME = "base";
  public static final @NonNls String GROUP_TAG_NAME = "group";

  public static final @NonNls String ATTRIBUTE_GROUP_TAG_NAME = "attributeGroup";
  public static final @NonNls String ATTRIBUTE_TAG_NAME = "attribute";
  public static final @NonNls String ELEMENT_TAG_NAME = "element";
  public static final @NonNls String SIMPLE_TYPE_TAG_NAME = "simpleType";

  public static final @NonNls String COMPLEX_TYPE_TAG_NAME = "complexType";
  static final @NonNls String REF_ATTR_NAME = "ref";
  static final @NonNls String TYPE_ATTR_NAME = "type";
  static final @NonNls String SUBSTITUTION_GROUP_ATTR_NAME = "substitutionGroup";

  public String[] getCandidateAttributeNamesForSchemaReferences() {
    return new String[] {REF_ATTR_NAME,TYPE_ATTR_NAME, BASE_ATTR_NAME,NAME_ATTR_NAME, SUBSTITUTION_GROUP_ATTR_NAME,MEMBER_TYPES_ATTR_NAME,
      VALUE_ATTR_NAME, ITEM_TYPE_ATTR_NAME};
  }

  public static final class NameReference implements PsiReference {
    private final PsiElement myElement;

    public NameReference(PsiElement element) {
      myElement = element;
    }

    @Override
    public @NotNull PsiElement getElement() {
      return myElement;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
      return ElementManipulators.getValueTextRange(myElement);
    }

    @Override
    public @Nullable PsiElement resolve() {
      return myElement.getParent().getParent();
    }

    @Override
    public @NotNull String getCanonicalText() {
      String text = myElement.getText();
      return text.substring(1,text.length()- 1);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      return ElementManipulators.handleContentChange(
        myElement,
        getRangeInElement(),
        newElementName.substring(newElementName.indexOf(':') + 1)
      );
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, final @NotNull ProcessingContext context) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof XmlAttribute)) return PsiReference.EMPTY_ARRAY;
    final String attrName = ((XmlAttribute)parent).getName();

    return switch (attrName) {
      case VALUE_ATTR_NAME -> PsiReference.EMPTY_ARRAY;
      case NAME_ATTR_NAME -> new PsiReference[]{new NameReference(element)};
      case MEMBER_TYPES_ATTR_NAME -> {
        final List<PsiReference> result = new ArrayList<>(1);
        final String text = element.getText();
        int lastIndex = 1;
        final int testLength = text.length();
        for (int i = 1; i < testLength; ++i) {
          if (Character.isWhitespace(text.charAt(i))) {
            if (lastIndex != i) result.add(new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, i)));
            lastIndex = i + 1;
          }
        }
        if (lastIndex != testLength - 1) {
          result.add(new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, testLength - 1)));
        }
        yield result.toArray(PsiReference.EMPTY_ARRAY);
      }
      default -> {
        final PsiReference prefix = createSchemaPrefixReference(element);
        final PsiReference ref = createTypeOrElementOrAttributeReference(element, prefix == null ? null : prefix.getCanonicalText());
        yield prefix == null ? new PsiReference[]{ref} : new PsiReference[]{ref, prefix};
      }
    };
  }

  public static PsiReference createTypeOrElementOrAttributeReference(final @NotNull PsiElement element) {
    return createTypeOrElementOrAttributeReference(element, null);
  }

  public static PsiReference createTypeOrElementOrAttributeReference(final @NotNull PsiElement element, String ns) {
    final int length = element.getTextLength();
    int offset = (element instanceof XmlAttributeValue) ?
      XmlUtil.findPrefixByQualifiedName(((XmlAttributeValue)element).getValue()).length() : 0;
    if (offset > 0) offset++;
    final TypeOrElementOrAttributeReference ref = new TypeOrElementOrAttributeReference(element, length >= 2 ? new TextRange(1 + offset, length - 1) : TextRange.EMPTY_RANGE);
    ref.setNamespacePrefix(ns);
    return ref;
  }

  private static @Nullable PsiReference createSchemaPrefixReference(final PsiElement element) {
    if (element instanceof XmlAttributeValue attributeValue) {
      final String prefix = XmlUtil.findPrefixByQualifiedName(attributeValue.getValue());
      if (!prefix.isEmpty()) {
        return new SchemaPrefixReference(attributeValue, TextRange.from(1, prefix.length()), prefix, null);
      }
    }
    return null;
  }

  public static @Nullable XmlNSDescriptorImpl findRedefinedDescriptor(XmlTag tag, String text) {
    final String localName = XmlUtil.findLocalNameByQualifiedName(text);
    for(XmlTag parentTag = tag.getParentTag(); parentTag != null; parentTag = parentTag.getParentTag()) {

      if (localName.equals(parentTag.getAttributeValue("name"))) {
        final XmlTag grandParent = parentTag.getParentTag();

        if (grandParent != null && "redefine".equals(grandParent.getLocalName())) {
          return XmlNSDescriptorImpl.getRedefinedElementDescriptor(grandParent);
        }
      }
    }

    return null;
  }
}
