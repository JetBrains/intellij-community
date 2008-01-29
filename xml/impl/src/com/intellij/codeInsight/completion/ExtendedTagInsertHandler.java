package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.Nullable;

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
    final Editor editor = context.editor;
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    final RangeMarker rangeMarker = document.createRangeMarker(context.startOffset, context.startOffset);
    final int caretOffset = editor.getCaretModel().getOffset();
    final RangeMarker caretMarker = document.createRangeMarker(caretOffset, caretOffset);

    try {
      extension.insertNamespaceDeclaration(file, editor, namespaces, myNamespacePrefix,
        new Consumer<String>() {

          public void consume(final String namespacePrefix) {

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
        });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
