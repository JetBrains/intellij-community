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
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @by Maxim.Mossienko
 * @author Konstantin Bulenkov
 */
public class SchemaReferencesProvider extends PsiReferenceProvider {
  @NonNls private static final String VALUE_ATTR_NAME = "value";
  @NonNls static final String NAME_ATTR_NAME = "name";

  @NonNls static final String MEMBER_TYPES_ATTR_NAME = "memberTypes";
  @NonNls static final String ITEM_TYPE_ATTR_NAME = "itemType";
  @NonNls static final String BASE_ATTR_NAME = "base";
  @NonNls static final String GROUP_TAG_NAME = "group";

  @NonNls static final String ATTRIBUTE_GROUP_TAG_NAME = "attributeGroup";
  @NonNls static final String ATTRIBUTE_TAG_NAME = "attribute";
  @NonNls static final String ELEMENT_TAG_NAME = "element";
  @NonNls static final String SIMPLE_TYPE_TAG_NAME = "simpleType";

  @NonNls static final String COMPLEX_TYPE_TAG_NAME = "complexType";
  @NonNls static final String REF_ATTR_NAME = "ref";
  @NonNls static final String TYPE_ATTR_NAME = "type";
  @NonNls static final String SUBSTITUTION_GROUP_ATTR_NAME = "substitutionGroup";

  public String[] getCandidateAttributeNamesForSchemaReferences() {
    return new String[] {REF_ATTR_NAME,TYPE_ATTR_NAME, BASE_ATTR_NAME,NAME_ATTR_NAME, SUBSTITUTION_GROUP_ATTR_NAME,MEMBER_TYPES_ATTR_NAME,
      VALUE_ATTR_NAME, ITEM_TYPE_ATTR_NAME};
  }

  public static class NameReference implements PsiReference {
    private final PsiElement myElement;

    public NameReference(PsiElement element) {
      myElement = element;
    }

    @Override
    public PsiElement getElement() {
      return myElement;
    }

    @Override
    public TextRange getRangeInElement() {
      return ElementManipulators.getValueTextRange(myElement);
    }

    @Override
    @Nullable
    public PsiElement resolve() {
      return myElement.getParent().getParent();
    }

    @Override
    @NotNull
    public String getCanonicalText() {
      String text = myElement.getText();
      return text.substring(1,text.length()- 1);
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return ElementManipulators.getManipulator(myElement).handleContentChange(
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
    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    @Override
    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof XmlAttribute)) return PsiReference.EMPTY_ARRAY;
    final String attrName = ((XmlAttribute)parent).getName();

    if (VALUE_ATTR_NAME.equals(attrName)) {
      return PsiReference.EMPTY_ARRAY;
    } else if (NAME_ATTR_NAME.equals(attrName)) {
      return new PsiReference[] { new NameReference(element) };
    } else if (MEMBER_TYPES_ATTR_NAME.equals(attrName)) {
      final List<PsiReference> result = new ArrayList<>(1);
      final String text = element.getText();
      int lastIndex = 1;
      final int testLength = text.length();

      for(int i = 1; i < testLength; ++i) {
        if (Character.isWhitespace(text.charAt(i))) {
          if (lastIndex != i) result.add( new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, i) ) );
          lastIndex = i + 1;
        }
      }

      if (lastIndex != testLength - 1) result.add( new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, testLength - 1) ) );
      return result.toArray(new PsiReference[result.size()]);
    } else {
      final PsiReference prefix = createSchemaPrefixReference(element);
      final PsiReference ref = createTypeOrElementOrAttributeReference(element, prefix == null ? null : prefix.getCanonicalText());
      return prefix == null ? new PsiReference[] {ref} : new PsiReference[] {ref, prefix};
    }
  }

  public static PsiReference createTypeOrElementOrAttributeReference(final PsiElement element) {
    return createTypeOrElementOrAttributeReference(element, null);
  }

  public static PsiReference createTypeOrElementOrAttributeReference(final PsiElement element, String ns) {
    final int length = element.getTextLength();
    int offset = (element instanceof XmlAttributeValue) ?
      XmlUtil.findPrefixByQualifiedName(((XmlAttributeValue)element).getValue()).length() : 0;
    if (offset > 0) offset++;
    final TypeOrElementOrAttributeReference ref = new TypeOrElementOrAttributeReference(element, length >= 2 ? new TextRange(1 + offset, length - 1) : TextRange.EMPTY_RANGE);
    ref.setNamespacePrefix(ns);
    return ref;
  }

  @Nullable
  private static PsiReference createSchemaPrefixReference(final PsiElement element) {
    if (element instanceof XmlAttributeValue) {
      final XmlAttributeValue attributeValue = (XmlAttributeValue)element;
      final String prefix = XmlUtil.findPrefixByQualifiedName(attributeValue.getValue());
      if (!prefix.isEmpty()) {
        return new SchemaPrefixReference(attributeValue, TextRange.from(1, prefix.length()), prefix, null);
      }
    }
    return null;
  }

  @Nullable
  public static XmlNSDescriptorImpl findRedefinedDescriptor(XmlTag tag, String text) {
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
