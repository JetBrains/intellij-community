/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.ex.AddAssertStatementFix;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;

import java.util.*;

public class DataFlowInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowInspection");

  public static final String DISPLAY_NAME = "Constant conditions & exceptions";
  public static final String SHORT_NAME = "ConstantConditions";

  public DataFlowInspection() {
  }

  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    return analyzeCodeBlock(method.getBody(), manager);
  }

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> allProblems = null;
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (int i = 0; i < initializers.length; i++) {
      final ProblemDescriptor[] problems = analyzeCodeBlock(initializers[i].getBody(), manager);
      if (problems != null) {
        if (allProblems == null) {
          allProblems = new ArrayList<ProblemDescriptor>(1);
        }
        allProblems.addAll(Arrays.asList(problems));
      }
    }
    return allProblems == null ? null : allProblems.toArray(new ProblemDescriptor[allProblems.size()]);
  }

  private ProblemDescriptor[] analyzeCodeBlock(final PsiCodeBlock body, InspectionManager manager) {
    if (body == null) return null;
    DataFlowRunner dfaRunner = new DataFlowRunner();
    if (dfaRunner.analyzeMethod(body)) {
      HashSet[] constConditions = dfaRunner.getConstConditionalExpressions();
      if (constConditions[0].size() > 0 ||
          constConditions[1].size() > 0 ||
          dfaRunner.getNPEInstructions().size() > 0 ||
          dfaRunner.getCCEInstructions().size() > 0 ||
          dfaRunner.getRedundantInstanceofs().size() > 0) {
        return createDescription(dfaRunner, manager);
      }
    }

    return null;
  }

  private static LocalQuickFix createAssertNotNullFix(PsiExpression qualifier) {
    if (qualifier != null && qualifier.getManager().getEffectiveLanguageLevel().hasAssertKeyword() &&
        !(qualifier instanceof PsiMethodCallExpression)) {
      try {
        PsiBinaryExpression binary = (PsiBinaryExpression)qualifier.getManager().getElementFactory().createExpressionFromText("a != null",
                                                                                                                              null);
        binary.getLOperand().replace(qualifier);
        return new AddAssertStatementFix(binary);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }
    return null;
  }

  private ProblemDescriptor[] createDescription(DataFlowRunner runner, InspectionManager manager) {
    HashSet[] constConditions = runner.getConstConditionalExpressions();
    HashSet trueSet = constConditions[0];
    HashSet falseSet = constConditions[1];
    Set npeSet = runner.getNPEInstructions();
    Set cceSet = runner.getCCEInstructions();
    Set redundantInstanceofs = runner.getRedundantInstanceofs();

    ArrayList<Instruction> allProblems = new ArrayList<Instruction>();
    for (Iterator iterator = trueSet.iterator(); iterator.hasNext();) {
      Instruction branchingInstruction = (Instruction)iterator.next();
      allProblems.add(branchingInstruction);
    }

    for (Iterator iterator = falseSet.iterator(); iterator.hasNext();) {
      Instruction branchingInstruction = (Instruction)iterator.next();
      allProblems.add(branchingInstruction);
    }

    for (Iterator iterator = npeSet.iterator(); iterator.hasNext();) {
      Instruction methodCallInstruction = (Instruction)iterator.next();
      allProblems.add(methodCallInstruction);
    }

    for (Iterator iterator = cceSet.iterator(); iterator.hasNext();) {
      Instruction typeCastInstruction = (Instruction)iterator.next();
      allProblems.add(typeCastInstruction);
    }

    for (Iterator iterator = redundantInstanceofs.iterator(); iterator.hasNext();) {
      Instruction instruction = (Instruction)iterator.next();
      allProblems.add(instruction);
    }

    Collections.sort(allProblems, new Comparator() {
      public int compare(Object o1, Object o2) {
        int i1 = ((Instruction)o1).getIndex();
        int i2 = ((Instruction)o2).getIndex();

        if (i1 == i2) return 0;
        if (i1 > i2) return 1;

        return -1;
      }
    });

    ArrayList<ProblemDescriptor> descriptions = new ArrayList<ProblemDescriptor>(allProblems.size());
    HashSet<PsiElement> reportedAnchors = new HashSet<PsiElement>();

    for (int i = 0; i < allProblems.size(); i++) {
      Instruction instruction = allProblems.get(i);

      if (instruction instanceof MethodCallInstruction) {
        MethodCallInstruction mcInstruction = (MethodCallInstruction)instruction;
        PsiMethodCallExpression callExpression = mcInstruction.getCallExpression();
        LocalQuickFix fix = createAssertNotNullFix(callExpression.getMethodExpression().getQualifierExpression());

        descriptions.add(manager.createProblemDescriptor(mcInstruction.getCallExpression(),
                                                         "Method invocation <code>#ref</code> #loc may produce <code>java.lang.NullPointerException</code>.",
                                                         fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
      else if (instruction instanceof FieldReferenceInstruction) {
        FieldReferenceInstruction frInstruction = (FieldReferenceInstruction)instruction;
        PsiExpression expression = frInstruction.getExpression();
        if (expression instanceof PsiArrayAccessExpression) {
          LocalQuickFix fix = createAssertNotNullFix(((PsiArrayAccessExpression)expression).getArrayExpression());
          descriptions.add(manager.createProblemDescriptor(expression,
                                                           "Array access <code>#ref</code> #loc may produce <code>java.lang.NullPointerException<./code>.",
                                                           fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        else {
          LocalQuickFix fix = createAssertNotNullFix(((PsiReferenceExpression)expression).getQualifierExpression());
          descriptions.add(manager.createProblemDescriptor(expression,
                                                           "Member variable access <code>#ref</code> #loc may produce <code>java.lang.NullPointerException<./code>.",
                                                           fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
      else if (instruction instanceof TypeCastInstruction) {
        TypeCastInstruction tcInstruction = (TypeCastInstruction)instruction;
        PsiTypeCastExpression typeCast = tcInstruction.getCastExpression();
        descriptions.add(manager.createProblemDescriptor(typeCast.getCastType(),
                                                         "Casting <code>" + typeCast.getOperand().getText() +
                                                         "</code> to <code>#ref</code> #loc may produce <code>java.lang.ClassCastException</code>.",
                                                         null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
      else if (instruction instanceof BranchingInstruction) {
        PsiElement psiAnchor = ((BranchingInstruction)instruction).getPsiAnchor();
        if (instruction instanceof BinopInstruction && ((BinopInstruction)instruction).isInstanceofRedundant()) {
          if (((BinopInstruction)instruction).canBeNull()) {
            descriptions.add(manager.createProblemDescriptor(psiAnchor,
                                                             "Condition <code>#ref</code> #loc is redundant and can be replaced with <code>!= null</code>",
                                                             new RedundantInstanceofFix(),
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
          else {
            final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(true);
            descriptions.add(manager.createProblemDescriptor(psiAnchor,
                                                             "Condition <code>#ref</code> #loc is always true</code>",
                                                             localQuickFix,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));

          }
        }
        else if (psiAnchor instanceof PsiSwitchLabelStatement) {
          if (falseSet.contains(instruction)) {
            descriptions.add(manager.createProblemDescriptor(psiAnchor, "Switch label<code>#ref</code> #loc is unreachable.", null,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
        else if (psiAnchor != null) {
          if (!reportedAnchors.contains(psiAnchor)) {
            final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(trueSet.contains(instruction));
            descriptions.add(manager.createProblemDescriptor(psiAnchor, "Condition <code>#ref</code> #loc is always <code>" +
                                                                        (trueSet.contains(instruction) ? "true" : "false") +
                                                                        "</code>.", localQuickFix,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            reportedAnchors.add(psiAnchor);
          }
        }
      }
    }

    return descriptions.toArray(new ProblemDescriptor[descriptions.size()]);
  }

  private static LocalQuickFix createSimplifyBooleanExpressionFix(final boolean value) {
    return new LocalQuickFix() {
      public String getName() {
        return new SimplifyBooleanExpressionFix(null,false).getText();
      }

      public void applyFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement psiElement = descriptor.getPsiElement();
        try {
          final SimplifyBooleanExpressionFix action = new SimplifyBooleanExpressionFix((PsiExpression)psiElement, value);
          LOG.assertTrue(psiElement.isValid());
          action.invoke(project, null, psiElement.getContainingFile());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
  }

  private static class RedundantInstanceofFix implements LocalQuickFix {
    public String getName() {
      return "Replace with != null";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiInstanceOfExpression) {
        try {
          final PsiExpression compareToNull = psiElement.getManager().getElementFactory().
            createExpressionFromText(((PsiInstanceOfExpression)psiElement).getOperand().getText() + " != null",
                                     psiElement.getParent());
          psiElement.replace(compareToNull);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }


  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return "Local Code Analysis";
  }

  public String getShortName() {
    return SHORT_NAME;
  }
}
