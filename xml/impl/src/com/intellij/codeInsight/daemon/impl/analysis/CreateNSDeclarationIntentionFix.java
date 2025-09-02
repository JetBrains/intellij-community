// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.application.options.XmlSettings;
import com.intellij.codeInsight.completion.ExtendedTagInsertHandler;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlNamespaceHelper;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class CreateNSDeclarationIntentionFix implements HintAction, LocalQuickFix {

  private static final Logger LOG = Logger.getInstance(CreateNSDeclarationIntentionFix.class);

  private final String myNamespacePrefix;
  private final PsiAnchor myElement;
  private final PsiAnchor myToken;

  private @NotNull XmlFile getFile() {
    return (XmlFile)myElement.getFile();
  }

  protected CreateNSDeclarationIntentionFix(final @NotNull PsiElement element,
                                            final @NotNull String namespacePrefix) {
    this(element, namespacePrefix, null);
  }

  public CreateNSDeclarationIntentionFix(final @NotNull PsiElement element,
                                         @NotNull String namespacePrefix,
                                         final @Nullable XmlToken token) {
    myNamespacePrefix = namespacePrefix;
    myElement = PsiAnchor.create(element);
    myToken = token == null ? null : PsiAnchor.create(token);
  }

  @Override
  public @NotNull String getText() {
    final String alias = getXmlNamespaceHelper().getNamespaceAlias(getFile());
    return XmlPsiBundle.message("xml.quickfix.create.namespace.declaration.text", alias);
  }

  private XmlNamespaceHelper getXmlNamespaceHelper() {
    return XmlNamespaceHelper.getHelper(getFile());
  }

  @Override
  public @NotNull String getFamilyName() {
    return XmlPsiBundle.message("xml.quickfix.create.namespace.declaration.family");
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    final PsiFile containingFile = descriptor.getPsiElement().getContainingFile();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    final PsiFile psiFile = editor != null ? PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) : null;
    if (psiFile == null || !Comparing.equal(psiFile.getVirtualFile(), containingFile.getVirtualFile())) return;

    try {
      invoke(project, editor, containingFile);
    }
    catch (IncorrectOperationException ex) {
      LOG.error(ex);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return doPreview(project, previewDescriptor.getPsiElement(), null);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement element = myElement.retrieve();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    return doPreview(project, PsiTreeUtil.findSameElementInCopy(element, psiFile), editor);
  }

  private @NotNull IntentionPreviewInfo doPreview(@NotNull Project project, PsiElement element, @Nullable Editor editor) {
    PsiFile psiFile = element.getContainingFile();
    if (!(psiFile instanceof XmlFile xmlFile)) return IntentionPreviewInfo.EMPTY;
    List<String> namespaces = getNamespaces(element, xmlFile);
    String namespace = namespaces.isEmpty() ? "" : namespaces.get(0);
    new MyStringToAttributeProcessor(element, project, editor, xmlFile)
      .doSomethingWithGivenStringToProduceXmlAttributeNowPlease(namespace);
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof XmlFile)) return false;
    PsiElement element = myElement.retrieve();
    XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
    return element != null && rootTag != null && !PsiUtilCore.hasErrorElementChild(rootTag);
  }

  /**
   * Looks up the unbound namespaces and sorts them
   */
  private @NotNull List<String> getNamespaces(PsiElement element, XmlFile xmlFile) {
    if (element instanceof XmlAttribute) {
      element = element.getParent();
    }
    Set<String> set = getXmlNamespaceHelper().guessUnboundNamespaces(element, xmlFile);

    final String match = getUnboundNamespaceForPrefix(myNamespacePrefix, xmlFile, set);
    if (match != null) {
      return Collections.singletonList(match);
    }

    List<String> namespaces = new ArrayList<>(set);
    Collections.sort(namespaces);
    return namespaces;
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, @NotNull PsiFile psiFile) throws IncorrectOperationException {
    final PsiElement element = myElement.retrieve();
    if (element == null) return;
    XmlFile xmlFile = getFile();
    final String[] namespaces = ArrayUtilRt.toStringArray(getNamespaces(element, xmlFile));

    runActionOverSeveralAttributeValuesAfterLettingUserSelectTheNeededOne(
      namespaces,
      project,
      new MyStringToAttributeProcessor(element, project, editor, xmlFile), getSelectNSActionTitle(),
      this,
      editor);
  }

  /**
   * Given a prefix in a file and a set of candidate namespaces, returns the namespace that matches the prefix (if any)
   * as determined by the {@link XmlSchemaProvider#getDefaultPrefix(String, XmlFile)}
   * implementations
   */
  public static @Nullable String getUnboundNamespaceForPrefix(String prefix, XmlFile xmlFile, Set<String> namespaces) {
    final List<XmlSchemaProvider> providers = XmlSchemaProvider.getAvailableProviders(xmlFile);
    for (XmlSchemaProvider provider : providers) {
      for (String namespace : namespaces) {
        if (prefix.equals(provider.getDefaultPrefix(namespace, xmlFile))) {
          return namespace;
        }
      }
    }
    return null;
  }

  private @NlsContexts.PopupTitle String getSelectNSActionTitle() {
    return XmlPsiBundle.message("xml.action.select.namespace.title",
                                StringUtil.capitalize(getXmlNamespaceHelper().getNamespaceAlias(getFile())));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public boolean showHint(final @NotNull Editor editor) {
    XmlToken token = null;
    if (myToken != null) {
      token = (XmlToken)myToken.retrieve();
      if (token == null) return false;
    }
    if (!XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS || myNamespacePrefix.isEmpty()) {
      return false;
    }
    final PsiElement element = myElement.retrieve();
    if (element == null) return false;
    final List<String> namespaces = getNamespaces(element, getFile());
    if (!namespaces.isEmpty()) {
      final String message = ShowAutoImportPass.getMessage(namespaces.size() > 1, XmlPsiBundle.message("xml.terms.namespace.alias"),
                                                           namespaces.iterator().next());
      final String title = getSelectNSActionTitle();
      final ImportNSAction action = new ImportNSAction(namespaces, getFile(), element, editor, title);
      if (element instanceof XmlTag && token != null) {
        if (editor.calculateVisibleRange().contains(token.getTextRange())) {
          HintManager.getInstance().showQuestionHint(editor, message,
                                                     token.getTextOffset(),
                                                     token.getTextOffset() + myNamespacePrefix.length(), action);
          return true;
        }
      }
      else {
        HintManager.getInstance().showQuestionHint(editor, message,
                                                   element.getTextOffset(),
                                                   element.getTextRange().getEndOffset(), action);
        return true;
      }
    }
    return false;
  }

  private static boolean checkIfGivenXmlHasTheseWords(final String name, final XmlFile tldFileByUri) {
    if (name == null || name.isEmpty()) return true;
    final List<String> list = StringUtil.getWordsIn(name);
    final String[] words = ArrayUtilRt.toStringArray(list);
    final boolean[] wordsFound = new boolean[words.length];
    final int[] wordsFoundCount = new int[1];

    IdTableBuilding.ScanWordProcessor wordProcessor = new IdTableBuilding.ScanWordProcessor() {
      @Override
      public void run(final CharSequence chars, char @Nullable [] charsArray, int start, int end) {
        if (wordsFoundCount[0] == words.length) return;
        final int foundWordLen = end - start;

        Next:
        for (int i = 0; i < words.length; ++i) {
          final String localName = words[i];
          if (wordsFound[i] || localName.length() != foundWordLen) continue;

          for (int j = 0; j < localName.length(); ++j) {
            if (chars.charAt(start + j) != localName.charAt(j)) continue Next;
          }

          wordsFound[i] = true;
          wordsFoundCount[0]++;
          break;
        }
      }
    };

    final CharSequence contents = tldFileByUri.getViewProvider().getContents();

    IdTableBuilding.scanWords(wordProcessor, contents, 0, contents.length());

    return wordsFoundCount[0] == words.length;
  }

  public interface StringToAttributeProcessor {
    void doSomethingWithGivenStringToProduceXmlAttributeNowPlease(@NonNls @NotNull String attrName) throws IncorrectOperationException;
  }


  public static void runActionOverSeveralAttributeValuesAfterLettingUserSelectTheNeededOne(final String @NotNull [] namespacesToChooseFrom,
                                                                                           final Project project,
                                                                                           final StringToAttributeProcessor onSelection,
                                                                                           @NlsContexts.PopupTitle String title,
                                                                                           final IntentionAction requestor,
                                                                                           final Editor editor)
    throws IncorrectOperationException {

    if (namespacesToChooseFrom.length > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(List.of(namespacesToChooseFrom))
        .setRenderer(new XmlNSRenderer())
        .setTitle(title)
        .setItemChosenCallback(selectedValue -> {
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          CommandProcessor.getInstance().executeCommand(
            project,
            () -> ApplicationManager.getApplication().runWriteAction(
              () -> {
                try {
                  onSelection.doSomethingWithGivenStringToProduceXmlAttributeNowPlease(selectedValue);
                }
                catch (IncorrectOperationException ex) {
                  throw new RuntimeException(ex);
                }
              }
            ),
            requestor.getText(),
            requestor.getFamilyName()
          );
        })
        .createPopup()
        .showInBestPositionFor(editor);
    }
    else {
      WriteAction.run(() -> {
        String attrName = namespacesToChooseFrom.length == 0 ? "" : namespacesToChooseFrom[0];
        onSelection.doSomethingWithGivenStringToProduceXmlAttributeNowPlease(attrName);
      });
    }
  }

  public static void processExternalUris(final MetaHandler metaHandler,
                                         final PsiFile psiFile,
                                         final ExternalUriProcessor processor) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.run(() -> processExternalUrisImpl(metaHandler, psiFile, processor)),
      XmlPsiBundle.message("xml.progress.finding.acceptable.uri"),
      false,
      psiFile.getProject()
    );
  }

  public interface MetaHandler {
    boolean isAcceptableMetaData(PsiMetaData metadata, final String url);

    String searchFor();
  }

  public static class TagMetaHandler implements MetaHandler {
    private final String myName;


    public TagMetaHandler(final String name) {
      myName = name;
    }

    @Override
    public boolean isAcceptableMetaData(final PsiMetaData metaData, final String url) {
      if (metaData instanceof XmlNSDescriptorImpl nsDescriptor) {

        final XmlElementDescriptor descriptor = nsDescriptor.getElementDescriptor(searchFor(), url);
        return descriptor != null && !(descriptor instanceof AnyXmlElementDescriptor);
      }
      return false;
    }

    @Override
    public String searchFor() {
      return myName;
    }
  }

  private static void processExternalUrisImpl(final MetaHandler metaHandler,
                                              final PsiFile psiFile,
                                              final ExternalUriProcessor processor) {
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

    final String searchFor = metaHandler.searchFor();

    if (pi != null) {
      pi.setText(XmlPsiBundle.message("xml.progress.looking.in.schemas"));
      pi.setIndeterminate(false);
    }
    final ExternalResourceManager instanceEx = ExternalResourceManager.getInstance();
    final String[] availableUrls = instanceEx.getResourceUrls(null, true);
    int i = 0;

    for (@NlsSafe String url : availableUrls) {
      if (pi != null) {
        pi.setFraction((double)i / availableUrls.length);
        pi.setText2(url);
        ++i;
      }
      final XmlFile xmlFile = XmlUtil.findNamespace(psiFile, url);

      if (xmlFile != null) {
        final boolean wordFound = checkIfGivenXmlHasTheseWords(searchFor, xmlFile);
        if (!wordFound) continue;
        final XmlDocument document = xmlFile.getDocument();
        assert document != null;
        final PsiMetaData metaData = document.getMetaData();

        if (metaHandler.isAcceptableMetaData(metaData, url)) {
          final XmlNSDescriptorImpl descriptor = metaData instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)metaData : null;
          final String defaultNamespace = descriptor != null ? descriptor.getDefaultNamespace() : url;

          // Skip rare stuff
          if (!XmlUtil.XML_SCHEMA_URI2.equals(defaultNamespace) && !XmlUtil.XML_SCHEMA_URI3.equals(defaultNamespace)) {
            processor.process(defaultNamespace, url);
          }
        }
      }
    }
  }

  public interface ExternalUriProcessor {
    void process(@NotNull String uri, final @Nullable String url);
  }

  private class MyStringToAttributeProcessor implements StringToAttributeProcessor {
    private final @NotNull PsiElement myElement;
    private final @NotNull Project myProject;
    private final @Nullable Editor myEditor;
    private final @NotNull XmlFile myXmlFile;

    private MyStringToAttributeProcessor(@NotNull PsiElement element,
                                         @NotNull Project project,
                                         @Nullable Editor editor,
                                         @NotNull XmlFile xmlFile) {
      myElement = element;
      myProject = project;
      myEditor = editor;
      myXmlFile = xmlFile;
    }

    @Override
    public void doSomethingWithGivenStringToProduceXmlAttributeNowPlease(final @NotNull String namespace)
      throws IncorrectOperationException {
      String prefix = myNamespacePrefix;
      if (StringUtil.isEmpty(prefix)) {
        final XmlFile xmlFile = XmlExtension.getExtension(myXmlFile).getContainingFile(myElement);
        prefix = ExtendedTagInsertHandler.getPrefixByNamespace(xmlFile, namespace);
        if (StringUtil.isNotEmpty(prefix)) {
          // namespace already declared
          ExtendedTagInsertHandler.qualifyWithPrefix(prefix, myElement);
          return;
        }
        else {
          prefix = ExtendedTagInsertHandler.suggestPrefix(xmlFile, namespace);
          if (!StringUtil.isEmpty(prefix)) {
            ExtendedTagInsertHandler.qualifyWithPrefix(prefix, myElement);
            if (myEditor != null) {
              PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
            }
          }
        }
      }
      final RangeMarker marker;
      if (myEditor != null) {
        final int offset = myEditor.getCaretModel().getOffset();
        marker = myEditor.getDocument().createRangeMarker(offset, offset);
      }
      else {
        marker = null;
      }
      final XmlNamespaceHelper helper = XmlNamespaceHelper.getHelper(myXmlFile);
      helper.insertNamespaceDeclaration(myXmlFile, myEditor, Collections.singleton(namespace), prefix, __ -> {
        if (myEditor != null && !namespace.isEmpty()) {
          myEditor.getCaretModel().moveToOffset(marker.getStartOffset());
        }
      });
    }
  }
}
