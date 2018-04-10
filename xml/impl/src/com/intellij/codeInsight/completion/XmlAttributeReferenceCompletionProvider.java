/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.dtd.HtmlAttributeDescriptorImpl;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.impl.source.xml.XmlAttributeReference;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED;

public class XmlAttributeReferenceCompletionProvider extends CompletionProvider<CompletionParameters> {
  private static final Logger LOG = Logger.getInstance(XmlAttributeReferenceCompletionProvider.class);

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiReference reference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
    if (reference instanceof XmlAttributeReference) {
      addAttributeReferenceCompletionVariants((XmlAttributeReference)reference, result, null);
    }
  }

  public static void addAttributeReferenceCompletionVariants(XmlAttributeReference reference, CompletionResultSet result,
                                                             @Nullable InsertHandler<LookupElement> replacementInsertHandler) {
    final XmlTag declarationTag = reference.getElement().getParent();
    LOG.assertTrue(declarationTag.isValid());
    final XmlElementDescriptor parentDescriptor = declarationTag.getDescriptor();
    if (parentDescriptor != null) {
      final XmlAttribute[] attributes = declarationTag.getAttributes();
      XmlAttributeDescriptor[] descriptors = parentDescriptor.getAttributesDescriptors(declarationTag);

      descriptors = HtmlUtil.appendHtmlSpecificAttributeCompletions(declarationTag, descriptors, reference.getElement());

      addVariants(result, attributes, descriptors, reference.getElement(), replacementInsertHandler);
    }
  }

  private static void addVariants(final CompletionResultSet result,
                                  final XmlAttribute[] attributes,
                                  final XmlAttributeDescriptor[] descriptors,
                                  XmlAttribute attribute,
                                  @Nullable InsertHandler<LookupElement> replacementInsertHandler) {
    final XmlTag tag = attribute.getParent();
    final PsiFile file = tag.getContainingFile();
    final XmlExtension extension = XmlExtension.getExtension(file);
    final String prefix = attribute.getName().contains(":") && ((XmlAttributeImpl) attribute).getRealLocalName().length() > 0
                          ? attribute.getNamespacePrefix() + ":"
                          : null;

    for (XmlAttributeDescriptor descriptor : descriptors) {
      if (isValidVariant(attribute, descriptor, attributes, extension)) {
        String name = descriptor.getName(tag);

        InsertHandler<LookupElement> insertHandler = XmlAttributeInsertHandler.INSTANCE;

        if (tag instanceof HtmlTag &&
            HtmlUtil.isShortNotationOfBooleanAttributePreferred() &&
            HtmlUtil.isBooleanAttribute(descriptor, tag)) {
          insertHandler = null;
        }

        if (replacementInsertHandler != null) {
          insertHandler = replacementInsertHandler;
        }
        else if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
          final String namespace = ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(tag);

          if (file instanceof XmlFile &&
              namespace != null &&
              namespace.length() > 0 &&
              !name.contains(":") &&
              tag.getPrefixByNamespace(namespace) == null) {
            insertHandler = new XmlAttributeInsertHandler(namespace);
          }
        }
        if (prefix == null || name.startsWith(prefix)) {
          if (prefix != null && name.length() > prefix.length()) {
            name = descriptor.getName(tag).substring(prefix.length());
          }
          LookupElementBuilder element = LookupElementBuilder.create(name);
          if (descriptor instanceof PsiPresentableMetaData) {
            element = element.withIcon(((PsiPresentableMetaData)descriptor).getIcon());
          }
          final int separator = name.indexOf(':');
          if (separator > 0) {
            element = element.withLookupString(name.substring(separator + 1));
          }
          element = element
            .withCaseSensitivity(!(descriptor instanceof HtmlAttributeDescriptorImpl) || ((HtmlAttributeDescriptorImpl)descriptor).isCaseSensitive())
            .withInsertHandler(insertHandler);
          result.addElement(
            descriptor.isRequired() ? PrioritizedLookupElement.withPriority(element.appendTailText("(required)", true), 100) :
            HtmlUtil.isOwnHtmlAttribute(descriptor) ? PrioritizedLookupElement.withPriority(element, 50) : element);
        }
      }
    }
  }

  private static boolean isValidVariant(XmlAttribute attribute,
                                        @NotNull XmlAttributeDescriptor descriptor,
                                        final XmlAttribute[] attributes,
                                        final XmlExtension extension) {
    if (extension.isIndirectSyntax(descriptor)) return false;
    String descriptorName = descriptor.getName(attribute.getParent());
    if (descriptorName == null) {
      LOG.error("Null descriptor name for " + descriptor + " " + descriptor.getClass() + " ");
      return false;
    }
    for (final XmlAttribute otherAttr : attributes) {
      if (otherAttr != attribute && otherAttr.getName().equals(descriptorName)) return false;
    }
    return !descriptorName.contains(DUMMY_IDENTIFIER_TRIMMED);
  }

}
