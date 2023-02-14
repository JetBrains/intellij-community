// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlNamespaceHelper;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Avdeev
*/
public class ExtendedTagInsertHandler extends XmlTagInsertHandler {

  private static final Logger LOG = Logger.getInstance(ExtendedTagInsertHandler.class);

  protected final String myElementName;
  @Nullable protected final String myNamespace;
  @Nullable protected final String myNamespacePrefix;

  public ExtendedTagInsertHandler(final String elementName, @Nullable final String namespace, @Nullable final String namespacePrefix) {
    myElementName = elementName;
    myNamespace = namespace;
    myNamespacePrefix = namespacePrefix;
  }

  @Override
  public void handleInsert(@NotNull final InsertionContext context, @NotNull final LookupElement item) {

    final XmlFile contextFile = (XmlFile)context.getFile();
    final XmlExtension extension = XmlExtension.getExtension(contextFile);
    final XmlFile file = extension.getContainingFile(contextFile);
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
    caretMarker.setGreedyToRight(true);

    final XmlNamespaceHelper.Runner<String, IncorrectOperationException> runAfter =
      new XmlNamespaceHelper.Runner<>() {

        @Override
        public void run(final String namespacePrefix) {
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          final PsiElement element = file.findElementAt(context.getStartOffset());
          if (element != null) {
            qualifyWithPrefix(namespacePrefix, element, document);
            PsiDocumentManager.getInstance(project).commitDocument(document);
          }
          editor.getCaretModel().moveToOffset(caretMarker.getEndOffset());
          doDefault(context, item);
        }
      };

    try {
      final String prefixByNamespace = getPrefixByNamespace(file, myNamespace);
      if (myNamespacePrefix != null || StringUtil.isEmpty(prefixByNamespace)) {
        final String nsPrefix = myNamespacePrefix == null ? suggestPrefix(file, myNamespace) : myNamespacePrefix;
        XmlNamespaceHelper.getHelper(file).insertNamespaceDeclaration(file, editor, Collections.singleton(myNamespace), nsPrefix, runAfter);
        FeatureUsageTracker.getInstance().triggerFeatureUsed(XmlCompletionContributor.TAG_NAME_COMPLETION_FEATURE);
      } else {
        runAfter.run(prefixByNamespace);    // qualify && complete
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected void doDefault(@NotNull InsertionContext context, @NotNull LookupElement item) {
    super.handleInsert(context, item);
  }

  protected boolean isNamespaceBound(PsiElement psiElement) {
    PsiElement parent = psiElement.getParent();
    if (!(parent instanceof XmlTag tag)) return false;
    final XmlElementDescriptor tagDescriptor = tag.getDescriptor();
    final String tagNamespace = tag.getNamespace();
    assert myNamespace != null;
    return tagDescriptor != null && !(tagDescriptor instanceof AnyXmlElementDescriptor) && myNamespace.equals(tagNamespace);
  }

  @Nullable
  public static String getPrefixByNamespace(XmlFile file, final String namespace) {
    final XmlTag tag = file.getRootTag();
    return tag == null ? null : tag.getPrefixByNamespace(namespace);
  }

  @Nullable
  public static String suggestPrefix(XmlFile file, @Nullable String namespace) {
    if (namespace == null) {
      return null;
    }
    for (XmlSchemaProvider provider : XmlSchemaProvider.getAvailableProviders(file)) {
      String prefix = provider.getDefaultPrefix(namespace, file);
      if (prefix != null) {
        return prefix;
      }
    }
    return null; 
  }

  protected Set<String> getNamespaces(final XmlFile file) {
    return XmlNamespaceHelper.getHelper(file).getNamespacesByTagName(myElementName, file);
  }

  protected void qualifyWithPrefix(final String namespacePrefix, final PsiElement element, final Document document) {
    qualifyWithPrefix(namespacePrefix, element);
  }

  public static void qualifyWithPrefix(final String namespacePrefix, final PsiElement element) {
    final PsiElement tag = element.getParent();
    if (tag instanceof XmlTag) {
      final String prefix = ((XmlTag)tag).getNamespacePrefix();
      if (!prefix.equals(namespacePrefix) && StringUtil.isNotEmpty(namespacePrefix)) {
        String toInsert = namespacePrefix + ":";
        Document document = element.getContainingFile().getViewProvider().getDocument();
        assert document != null;

        ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());
        ASTNode endTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tag.getNode());
        if (endTagName != null) {
          document.insertString(endTagName.getStartOffset(), toInsert);
        }
        if (startTagName != null) {
          document.insertString(startTagName.getStartOffset(), toInsert);
        }
      }
    }
  }
}
