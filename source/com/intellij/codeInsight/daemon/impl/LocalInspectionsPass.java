package com.intellij.codeInsight.daemon.impl;

import com.intellij.application.options.ErrorHighlightingOptions;
import com.intellij.application.options.ErrorHighlightingPanel;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import java.util.*;

/**
 * @author max
 */
public class LocalInspectionsPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LocalInspectionsPass");

  private Project myProject;
  private PsiFile myFile;
  private Document myDocument;
  private int myStartOffset;
  private int myEndOffset;
  private List<ProblemDescriptor> myDescriptors = Collections.EMPTY_LIST;
  private List<HighlightInfoType> myLevels = Collections.EMPTY_LIST;
  private List<LocalInspectionTool> myTools = Collections.EMPTY_LIST;
  private static final Class[] CHECKABLE = new Class[]{PsiMethod.class, PsiField.class, PsiClass.class};

  public LocalInspectionsPass(Project project,
                              PsiFile file,
                              Document document,
                              int startOffset,
                              int endOffset) {
    super(document);
    myProject = project;
    myFile = file;
    myDocument = document;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    InspectionManagerEx iManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);

    final PsiElement[] psiRoots = myFile.getPsiRoots();
    for (int j = 0; j < psiRoots.length; j++) {
      final PsiElement psiRoot = psiRoots[j];

      PsiElement[] elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      Set<PsiElement> workSet = new THashSet<PsiElement>();
      for (int i = 0; i < elements.length; i++) {
        ProgressManager.getInstance().checkCanceled();

        PsiElement element = elements[i];
        element = PsiTreeUtil.getParentOfType(element, CHECKABLE, false);
        while (element != null) {
          if (!workSet.add(element)) break;
          element = PsiTreeUtil.getParentOfType(element, CHECKABLE, true);
        }
      }

      myDescriptors = new ArrayList<ProblemDescriptor>();
      myLevels = new ArrayList<HighlightInfoType>();
      myTools = new ArrayList<LocalInspectionTool>();

      LocalInspectionTool[] tools = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().getHighlightingLocalInspectionTools();
      for (Iterator iterator = workSet.iterator(); iterator.hasNext();) {
        ProgressManager.getInstance().checkCanceled();
        LocalInspectionTool currentTool = null;
        try {
          PsiElement element = (PsiElement)iterator.next();
          if (element instanceof PsiMethod) {
            PsiMethod psiMethod = (PsiMethod)element;
            for (int k = 0; k < tools.length; k++) {
              currentTool = tools[k];
              if (iManager.isToCheckMember(psiMethod, currentTool.getID())) {
                appendDescriptors(currentTool.checkMethod(psiMethod, iManager, true), currentTool);
              }
            }
          }
          else if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
            PsiClass psiClass = (PsiClass)element;
            for (int k = 0; k < tools.length; k++) {
              currentTool = tools[k];
              if (iManager.isToCheckMember(psiClass, currentTool.getID())) {
                appendDescriptors(currentTool.checkClass(psiClass, iManager, true), currentTool);
              }
            }
          }
          else if (element instanceof PsiField) {
            PsiField psiField = (PsiField)element;
            for (int k = 0; k < tools.length; k++) {
              currentTool = tools[k];
              if (iManager.isToCheckMember(psiField, currentTool.getID())) {
                appendDescriptors(currentTool.checkField(psiField, iManager, true), currentTool);
              }
            }
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          if (currentTool != null) {
            LOG.error("Exception happened in local inspection tool: " + currentTool.getDisplayName(), e);
          }
          else {
            LOG.error(e);
          }
        }
      }
    }
  }

  private void appendDescriptors(ProblemDescriptor[] problemDescriptors, LocalInspectionTool tool) {
    ProgressManager.getInstance().checkCanceled();

    InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    if (problemDescriptors != null) {
      boolean isError = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().getErrorLevel(
        HighlightDisplayKey.find(tool.getShortName())) ==
                        HighlightDisplayLevel.ERROR;
      for (int i = 0; i < problemDescriptors.length; i++) {
        ProblemDescriptor problemDescriptor = problemDescriptors[i];
        if (!manager.inspectionResultSuppressed(problemDescriptor.getPsiElement(), tool)) {
          myDescriptors.add(problemDescriptor);
          ProblemHighlightType highlightType = problemDescriptor.getHighlightType();
          HighlightInfoType type = null;
          if (highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
            type = isError ? HighlightInfoType.ERROR : HighlightInfoType.WARNING;
          }
          else if (highlightType == ProblemHighlightType.LIKE_DEPRECATED) {
            type = HighlightInfoType.DEPRECATED;
          }
          else if (highlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) {
            type = HighlightInfoType.WRONG_REF;
          }
          else if (highlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
            type = HighlightInfoType.UNUSED_SYMBOL;
          }


          myLevels.add(type);
          myTools.add(tool);
        }
      }
    }
  }

  public void doApplyInformationToEditor() {
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>(myDescriptors.size());
    for (int i = 0; i < myDescriptors.size(); i++) {
      ProblemDescriptor descriptor = myDescriptors.get(i);
      final LocalInspectionTool tool = myTools.get(i);
      //TODO
      PsiElement psiElement = descriptor.getPsiElement();
      String message = renderDescriptionMessage(descriptor);
      HighlightInfoType level = myLevels.get(i);

      final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      final InspectionProfileImpl inspectionProfile = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile();
      if (!inspectionProfile.isToolEnabled(key)) continue;
      boolean isError = inspectionProfile.getErrorLevel(key) == HighlightDisplayLevel.ERROR;

      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(level, psiElement, message, message);
      infos.add(highlightInfo);
      highlightInfo.severity = isError ? HighlightInfo.ERROR : HighlightInfo.WARNING;
      if (descriptor.getFix() != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new QuickFixWrapper(descriptor));
      }
      QuickFixAction.registerQuickFixAction(highlightInfo, new SwitchOffToolAction(tool));
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddNoInspectionCommentAction(tool, psiElement));
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddNoInspectionDocTagAction(tool, psiElement));
    }

    final HighlightInfo[] array = infos.toArray(new HighlightInfo[infos.size()]);
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, array,
                                                   UpdateHighlightersUtil.INSPECTION_HIGHLIGHTERS_GROUP);
    myDescriptors = Collections.EMPTY_LIST;
    myLevels = Collections.EMPTY_LIST;
    myTools = Collections.EMPTY_LIST;

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, FileStatusMap.LOCAL_INSPECTIONS);

    ErrorStripeRenderer renderer = new RefreshStatusRenderer(myProject, daemonCodeAnalyzer, myDocument, myFile);
    Editor[] editors = EditorFactory.getInstance().getEditors(myDocument, myProject);
    for (int i = 0; i < editors.length; i++) {
      Editor editor = editors[i];
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(renderer);
    }
  }

  public int getPassId() {
    return Pass.LOCAL_INSPECTIONS;
  }

  private static String renderDescriptionMessage(ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    String message = descriptor.getDescriptionTemplate();
    if (message == null) return "Error message unavaliable";
    message = StringUtil.replace(message, "<code>", "'");
    message = StringUtil.replace(message, "</code>", "'");
    message = message.replaceAll("<[^>]*>", "");
    message = StringUtil.replace(message, "#ref", psiElement.getText());
    message = StringUtil.replace(message, "#loc", "");
    return message;
  }

  private static class SwitchOffToolAction implements IntentionAction {
    private final LocalInspectionTool myTool;

    public SwitchOffToolAction(LocalInspectionTool tool) {
      myTool = tool;
    }

    public String getText() {
      return "Edit options of \"" + myTool.getDisplayName() + "\"";
    }

    public String getFamilyName() {
      return "Edit options of reporter inspection";
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      Runnable selectTool = new Runnable() {
        public void run() {
          final ErrorHighlightingPanel panel = ErrorHighlightingOptions.getInstance().getPanel();
          panel.selectInspectionTool(myTool.getShortName());
        }
      };

      ShowSettingsUtil.getInstance().editConfigurable(project, ErrorHighlightingOptions.getInstance(), selectTool);
    }

    public boolean startInWriteAction() {
      return false;
    }
  }
}
