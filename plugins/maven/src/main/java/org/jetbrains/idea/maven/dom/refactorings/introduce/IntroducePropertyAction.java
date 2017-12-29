/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.refactorings.introduce;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;

import java.util.*;

public class IntroducePropertyAction extends BaseRefactoringAction {
  private static final String PREFIX = "${";
  private static final String SUFFIX = "}";

  public IntroducePropertyAction() {
    setInjectedContext(true);
  }

  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new MyRefactoringActionHandler();
  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    return MavenDomUtil.isMavenFile(file)
           && virtualFile != null
           && virtualFile.getFileSystem() != JarFileSystem.getInstance();
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    if (!super.isAvailableOnElementInEditorAndFile(element, editor, file, context)) return false;
    return getSelectedElementAndTextRange(editor, file) != null;
  }

  @Nullable
  public static Pair<XmlElement, TextRange> getSelectedElementAndTextRange(Editor editor, final PsiFile file) {
    final int startOffset = editor.getSelectionModel().getSelectionStart();
    final int endOffset = editor.getSelectionModel().getSelectionEnd();

    final PsiElement elementAtStart = file.findElementAt(startOffset);
    if (elementAtStart == null) return null;
    final PsiElement elementAtEnd = file.findElementAt(endOffset == startOffset ? endOffset : endOffset - 1);
    if (elementAtEnd == null) return null;

    PsiElement elementAt = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
    if (elementAt instanceof XmlToken) elementAt = elementAt.getParent();

    if (elementAt instanceof XmlText || elementAt instanceof XmlAttributeValue) {
      TextRange range;

      if (editor.getSelectionModel().hasSelection()) {
        range = new TextRange(startOffset, endOffset);
      }
      else {
        range = elementAt.getTextRange();
      }

      return Pair.create((XmlElement)elementAt, range);
    }

    return null;
  }

  private static class MyRefactoringActionHandler implements RefactoringActionHandler {
    public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      Pair<XmlElement, TextRange> elementAndRange = getSelectedElementAndTextRange(editor, file);
      if (elementAndRange == null) return;

      XmlElement selectedElement = elementAndRange.first;
      final TextRange range = elementAndRange.second;

      String stringValue = selectedElement.getText();
      if (stringValue == null) return;

      final MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class);
      final String selectedString = editor.getDocument().getText(range);

      List<TextRange> ranges = getPropertiesTextRanges(stringValue);
      int offsetInElement = range.getStartOffset() - selectedElement.getTextOffset();

      if (model == null ||
          StringUtil.isEmptyOrSpaces(selectedString) ||
          isIntersectWithRanges(ranges, offsetInElement, offsetInElement + selectedString.length())) {
        return;
      }

      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());

      IntroducePropertyDialog dialog = new IntroducePropertyDialog(project, selectedElement, model, selectedString);
      dialog.show();
      if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;

      final String propertyName = dialog.getEnteredName();
      final String replaceWith = PREFIX + propertyName + SUFFIX;
      final MavenDomProjectModel selectedProject = dialog.getSelectedProject();

      if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(getFiles(file, selectedProject)).hasReadonlyFiles()) {
        return;
      }

      new WriteCommandAction(project) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), replaceWith);
          PsiDocumentManager.getInstance(project).commitAllDocuments();

          createMavenProperty(selectedProject, propertyName, selectedString);

          PsiDocumentManager.getInstance(project).commitAllDocuments();
        }
      }.execute();

      showFindUsages(project, propertyName, selectedString, replaceWith, selectedProject);
    }

    private static VirtualFile[] getFiles(PsiFile file, MavenDomProjectModel model) {
      Set<VirtualFile> virtualFiles = new HashSet<>();
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        virtualFiles.add(virtualFile);
      }

      XmlElement xmlElement = model.getXmlElement();
      if (xmlElement != null) {
        VirtualFile vf = xmlElement.getContainingFile().getVirtualFile();
        if (vf != null) virtualFiles.add(vf);
      }

      return VfsUtil.toVirtualFileArray(virtualFiles);
    }

    private static void createMavenProperty(@NotNull MavenDomProjectModel model,
                                            @NotNull String enteredName,
                                            @NotNull String selectedString) {
      MavenDomProperties mavenDomProperties = model.getProperties();
      XmlTag xmlTag = mavenDomProperties.ensureTagExists();

      XmlTag propertyTag = xmlTag.createChildTag(enteredName, xmlTag.getNamespace(), selectedString, false);

      xmlTag.add(propertyTag);
    }

    private static void showFindUsages(@NotNull Project project,
                                       @NotNull String propertyName,
                                       @NotNull String selectedString,
                                       @NotNull String replaceWith,
                                       @NotNull MavenDomProjectModel model) {
      UsageViewManager manager = UsageViewManager.getInstance(project);
      if (manager == null) return;

      assureFindToolWindowRegistered(project);

      FindManager findManager = FindManager.getInstance(project);
      FindModel findModel = createFindModel(findManager, selectedString, replaceWith);

      final UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(true, findModel);
      final FindUsagesProcessPresentation processPresentation = FindInProjectUtil.setupProcessPresentation(project, true, presentation);

      findManager.getFindInProjectModel().copyFrom(findModel);
      final FindModel findModelCopy = (FindModel)findModel.clone();

      ReplaceInProjectManager.getInstance(project)
        .searchAndShowUsages(manager, new MyUsageSearcherFactory(model, propertyName, selectedString), findModelCopy, presentation,
                             processPresentation,
                             findManager);
    }

    //IDEA-54113
    private static void assureFindToolWindowRegistered(@NotNull Project project) {
      com.intellij.usageView.UsageViewManager uvm = com.intellij.usageView.UsageViewManager.getInstance(project);
    }

    private static FindModel createFindModel(FindManager findManager, String selectedString, String replaceWith) {
      FindModel findModel = (FindModel)findManager.getFindInProjectModel().clone();

      findModel.setStringToFind(selectedString);
      findModel.setStringToReplace(replaceWith);
      findModel.setReplaceState(true);
      findModel.setPromptOnReplace(true);
      findModel.setCaseSensitive(true);
      findModel.setRegularExpressions(false);

      return findModel;
    }

    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    }

    private static class MyUsageSearcherFactory implements Factory<UsageSearcher> {
      private final MavenDomProjectModel myModel;
      private final String myPropertyName;
      private final String mySelectedString;

      public MyUsageSearcherFactory(MavenDomProjectModel model, String propertyName, String selectedString) {
        myModel = model;
        myPropertyName = propertyName;
        mySelectedString = selectedString;
      }

      public UsageSearcher create() {
        return new UsageSearcher() {
          Set<UsageInfo> usages = new HashSet<>();

          @Override
          public void generate(@NotNull final Processor<Usage> processor) {
            AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();

            try {
              collectUsages(myModel);
              for (MavenDomProjectModel model : MavenDomProjectProcessorUtils.getChildrenProjects(myModel)) {
                collectUsages(model);
              }

              for (UsageInfo usage : usages) {
                processor.process(UsageInfo2UsageAdapter.CONVERTER.fun(usage));
              }
            }
            finally {
              accessToken.finish();
            }
          }

          private void collectUsages(@NotNull MavenDomProjectModel model) {
            if (model.isValid()) {
              final XmlElement root = model.getXmlElement();
              if (root != null) {
                root.acceptChildren(new XmlElementVisitor() {

                  @Override
                  public void visitXmlText(XmlText text) {
                    XmlTag xmlTag = PsiTreeUtil.getParentOfType(text, XmlTag.class);
                    if (xmlTag != null && !xmlTag.getName().equals(myPropertyName)) {
                      usages.addAll(getUsages(text));
                    }
                  }

                  @Override
                  public void visitXmlAttributeValue(XmlAttributeValue value) {
                    XmlTag xmlTag = PsiTreeUtil.getParentOfType(value, XmlTag.class);
                    if (xmlTag != null && !xmlTag.equals(root)) {
                      usages.addAll(getUsages(value));
                    }
                  }

                  @Override
                  public void visitXmlElement(XmlElement element) {
                    element.acceptChildren(this);
                  }
                });
              }
            }
          }

          @NotNull
          private Set<UsageInfo> getUsages(@NotNull XmlElement xmlElement) {
            String s = xmlElement.getText();
            if (StringUtil.isEmptyOrSpaces(s)) return Collections.emptySet();

            int start = s.indexOf(mySelectedString);
            if (start == -1) return Collections.emptySet();

            Set<UsageInfo> usages = new HashSet<>();

            List<TextRange> ranges = getPropertiesTextRanges(s);
            TextRange elementTextRange = xmlElement.getTextRange();
            PsiFile containingFile = xmlElement.getContainingFile();

            do {
              int end = start + mySelectedString.length();
              boolean isInsideProperty = isIntersectWithRanges(ranges, start, end);
              if (!isInsideProperty) {
                usages
                  .add(new UsageInfo(containingFile, elementTextRange.getStartOffset() + start, elementTextRange.getStartOffset() + end));
              }
              start = s.indexOf(mySelectedString, end);
            }
            while (start != -1);

            return usages;
          }
        };
      }
    }
  }

  private static List<TextRange> getPropertiesTextRanges(String s) {
    List<TextRange> ranges = new ArrayList<>();
    int startOffset = s.indexOf(PREFIX);
    while (startOffset >= 0) {
      int endOffset = s.indexOf(SUFFIX, startOffset);
      if (endOffset > startOffset) {
        if (s.substring(startOffset + PREFIX.length(), endOffset).contains(PREFIX)) {
          startOffset = s.indexOf(PREFIX, startOffset + 1);
        }
        else {
          ranges.add(new TextRange(startOffset, endOffset));
          startOffset = s.indexOf(PREFIX, endOffset);
        }
      }
      else {
        break;
      }
    }

    return ranges;
  }

  private static boolean isIntersectWithRanges(@NotNull Collection<TextRange> ranges, int start, int end) {
    for (TextRange range : ranges) {
      if (start <= range.getStartOffset() && end >= range.getEndOffset()) {
        continue; // range is inside [start, end]
      }

      if (end <= range.getStartOffset()) {
        continue; // range is on right
      }

      if (start >= range.getEndOffset()) {
        continue; // range is on right
      }

      return true;
    }
    return false;
  }
}
