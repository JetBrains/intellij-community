// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.refactorings.introduce;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector;

import java.util.*;

public class IntroducePropertyAction extends BaseRefactoringAction {
  private static final String PREFIX = "${";
  private static final String SUFFIX = "}";

  public IntroducePropertyAction() {
    setInjectedContext(true);
  }

  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return false;
  }

  @Override
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
  static Pair<XmlElement, TextRange> getSelectedElementAndTextRange(Editor editor, final PsiFile file) {
    final int startOffset = editor.getSelectionModel().getSelectionStart();
    final int endOffset = editor.getSelectionModel().getSelectionEnd();

    final PsiElement elementAtStart = file.findElementAt(startOffset);
    if (elementAtStart == null) return null;
    final PsiElement elementAtEnd = file.findElementAt(endOffset == startOffset ? endOffset : endOffset - 1);
    if (elementAtEnd == null) return null;

    PsiElement elementAt = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
    if (elementAt instanceof XmlToken) elementAt = elementAt.getParent();

    if (elementAt instanceof XmlText || elementAt instanceof XmlAttributeValue) {
      TextRange range = editor.getSelectionModel().hasSelection() ? new TextRange(startOffset, endOffset) : elementAt.getTextRange();

      return Pair.create((XmlElement)elementAt, range);
    }

    return null;
  }

  private static class MyRefactoringActionHandler implements RefactoringActionHandler {
    @Override
    public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
      MavenActionsUsagesCollector.trigger(project, MavenActionsUsagesCollector.INTRODUCE_PROPERTY);
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
      final MavenDomProjectModel selectedProject = dialog.getSelectedProject();

      if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(getFiles(file, selectedProject)).hasReadonlyFiles()) {
        return;
      }

      final String replaceWith = PREFIX + propertyName + SUFFIX;
      WriteCommandAction.runWriteCommandAction(project, () -> {
        editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), replaceWith);
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        createMavenProperty(selectedProject, propertyName, selectedString);

        PsiDocumentManager.getInstance(project).commitAllDocuments();
      });

      showFindUsages(project, propertyName, selectedString, replaceWith, selectedProject);
    }

    @NotNull
    private static List<VirtualFile> getFiles(PsiFile file, MavenDomProjectModel model) {
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

      return new ArrayList<>(virtualFiles);
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
      final FindModel findModelCopy = findModel.clone();

      ReplaceInProjectManager.getInstance(project)
        .searchAndShowUsages(manager, new MyUsageSearcherFactory(model, propertyName, selectedString), findModelCopy, presentation,
                             processPresentation
        );
    }

    //IDEA-54113
    private static void assureFindToolWindowRegistered(@NotNull Project project) {
      UsageViewContentManager uvm = UsageViewContentManager.getInstance(project);
    }

    private static FindModel createFindModel(FindManager findManager, String selectedString, String replaceWith) {
      FindModel findModel = findManager.getFindInProjectModel().clone();

      findModel.setStringToFind(selectedString);
      findModel.setStringToReplace(replaceWith);
      findModel.setReplaceState(true);
      findModel.setPromptOnReplace(true);
      findModel.setCaseSensitive(true);
      findModel.setRegularExpressions(false);

      return findModel;
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    }

    private static class MyUsageSearcherFactory implements Factory<UsageSearcher> {
      private final MavenDomProjectModel myModel;
      private final String myPropertyName;
      private final String mySelectedString;

      MyUsageSearcherFactory(MavenDomProjectModel model, String propertyName, String selectedString) {
        myModel = model;
        myPropertyName = propertyName;
        mySelectedString = selectedString;
      }

      @Override
      public UsageSearcher create() {
        return new UsageSearcher() {
          final Set<UsageInfo> usages = new HashSet<>();

          @Override
          public void generate(@NotNull final Processor<? super Usage> processor) {
            ApplicationManager.getApplication().runReadAction(() -> {
              collectUsages(myModel);
              for (MavenDomProjectModel model : MavenDomProjectProcessorUtils.getChildrenProjects(myModel)) {
                collectUsages(model);
              }

              for (UsageInfo usage : usages) {
                processor.process(UsageInfo2UsageAdapter.CONVERTER.fun(usage));
              }
            });
          }

          private void collectUsages(@NotNull MavenDomProjectModel model) {
            if (model.isValid()) {
              final XmlElement root = model.getXmlElement();
              if (root != null) {
                root.acceptChildren(new XmlElementVisitor() {

                  @Override
                  public void visitXmlText(@NotNull XmlText text) {
                    XmlTag xmlTag = PsiTreeUtil.getParentOfType(text, XmlTag.class);
                    if (xmlTag != null && !xmlTag.getName().equals(myPropertyName)) {
                      usages.addAll(getUsages(text));
                    }
                  }

                  @Override
                  public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
                    XmlTag xmlTag = PsiTreeUtil.getParentOfType(value, XmlTag.class);
                    if (xmlTag != null && !xmlTag.equals(root)) {
                      usages.addAll(getUsages(value));
                    }
                  }

                  @Override
                  public void visitXmlElement(@NotNull XmlElement element) {
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
