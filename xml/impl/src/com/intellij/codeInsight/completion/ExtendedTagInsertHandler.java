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
import com.intellij.psi.xml.XmlDocument;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Avdeev
*/
class ExtendedTagInsertHandler extends XmlTagInsertHandler {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.ExtendedTagInsertHandler");

  protected final String myElementName;
  @Nullable private final String myNamespacePrefix;

  public ExtendedTagInsertHandler(final String elementName, @Nullable final String namespacePrefix) {
    myElementName = elementName;
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

    final PsiElement psiElement = file.findElementAt(startOffset);
    assert psiElement != null;
    if (isNamespaceBound(psiElement)) {
      doDefault(context, startOffset, data, item, signatureSelected, completionChar);
      return;
    }

    final Editor editor = context.editor;
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    final RangeMarker rangeMarker = document.createRangeMarker(startOffset, startOffset);
    final int caretOffset = editor.getCaretModel().getOffset();
    final RangeMarker caretMarker = document.createRangeMarker(caretOffset, caretOffset);

    final Set<String> namespaces = getNamespaces(file);
    @Nullable String nsPrefix = getPrefix(file, namespaces);

    final XmlExtension.Runner<String, IncorrectOperationException> runAfter =
      new XmlExtension.Runner<String, IncorrectOperationException>() {

        public void run(final String namespacePrefix) {

          PsiDocumentManager.getInstance(project).commitDocument(document);
          final PsiElement element = file.findElementAt(rangeMarker.getStartOffset());
          if (element != null) {
            qualifyWithPrefix(namespacePrefix, element, document);
            PsiDocumentManager.getInstance(project).commitDocument(document);
          }
          final int offset = rangeMarker.getStartOffset();
          context.setStartOffset(rangeMarker.getStartOffset());
          editor.getCaretModel().moveToOffset(caretMarker.getStartOffset());
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          doDefault(context, offset, data, item, signatureSelected, completionChar);
        }
      };

    try {
      @Nullable String prefix = getExistingPrefix(file, namespaces);
      if (prefix == null) {
        XmlExtension.getExtension(file).insertNamespaceDeclaration(file, editor, namespaces, nsPrefix, runAfter);
      } else {
        runAfter.run(prefix);    // qualify && complete
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected void doDefault(final CompletionContext context, final int startOffset, final LookupData data, final LookupItem item,
                         final boolean signatureSelected,
                         final char completionChar) {
    ExtendedTagInsertHandler.super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
  }

  protected boolean isNamespaceBound(PsiElement psiElement) {
    final XmlTag tag = (XmlTag)psiElement.getParent();
    final XmlElementDescriptor tagDescriptor = tag.getDescriptor();
    final String tagNamespace = tag.getNamespace();
    return (tagDescriptor != null && !(tagDescriptor instanceof AnyXmlElementDescriptor) && !StringUtil.isEmpty(tagNamespace));
  }

  @Nullable
  private static String getExistingPrefix(XmlFile file, Set<String> namespaces) {
    final XmlDocument document = file.getDocument();
    assert document != null;
    final XmlTag tag = document.getRootTag();
    assert tag != null;
    for (String ns: tag.knownNamespaces()) {
      if (namespaces.contains(ns)) {
        final String prefix = tag.getPrefixByNamespace(ns);
        if (!StringUtil.isEmpty(prefix))
          return prefix;
      }
    }
    return null;
  }

  @Nullable
  private String getPrefix(final XmlFile file, final Set<String> namespaces) {
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
    return nsPrefix;
  }

  protected Set<String> getNamespaces(final XmlFile file) {
    return XmlExtension.getExtension(file).getNamespacesByTagName(myElementName, file);
  }

  protected void qualifyWithPrefix(final String namespacePrefix, final PsiElement element, final Document document) {
    final PsiElement tag = element.getParent();
    if (tag instanceof XmlTag) {
      final String prefix = ((XmlTag)tag).getNamespacePrefix();
      if (!prefix.equals(namespacePrefix)) {
        final String name = namespacePrefix + ":" + ((XmlTag)tag).getLocalName();
        try {
          ((XmlTag)tag).setName(name);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }
}
