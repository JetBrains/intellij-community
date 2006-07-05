package com.intellij.uiDesigner.inspections;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.JDOMUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class FormFileErrorCollector extends FormErrorCollector {
  private InspectionManager myManager;
  private PsiFile myFile;
  private List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();

  public FormFileErrorCollector(final PsiFile file, final InspectionManager manager) {
    myManager = manager;
    myFile = file;
  }

  public void addError(final String inspectionId,
                       @Nullable IProperty prop,
                       @NotNull String errorMessage,
                       @Nullable EditorQuickFixProvider editorQuickFixProvider) {
    myProblems.add(myManager.createProblemDescriptor(myFile, JDOMUtil.escapeText(errorMessage),
                                                     (LocalQuickFix)null,
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
  }

  public ProblemDescriptor[] result() {
    return myProblems.toArray(new ProblemDescriptor[myProblems.size()]);
  }
}
