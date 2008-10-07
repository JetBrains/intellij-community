package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
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
import com.intellij.featureStatistics.FeatureUsageTracker;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Avdeev
*/
public class ExtendedTagInsertHandler extends XmlTagInsertHandler {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.ExtendedTagInsertHandler");

  protected final String myElementName;
  @Nullable protected final String myNamespace;
  @Nullable protected final String myNamespacePrefix;

  public ExtendedTagInsertHandler(final String elementName, @Nullable final String namespace, @Nullable final String namespacePrefix) {
    myElementName = elementName;
    myNamespace = namespace;
    myNamespacePrefix = namespacePrefix;
  }

  public void handleInsert(final InsertionContext context, final LookupElement item) {

    final XmlFile contextfile = (XmlFile)context.getFile();
    final XmlExtension extension = XmlExtension.getExtension(contextfile);
    final XmlFile file = extension.getContainingFile(contextfile);
    final Project project = context.getProject();

    assert file != null;
    final PsiElement psiElement = file.findElementAt(context.getStartOffset());
    assert psiElement != null;
    if (isNamespaceBound(psiElement)) {
      doDefault(context, item);
      return;
    }

    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    final int caretOffset = editor.getCaretModel().getOffset();
    final RangeMarker caretMarker = document.createRangeMarker(caretOffset, caretOffset);

    final XmlExtension.Runner<String, IncorrectOperationException> runAfter =
      new XmlExtension.Runner<String, IncorrectOperationException>() {

        public void run(final String namespacePrefix) {

          PsiDocumentManager.getInstance(project).commitDocument(document);
          final PsiElement element = file.findElementAt(context.getStartOffset());
          if (element != null) {
            qualifyWithPrefix(namespacePrefix, element, document);
            PsiDocumentManager.getInstance(project).commitDocument(document);
          }
          editor.getCaretModel().moveToOffset(caretMarker.getStartOffset());
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          doDefault(context, item);
        }
      };

    try {
      final String prefixByNamespace = getPrefixByNamespace(file);
      if (myNamespacePrefix != null || StringUtil.isEmpty(prefixByNamespace)) {
        final String nsPrefix = myNamespacePrefix == null ? suggestPrefix(file) : myNamespacePrefix;
        extension.insertNamespaceDeclaration(file, editor, Collections.singleton(myNamespace), nsPrefix, runAfter);
        FeatureUsageTracker.getInstance().triggerFeatureUsed(XmlCompletionContributor.TAG_NAME_COMPLETION_FEATURE);
      } else {
        runAfter.run(prefixByNamespace);    // qualify && complete
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected void doDefault(final InsertionContext context, final LookupElement item) {
    ExtendedTagInsertHandler.super.handleInsert(context, item);
  }

  protected boolean isNamespaceBound(PsiElement psiElement) {
    final XmlTag tag = (XmlTag)psiElement.getParent();
    final XmlElementDescriptor tagDescriptor = tag.getDescriptor();
    final String tagNamespace = tag.getNamespace();
    return tagDescriptor != null && !(tagDescriptor instanceof AnyXmlElementDescriptor) && myNamespace.equals(tagNamespace);
  }

  @Nullable
  private String getPrefixByNamespace(XmlFile file) {
    final XmlDocument document = file.getDocument();
    assert document != null;
    final XmlTag tag = document.getRootTag();
    return tag == null ? null : tag.getPrefixByNamespace(myNamespace);
  }

  @Nullable
  protected String suggestPrefix(XmlFile file) {
    if (myNamespace == null) {
      return null;
    }
    final XmlSchemaProvider provider = XmlSchemaProvider.getAvailableProvider(file);
    return provider == null ? null : provider.getDefaultPrefix(myNamespace, file);
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
