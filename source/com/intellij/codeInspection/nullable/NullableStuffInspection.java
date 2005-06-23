package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.AnnotateMethodFix;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiSuperMethodUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class NullableStuffInspection extends BaseLocalInspectionTool {
  public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;

  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    List<MethodSignatureBackedByPsiMethod> superMethodSignatures = PsiSuperMethodUtil.findSuperMethodSignaturesIncludingStatic(method, true);
    ProblemDescriptor problemDescriptor = checkNullableStuff(method, superMethodSignatures, manager);
    return problemDescriptor == null ? null : new ProblemDescriptor[]{problemDescriptor};
  }

  public String getDisplayName() {
    return "@Nullable problems";
  }

  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  public String getShortName() {
    return "NullableProblems";
  }

  private ProblemDescriptor checkNullableStuff(PsiMethod method,
                                          List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                          final InspectionManager manager) {
    boolean isDeclaredNotNull = AnnotationUtil.isAnnotated(method, AnnotationUtil.NOT_NULL, false);
    boolean isDeclaredNullable = AnnotationUtil.isAnnotated(method, AnnotationUtil.NULLABLE, false);
    if (isDeclaredNullable && isDeclaredNotNull) {
      return manager.createProblemDescriptor(method.getNameIdentifier(),
                                             "Cannot annotate with both @Nullable and @NotNull",
                                             (LocalQuickFix)null,
                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    PsiParameter[] parameters = method.getParameterList().getParameters();

    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL && isDeclaredNullable && AnnotationUtil.isNotNull(superMethod)) {
        return manager.createProblemDescriptor(method.getNameIdentifier(),
                                               "Method annotated with @Nullable must not override @NotNull method",
                                               (LocalQuickFix)null,
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      if (REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL && !isDeclaredNullable && !isDeclaredNotNull && AnnotationUtil.isNotNull(superMethod)) {
        return manager.createProblemDescriptor(method.getNameIdentifier(),
                                               "Not annotated method overrides method annotated with @NotNull",
                                               new AnnotateMethodFix(AnnotationUtil.NOT_NULL),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      if (REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE) {
        PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
        if (superParameters.length != parameters.length) {
          continue;
        }
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiParameter superParameter = superParameters[i];
          if (AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false)
              && AnnotationUtil.isAnnotated(superParameter, AnnotationUtil.NULLABLE, false)) {
            return manager.createProblemDescriptor(parameter.getNameIdentifier(),
                                                   "Parameter annotated @NonNull must not override @Nullable parameter",
                                                   (LocalQuickFix)null,
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
      }
    }

    return null;
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private JCheckBox myNNParameterOverridesN;
    private JCheckBox myNAMethodOverridesNN;
    private JCheckBox myNMethodOverridesNN;
    private JPanel myPanel;

    private OptionsPanel() {
      super(new BorderLayout());
      add(myPanel, BorderLayout.CENTER);

      ActionListener actionListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          apply();
        }
      };
      myNAMethodOverridesNN.addActionListener(actionListener);
      myNMethodOverridesNN.addActionListener(actionListener);
      myNNParameterOverridesN.addActionListener(actionListener);
      reset();
    }

    private void reset() {
      myNNParameterOverridesN.setSelected(REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE);
      myNAMethodOverridesNN.setSelected(REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL);
      myNMethodOverridesNN.setSelected(REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL);
    }

    private void apply() {
      REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = myNAMethodOverridesNN.isSelected();
      REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = myNNParameterOverridesN.isSelected();
      REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = myNMethodOverridesNN.isSelected();
    }
  }
}
