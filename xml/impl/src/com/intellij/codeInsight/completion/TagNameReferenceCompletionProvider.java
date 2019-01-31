// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlTagNameProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class TagNameReferenceCompletionProvider extends CompletionProvider<CompletionParameters> {
  public static LookupElement[] getTagNameVariants(final @NotNull XmlTag tag, final String prefix) {
    List<LookupElement> elements = new ArrayList<>();
    for (XmlTagNameProvider tagNameProvider : XmlTagNameProvider.EP_NAME.getExtensionList()) {
      tagNameProvider.addTagNameVariants(elements, tag, prefix);
    }
    return elements.toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                @NotNull ProcessingContext context,
                                @NotNull final CompletionResultSet result) {
    LegacyCompletionContributor.processReferences(parameters, result, (reference, set) -> {
      if (reference instanceof TagNameReference) {
        collectCompletionVariants((TagNameReference)reference, set);
      }
      else if (reference instanceof SchemaPrefixReference) {
        TagNameReference tagNameReference = ((SchemaPrefixReference)reference).getTagNameReference();
        if (tagNameReference != null && !tagNameReference.isStartTagFlag()) {
          set.consume(createClosingTagLookupElements((XmlTag)tagNameReference.getElement(), true, tagNameReference.getNameElement()).get(0));
        }
      }
    });
  }

  public static void collectCompletionVariants(TagNameReference tagNameReference,
                                               Consumer<? super LookupElement> consumer) {
    PsiElement element = tagNameReference.getElement();
    if (element instanceof XmlTag) {
      if (!tagNameReference.isStartTagFlag()) {
        for (LookupElement variant : createClosingTagLookupElements((XmlTag)element, false, tagNameReference.getNameElement())) {
          consumer.consume(variant);
        }
      }
      else {
        XmlTag tag = (XmlTag) element;
        for(LookupElement variant: getTagNameVariants(tag, tag.getNamespacePrefix())) {
          consumer.consume(variant);
        }
      }
    }
  }

  public static List<LookupElement> createClosingTagLookupElements(XmlTag tag, boolean includePrefix, ASTNode nameElement) {
    List<LookupElement> result = new ArrayList<>();
    while (tag != null) {
      LookupElementBuilder
        builder = LookupElementBuilder.create(includePrefix || !nameElement.getText().contains(":") ? tag.getName() : tag.getLocalName());
      result.add(LookupElementDecorator.withInsertHandler(
        TailTypeDecorator.withTail(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder),
                                   TailType.createSimpleTailType('>')),
        XmlClosingTagInsertHandler.INSTANCE));
      if (includePrefix) break;
      tag = tag.getParentTag();
      if (tag != null &&
          (XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tag.getNode()) != null ||
           XmlChildRole.START_TAG_START_FINDER.findChild(tag.getNode()) == null)) {
        break;
      }
    }
    return result;
  }

}
