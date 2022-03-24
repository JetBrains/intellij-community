// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.xml.*;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.index.XsdNamespaceBuilder;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class DefaultXmlTagNameProvider implements XmlTagNameProvider {

  private static final Logger LOG = Logger.getInstance(DefaultXmlTagNameProvider.class);

  @Override
  public void addTagNameVariants(List<LookupElement> elements, @NotNull XmlTag tag, String prefix) {
    final List<String> namespaces;
    if (prefix.isEmpty()) {
      namespaces = new ArrayList<>(Arrays.asList(tag.knownNamespaces()));
      namespaces.add(XmlUtil.EMPTY_URI); // empty namespace
    }
    else {
      namespaces = new ArrayList<>(Collections.singletonList(tag.getNamespace()));
    }
    PsiFile psiFile = tag.getContainingFile();
    XmlExtension xmlExtension = XmlExtension.getExtension(psiFile);
    List<XmlElementDescriptor> variants = TagNameVariantCollector.getTagDescriptors(tag, namespaces, null);

    if (psiFile instanceof XmlFile && ((XmlFile) psiFile).getRootTag() == tag) {
      addXmlProcessingInstructions(elements, tag);
      if (variants.isEmpty()) {
        getRootTagsVariants(tag, elements);
        return;
      }
    }

    final Set<String> visited = new HashSet<>();
    for (XmlElementDescriptor descriptor : variants) {
      String qname = descriptor.getName(tag);
      if (!visited.add(qname)) continue;
      if (!prefix.isEmpty() && qname.startsWith(prefix + ":")) {
        qname = qname.substring(prefix.length() + 1);
      }

      PsiElement declaration = descriptor.getDeclaration();
      if (declaration != null && !declaration.isValid()) {
        LOG.error(descriptor + " contains invalid declaration: " + declaration);
      }
      LookupElementBuilder lookupElement =
        declaration == null ? LookupElementBuilder.create(qname) : LookupElementBuilder.create(declaration, qname);
      final int separator = qname.indexOf(':');
      if (separator > 0) {
        lookupElement = lookupElement.withLookupString(qname.substring(separator + 1));
      }
      Icon icon = AllIcons.Nodes.Tag;
      if (descriptor instanceof PsiPresentableMetaData) {
        icon = ((PsiPresentableMetaData)descriptor).getIcon();
      }
      lookupElement = lookupElement.withIcon(icon);
      if (xmlExtension.useXmlTagInsertHandler()) {
        lookupElement = lookupElement.withInsertHandler(XmlTagInsertHandler.INSTANCE);
      }
      boolean deprecated =
        descriptor instanceof XmlDeprecationOwnerDescriptor && ((XmlDeprecationOwnerDescriptor)descriptor).isDeprecated();
      if (deprecated) {
        lookupElement = lookupElement.withStrikeoutness(true);
      }
      lookupElement = lookupElement.withCaseSensitivity(!(descriptor instanceof HtmlElementDescriptorImpl));
      elements.add(PrioritizedLookupElement.withPriority(lookupElement, deprecated ? -1 : separator > 0 ? 0 : 1));
    }
  }

  private static void addXmlProcessingInstructions(@NotNull List<LookupElement> elements, @NotNull XmlTag tag) {
    final PsiElement file = tag.getParent();
    final PsiElement prolog = file.getFirstChild();
    if (prolog.getTextLength() != 0) {
      // "If [the XML Prolog] exists, it must come first in the document."
      return;
    }

    if (ContainerUtil.exists(tag.getChildren(), OuterLanguageElement.class::isInstance)) {
      return;
    }

    final LookupElementBuilder xmlDeclaration = LookupElementBuilder
      .create("?xml version=\"1.0\" encoding=\"\" ?>")
      .withPresentableText("<?xml version=\"1.0\" encoding=\"\" ?>")
      .withInsertHandler((context, item) -> {
        int offset = context.getEditor().getCaretModel().getOffset();
        context.getEditor().getCaretModel().moveToOffset(offset - 4);
        AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
      });
    elements.add(xmlDeclaration);
  }

  private static void getRootTagsVariants(final XmlTag tag, final List<? super LookupElement> elements) {
    final FileBasedIndex fbi = FileBasedIndex.getInstance();
    Collection<String> result = new ArrayList<>();
    Processor<String> processor = Processors.cancelableCollectProcessor(result);
    fbi.processAllKeys(XmlNamespaceIndex.NAME, processor, tag.getProject());

    final GlobalSearchScope scope = GlobalSearchScope.everythingScope(tag.getProject());
    for (final String ns : result) {
      if (ns.isEmpty()) continue;
      fbi.processValues(XmlNamespaceIndex.NAME, ns, null, new FileBasedIndex.ValueProcessor<>() {
        @Override
        public boolean process(@NotNull final VirtualFile file, XsdNamespaceBuilder value) {
          List<String> tags = value.getRootTags();
          for (String s : tags) {
            elements.add(LookupElementBuilder.create(s)
                           .withIcon(AllIcons.Nodes.Tag)
                           .withTypeText(ns).withInsertHandler(new XmlTagInsertHandler() {
              @Override
              public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
                final Editor editor = context.getEditor();
                final Document document = context.getDocument();
                final int caretOffset = editor.getCaretModel().getOffset();
                final RangeMarker caretMarker = document.createRangeMarker(caretOffset, caretOffset);
                caretMarker.setGreedyToRight(true);

                XmlFile psiFile = (XmlFile)context.getFile();
                boolean incomplete = XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) == null &&
                                     XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) == null;
                XmlNamespaceHelper.getHelper(psiFile).insertNamespaceDeclaration(psiFile, editor, Collections.singleton(ns), null, null);
                editor.getCaretModel().moveToOffset(caretMarker.getEndOffset());

                XmlTag rootTag = psiFile.getRootTag();
                if (incomplete) {
                  XmlToken token = XmlUtil.getTokenOfType(rootTag, XmlTokenType.XML_EMPTY_ELEMENT_END);
                  if (token != null) token.delete(); // remove tag end added by smart attribute adder :(
                  PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(document);
                  super.handleInsert(context, item);
                }
              }
            }));
          }
          return true;
        }
      }, scope);
    }
  }
}
