package com.intellij.uiDesigner.inspections;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.make.FormElementNavigatable;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.JDOMUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class FormFileErrorCollector extends FormErrorCollector {
  private final InspectionManager myManager;
  private final PsiFile myFile;
  private final List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();

  public FormFileErrorCollector(final PsiFile file, final InspectionManager manager) {
    myManager = manager;
    myFile = file;
  }

  public void addError(final String inspectionId, final IComponent component, @Nullable IProperty prop,
                       @NotNull String errorMessage,
                       @Nullable EditorQuickFixProvider editorQuickFixProvider) {
    final ProblemDescriptor problemDescriptor = myManager.createProblemDescriptor(myFile, JDOMUtil.escapeText(errorMessage),
                                                                                  (LocalQuickFix)null,
                                                                                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    if (problemDescriptor instanceof ProblemDescriptorImpl && component != null) {
      FormElementNavigatable navigatable = new FormElementNavigatable(myFile.getProject(), myFile.getVirtualFile(),
                                                                      component.getId());
      ((ProblemDescriptorImpl) problemDescriptor).setNavigatable(navigatable);
    }
    myProblems.add(problemDescriptor);
  }

  public ProblemDescriptor[] result() {
    return myProblems.toArray(new ProblemDescriptor[myProblems.size()]);
  }
}
