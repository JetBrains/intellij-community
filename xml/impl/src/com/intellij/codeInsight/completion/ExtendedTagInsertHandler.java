package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Dmitry Avdeev
*/
class ExtendedTagInsertHandler extends XmlTagInsertHandler {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.ExtendedTagInsertHandler");

  private final String myTagName;
  @Nullable private final String myNamespacePrefix;

  public ExtendedTagInsertHandler(final String tagName, @Nullable final String namespacePrefix) {
    myTagName = tagName;
    myNamespacePrefix = namespacePrefix;
  }

  public void handleInsert(final CompletionContext context,
                           final int startOffset,
                           final LookupData data,
                           final LookupItem item,
                           final boolean signatureSelected,
                           final char completionChar) {

    final XmlFile file = (XmlFile)context.file;
    final Project project = context.project;

    final XmlExtension extension = XmlExtension.getExtension(file);
    final Set<String> namespaces = extension.getNamespacesByTagName(myTagName, file);
    final PsiElement psiElement = file.findElementAt(startOffset);
    assert psiElement != null;
    final XmlTag tag = (XmlTag)psiElement.getParent();
    final String tagPrefix = tag.getNamespacePrefix();
    final XmlElementDescriptor tagDescriptor = tag.getDescriptor();
    final String tagNamespace = tag.getNamespace();
    if (tagDescriptor != null && !(tagDescriptor instanceof AnyXmlElementDescriptor) && !StringUtil.isEmpty(tagNamespace)) {
      ExtendedTagInsertHandler.super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
      return;
    }

    final Editor editor = context.editor;
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    final RangeMarker rangeMarker = document.createRangeMarker(context.startOffset, context.startOffset);
    final int caretOffset = editor.getCaretModel().getOffset();
    final RangeMarker caretMarker = document.createRangeMarker(caretOffset, caretOffset);

    @Nullable String nsPrefix = myNamespacePrefix;
    if (myNamespacePrefix == null && namespaces.size() > 0) {
      final XmlSchemaProvider provider = XmlSchemaProvider.getAvailableProvider(file);
      if (provider != null) {
        for (String namespace : namespaces) {
          final String prefix = provider.getDefaultPrefix(namespace, file);
          if (prefix != null) {
            if (nsPrefix == null) {
              nsPrefix = prefix;
            } else if (!prefix.equals(nsPrefix)) {
              nsPrefix = null;
              break;
            }
          }
        }
      }
    }

    final XmlExtension.Runner<String, IncorrectOperationException> runAfter =
      new XmlExtension.Runner<String, IncorrectOperationException>() {

        public void run(final String namespacePrefix) {

          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
          final PsiElement element = file.findElementAt(rangeMarker.getStartOffset());
          if (element != null) {
            final PsiElement tag = element.getParent();
            if (tag instanceof XmlTag) {
              final String prefix = ((XmlTag)tag).getNamespacePrefix();
              if (!prefix.equals(namespacePrefix)) {
                final String name = namespacePrefix + ":" + ((XmlTag)tag).getLocalName();
                try {
                  ((XmlTag)tag).setName(name);
                  PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
            }
          }
          final int offset = rangeMarker.getStartOffset();
          context.startOffset = rangeMarker.getStartOffset();
          editor.getCaretModel().moveToOffset(caretMarker.getStartOffset());
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          ExtendedTagInsertHandler.super.handleInsert(context, offset, data, item, signatureSelected, completionChar);
        }
      };

    try {
      final String[] strings = tag.knownNamespaces();
      if (Arrays.asList(strings).contains(tagNamespace) && (namespaces.size() == 0 || namespaces.contains(tagNamespace))) {
        if (nsPrefix != null && !nsPrefix.equals(tagPrefix)) {
          runAfter.run(nsPrefix);
        } else {
          ExtendedTagInsertHandler.super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);          
        }
      } else {
        extension.insertNamespaceDeclaration(file, editor, namespaces, nsPrefix, runAfter);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
