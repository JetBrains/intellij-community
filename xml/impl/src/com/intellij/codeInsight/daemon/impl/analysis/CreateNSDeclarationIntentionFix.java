/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.application.options.XmlSettings;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.ExtendedTagInsertHandler;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class CreateNSDeclarationIntentionFix implements HintAction, LocalQuickFix {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix");

  private final String myNamespacePrefix;
  private final PsiElement myElement;
  private final XmlFile myFile;
  private final XmlToken myToken;

  public CreateNSDeclarationIntentionFix(@NotNull final PsiElement element,
                                         @NotNull final String namespacePrefix
                                         ) {
    this(element, namespacePrefix, null);
  }

  public CreateNSDeclarationIntentionFix(final PsiElement element, final String namespacePrefix, final XmlToken token) {
    myNamespacePrefix = namespacePrefix;
    myElement = element;
    myFile = (XmlFile)element.getContainingFile();
    myToken = token;
  }

  @NotNull
  public String getText() {
    final String alias = StringUtil.capitalize(XmlExtension.getExtension(myFile).getNamespaceAlias(myFile));
    return XmlErrorMessages.message("create.namespace.declaration.quickfix", alias);
  }

  @NotNull
  public String getName() {
    return getFamilyName();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiFile containingFile = descriptor.getPsiElement().getContainingFile();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    final PsiFile file = editor != null ? PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()):null;
    if (file == null || file.getVirtualFile() != containingFile.getVirtualFile()) return;

    try { invoke(project, editor, containingFile); } catch (IncorrectOperationException ex) {
      LOG.error(ex);
    }
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myElement.isValid();
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!myElement.isValid() || !CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final Set<String> set = XmlExtension.getExtension(file).guessUnboundNamespaces(myElement, (XmlFile)file);
    final String[] namespaces = ArrayUtil.toStringArray(set);
    Arrays.sort(namespaces);

    runActionOverSeveralAttributeValuesAfterLettingUserSelectTheNeededOne(
      namespaces,
      project,
      new StringToAttributeProcessor() {
        public void doSomethingWithGivenStringToProduceXmlAttributeNowPlease(@NotNull final String namespace) throws IncorrectOperationException {
          String prefix = myNamespacePrefix;
          if (StringUtil.isEmpty(prefix)) {
            final XmlExtension extension = XmlExtension.getExtension(myFile);
            final XmlFile xmlFile = extension.getContainingFile(myElement);
            prefix = ExtendedTagInsertHandler.getPrefixByNamespace(xmlFile, namespace);
            if (StringUtil.isNotEmpty(prefix)) {
              ExtendedTagInsertHandler.qualifyWithPrefix(prefix, myElement);
              return;
            } else {
              prefix = ExtendedTagInsertHandler.suggestPrefix(xmlFile, namespace);
              if (StringUtil.isNotEmpty(prefix)) {
                ExtendedTagInsertHandler.qualifyWithPrefix(prefix, myElement);
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
              }
            }
          }
          final int offset = editor.getCaretModel().getOffset();
          final RangeMarker marker = editor.getDocument().createRangeMarker(offset, offset);
          final XmlExtension extension = XmlExtension.getExtension(file);
          extension.insertNamespaceDeclaration((XmlFile)file, editor, Collections.singleton(namespace), prefix, new XmlExtension.Runner<String, IncorrectOperationException>() {
            public void run(final String param) throws IncorrectOperationException {
              if (namespace.length() > 0) {
                editor.getCaretModel().moveToOffset(marker.getStartOffset());
              }
            }
          });
        }
      }, getTitle(),
      this,
      editor);
  }

  private String getTitle() {
    return XmlErrorMessages.message("select.namespace.title", StringUtil.capitalize(XmlExtension.getExtension(myFile).getNamespaceAlias(myFile)));
  }

  public boolean startInWriteAction() {
    return true;
  }

  public boolean showHint(final Editor editor) {
    if (!XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS || !myElement.isValid() || myNamespacePrefix.length() == 0) {
      return false;
    }
    final Set<String> namespaces = XmlExtension.getExtension(myFile).guessUnboundNamespaces(myElement, myFile);
    if (!namespaces.isEmpty()) {
      final String message = ShowAutoImportPass.getMessage(namespaces.size() > 1, namespaces.iterator().next());
      final String title = getTitle();
      final ImportNSAction action = new ImportNSAction(namespaces, myFile, myElement, editor, title);
      if (myElement instanceof XmlTag) {
        if (ShowAutoImportPass.getVisibleRange(editor).contains(myToken.getTextRange())) {
          HintManager.getInstance().showQuestionHint(editor, message,
                                                     myToken.getTextOffset(),
                                                     myToken.getTextOffset() + myNamespacePrefix.length(), action);
          return true;        
        }
      } else {
        HintManager.getInstance().showQuestionHint(editor, message,
                                                   myElement.getTextOffset(),
                                                   myElement.getTextRange().getEndOffset(), action);
        return true;
      }
    }
    return false;
  }

  private static boolean checkIfGivenXmlHasTheseWords(final String name, final XmlFile tldFileByUri) {
    if (name == null || name.length() == 0) return true;
    final List<String> list = StringUtil.getWordsIn(name);
    final String[] words = ArrayUtil.toStringArray(list);
    final boolean[] wordsFound = new boolean[words.length];
    final int[] wordsFoundCount = new int[1];

    IdTableBuilding.ScanWordProcessor wordProcessor = new IdTableBuilding.ScanWordProcessor() {
      public void run(final CharSequence chars, int start, int end) {
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


  public static void runActionOverSeveralAttributeValuesAfterLettingUserSelectTheNeededOne(final @NotNull String[] namespacesToChooseFrom,
                                                                                           final Project project, final StringToAttributeProcessor onSelection,
                                                                                           String title,
                                                                                           final IntentionAction requestor,
                                                                                           final Editor editor) throws IncorrectOperationException {
    
    if (namespacesToChooseFrom.length > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      final JList list = new JBList(namespacesToChooseFrom);
      list.setCellRenderer(XmlNSRenderer.INSTANCE);
      Runnable runnable = new Runnable() {
        public void run() {
          final int index = list.getSelectedIndex();
          if (index < 0) return;
          PsiDocumentManager.getInstance(project).commitAllDocuments();

          CommandProcessor.getInstance().executeCommand(
            project,
            new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runWriteAction(
                  new Runnable() {
                    public void run() {
                      try {
                        onSelection.doSomethingWithGivenStringToProduceXmlAttributeNowPlease(namespacesToChooseFrom[index]);
                      } catch (IncorrectOperationException ex) {
                        throw new RuntimeException(ex);
                      }
                    }
                  }
                );
              }
            },
            requestor.getText(),
            requestor.getFamilyName()
          );
        }
      };

      new PopupChooserBuilder(list).
        setTitle(title).
        setItemChoosenCallback(runnable).
        createPopup().
        showInBestPositionFor(editor);
    } else {
      onSelection.doSomethingWithGivenStringToProduceXmlAttributeNowPlease(namespacesToChooseFrom.length == 0 ? "" : namespacesToChooseFrom[0]);
    }
  }

  public static void processExternalUris(final MetaHandler metaHandler,
                                         final PsiFile file,
                                         final ExternalUriProcessor processor,
                                         final boolean showProgress) {
    if (!showProgress || ApplicationManager.getApplication().isUnitTestMode()) {
      processExternalUrisImpl(metaHandler, file, processor);
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        new Runnable() {
          public void run() {
            processExternalUrisImpl(metaHandler, file, processor);
          }
        },
        XmlErrorMessages.message("finding.acceptable.uri"),
        false,
        file.getProject()
      );
    }
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

    public boolean isAcceptableMetaData(final PsiMetaData metaData, final String url) {
      if (metaData instanceof XmlNSDescriptorImpl) {
        final XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl)metaData;

        final XmlElementDescriptor descriptor = nsDescriptor.getElementDescriptor(searchFor(), url);
        return descriptor != null && !(descriptor instanceof AnyXmlElementDescriptor);
      }
      return false;
    }

    public String searchFor() {
      return myName;
    }
  }

  private static void processExternalUrisImpl(final MetaHandler metaHandler,
                                              final PsiFile file,
                                              final ExternalUriProcessor processor) {
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

    final String searchFor = metaHandler.searchFor();

    if (pi != null) pi.setText(XmlErrorMessages.message("looking.in.schemas"));
    final ExternalResourceManager instanceEx = ExternalResourceManager.getInstance();
    final String[] availableUrls = instanceEx.getResourceUrls(null, true);
    int i = 0;

    for (String url : availableUrls) {
      if (pi != null) {
        pi.setFraction((double)i / availableUrls.length);
        pi.setText2(url);
        ++i;
      }
      final XmlFile xmlFile = XmlUtil.findNamespace(file, url);

      if (xmlFile != null) {
        final boolean wordFound = checkIfGivenXmlHasTheseWords(searchFor, xmlFile);
        if (!wordFound) continue;
        final XmlDocument document = xmlFile.getDocument();
        assert document != null;
        final PsiMetaData metaData = document.getMetaData();

        if (metaHandler.isAcceptableMetaData(metaData, url)) {
          final XmlNSDescriptorImpl descriptor = metaData instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)metaData:null;
          final String defaultNamespace = descriptor != null ? descriptor.getDefaultNamespace():url;

          // Skip rare stuff
          if (!XmlUtil.XML_SCHEMA_URI2.equals(defaultNamespace) && !XmlUtil.XML_SCHEMA_URI3.equals(defaultNamespace)) {
            processor.process(defaultNamespace, url);
          }
        }
      }
    }
  }

  public interface ExternalUriProcessor {
    void process(@NotNull String uri,@Nullable final String url);
  }
}
