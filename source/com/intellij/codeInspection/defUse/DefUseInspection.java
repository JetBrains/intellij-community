/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.defUse;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix;
import com.intellij.codeInsight.daemon.impl.quickfix.SideEffectWarningDialog;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DefUseInspection extends BaseLocalInspectionTool {
  public boolean REPORT_PREFIX_EXPRESSIONS = false;
  public boolean REPORT_POSTFIX_EXPRESSIONS = true;
  public boolean REPORT_REDUNDANT_INITIALIZER = true;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defUse.DefUseInspection");

  public static final String DISPLAY_NAME = "Unused assignment";
  public static final String SHORT_NAME = "UnusedAssignment";

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> allProblems = null;
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (int i = 0; i < initializers.length; i++) {
      final ProblemDescriptor[] problems = checkCodeBlock(initializers[i].getBody(), manager, isOnTheFly);
      if (problems != null) {
        if (allProblems == null) {
          allProblems = new ArrayList<ProblemDescriptor>(1);
        }
        allProblems.addAll(Arrays.asList(problems));
      }
    }
    return allProblems == null ? null : allProblems.toArray(new ProblemDescriptor[allProblems.size()]);
  }

  public ProblemDescriptor[] checkMethod(PsiMethod method, final InspectionManager manager, final boolean isOnTheFly) {
    return checkCodeBlock(method.getBody(), manager, isOnTheFly);
  }

  private ProblemDescriptor[] checkCodeBlock(final PsiCodeBlock body,
                                             final InspectionManager manager,
                                             final boolean isOnTheFly) {
    if (body == null) return null;
    final List<ProblemDescriptor> descriptions = new ArrayList<ProblemDescriptor>();
    final Set<PsiVariable> usedVariables = new THashSet<PsiVariable>();
    List<DefUseUtil.Info> unusedDefs = DefUseUtil.getUnusedDefs(body, usedVariables);

    if (unusedDefs != null && !unusedDefs.isEmpty()) {
      Collections.sort(unusedDefs, new Comparator<DefUseUtil.Info>() {
        public int compare(DefUseUtil.Info o1, DefUseUtil.Info o2) {
          int offset1 = o1.getContext().getTextOffset();
          int offset2 = o2.getContext().getTextOffset();

          if (offset1 == offset2) return 0;
          if (offset1 < offset2) return -1;

          return 1;
        }
      });

      for (int i = 0; i < unusedDefs.size(); i++) {
        DefUseUtil.Info info = unusedDefs.get(i);
        PsiElement context = info.getContext();
        PsiVariable psiVariable = info.getVariable();

        if (context instanceof PsiDeclarationStatement) {
          if (!info.isRead()) {
            if (!isOnTheFly) {
              descriptions.add(manager.createProblemDescriptor(psiVariable.getNameIdentifier(),
                                                               "Variable <code>#ref</code> #loc is never used.", (LocalQuickFix [])null,
                                                               ProblemHighlightType.LIKE_UNUSED_SYMBOL));
            }
          }
          else {
            if (REPORT_REDUNDANT_INITIALIZER) {
              descriptions.add(manager.createProblemDescriptor(psiVariable.getInitializer(),
                                                               "Variable <code>" + psiVariable.getName() +
                                                               "</code> initializer <code>#ref</code> #loc is redundant.",
                                                               new RemoveInitializerFix(),
                                                               ProblemHighlightType.LIKE_UNUSED_SYMBOL));
            }
          }
        }
        else if (context instanceof PsiAssignmentExpression &&
                 ((PsiAssignmentExpression)context).getOperationSign().getTokenType() == JavaTokenType.EQ) {
          final PsiAssignmentExpression assignment = (PsiAssignmentExpression)context;
          descriptions.add(manager.createProblemDescriptor(assignment.getRExpression(), "The value <code>#ref</code> assigned to " +
                                                                                        assignment.getLExpression().getText() +
                                                                                        " #loc is never used.",
                                                           (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        else {
          if (context instanceof PsiPrefixExpression && REPORT_PREFIX_EXPRESSIONS ||
              context instanceof PsiPostfixExpression && REPORT_POSTFIX_EXPRESSIONS) {
            descriptions.add(manager.createProblemDescriptor(context, "The value changed at <code>#ref</code> #loc is never used.",
                                                             (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }
    }

    body.accept(new PsiRecursiveElementVisitor() {
      public void visitClass(PsiClass aClass) {
      }

      public void visitLocalVariable(PsiLocalVariable variable) {
        if (!usedVariables.contains(variable) && variable.getInitializer() == null && !isOnTheFly) {
          descriptions.add(manager.createProblemDescriptor(variable.getNameIdentifier(),
                                                           "Variable <code>#ref</code> #loc is never used.", (LocalQuickFix [])null,
                                                           ProblemHighlightType.LIKE_UNUSED_SYMBOL));
        }
      }

      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression lExpression = expression.getLExpression();
        PsiExpression rExpression = expression.getRExpression();

        if (lExpression instanceof PsiReferenceExpression && rExpression instanceof PsiReferenceExpression) {
          PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
          PsiReferenceExpression rRef = (PsiReferenceExpression)rExpression;

          if (lRef.resolve() != rRef.resolve()) return;
          PsiExpression lQualifier = lRef.getQualifierExpression();
          PsiExpression rQualifier = rRef.getQualifierExpression();

          if ((lQualifier == null && rQualifier == null ||
               lQualifier instanceof PsiThisExpression && rQualifier instanceof PsiThisExpression ||
               lQualifier instanceof PsiThisExpression && rQualifier == null ||
               lQualifier == null && rQualifier instanceof PsiThisExpression) && !isOnTheFly) {
            descriptions.add(manager.createProblemDescriptor(expression, "The variable is assigned to itself in <code>#ref</code>.",
                                                             (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }
    });

    return descriptions.isEmpty()
           ? null
           : (ProblemDescriptor[])descriptions.toArray(new ProblemDescriptorImpl[descriptions.size()]);
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myReportPrefix;
    private final JCheckBox myReportPostfix;
    private final JCheckBox myReportInitializer;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myReportInitializer = new JCheckBox("Report redundant initializers");
      myReportInitializer.setSelected(REPORT_REDUNDANT_INITIALIZER);
      myReportInitializer.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          REPORT_REDUNDANT_INITIALIZER = myReportInitializer.isSelected();
        }
      });
      gc.insets = new Insets(0, 0, 15, 0);
      gc.gridy = 0;
      add(myReportInitializer, gc);

      myReportPrefix = new JCheckBox("Report ++i when may be replaced with (i + 1)");
      myReportPrefix.setSelected(REPORT_PREFIX_EXPRESSIONS);
      myReportPrefix.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          REPORT_PREFIX_EXPRESSIONS = myReportPrefix.isSelected();
        }
      });
      gc.insets = new Insets(0, 0, 0, 0);
      gc.gridy++;
      add(myReportPrefix, gc);

      myReportPostfix = new JCheckBox("Report i++ when changed value is not used afterwards");
      myReportPostfix.setSelected(REPORT_POSTFIX_EXPRESSIONS);
      myReportPostfix.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          REPORT_POSTFIX_EXPRESSIONS = myReportPostfix.isSelected();
        }
      });

      gc.weighty = 1;
      gc.gridy++;
      add(myReportPostfix, gc);
    }
  }


  private static class RemoveInitializerFix implements LocalQuickFix {

    public String getName() {
      return "Remove Redundant Initializer";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement psiInitializer = descriptor.getPsiElement();
      if (!(psiInitializer instanceof PsiExpression)) return;
      if (!(psiInitializer.getParent() instanceof PsiVariable)) return;

      final PsiVariable variable = (PsiVariable)psiInitializer.getParent();
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)variable.getParent();
      final List<PsiElement> sideEffects = new ArrayList<PsiElement>();
      boolean hasSideEffects = RemoveUnusedVariableFix.checkSideEffects(psiInitializer, variable, sideEffects);
      int res = SideEffectWarningDialog.DELETE_ALL;
      if (hasSideEffects) {
        hasSideEffects = PsiUtil.isStatement(psiInitializer);
        res = RemoveUnusedVariableFix.showSideEffectsWarning(sideEffects, variable, FileEditorManager.getInstance(project).getSelectedTextEditor(), hasSideEffects, sideEffects.get(0).getText(), variable.getTypeElement().getText() + " " + variable.getName() + ";<br>" + psiInitializer.getText());
      }
      try {
        if (res == SideEffectWarningDialog.DELETE_ALL) {
          psiInitializer.delete();
        }
        else if (res == SideEffectWarningDialog.MAKE_STATEMENT) {
          final PsiElementFactory factory = variable.getManager().getElementFactory();
          final PsiStatement statementFromText = factory.createStatementFromText(psiInitializer.getText() + ";", null);
          declaration.getParent().addAfter(statementFromText, declaration);
          psiInitializer.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    public String getFamilyName() {
      return getName();
    }
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  public String getShortName() {
    return SHORT_NAME;
  }
}
