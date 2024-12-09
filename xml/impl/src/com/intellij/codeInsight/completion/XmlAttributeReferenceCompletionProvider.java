// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
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
                                @NotNull ProcessingContext context,
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
    final String prefix = attribute.getName().contains(":") && XmlAttributeImpl.getRealName(attribute).length() > 0
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
          if (descriptor instanceof PsiPresentableMetaData presentableMetaData) {
            if (descriptor instanceof PsiPresentableMetaDataRenderStrategy renderStrategy && renderStrategy.isRenderExpensive()) {
              element = element.withExpensiveRenderer(new LookupElementRenderer<>() {
                @Override
                public void renderElement(LookupElement element, LookupElementPresentation presentation) {
                  element.renderElement(presentation);
                  presentation.setIcon(presentableMetaData.getIcon());
                  String typeName = presentableMetaData.getTypeName();
                  if (!StringUtil.isEmpty(typeName)) {
                    presentation.setTypeText(typeName);
                  }
                }
              });
            }
            else {
              element = element.withIcon(presentableMetaData.getIcon());
              String typeName = presentableMetaData.getTypeName();
              if (!StringUtil.isEmpty(typeName)) {
                element = element.withTypeText(typeName);
              }
            }
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

  public static boolean isValidVariant(XmlAttribute attribute,
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
