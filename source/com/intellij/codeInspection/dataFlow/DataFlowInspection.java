/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.ex.AddAssertStatementFix;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DataFlowInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowInspection");

  public static final @NonNls String SHORT_NAME = "ConstantConditions";

  public boolean SUGGEST_NULLABLE_ANNOTATIONS = false;

  public DataFlowInspection() {
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    return analyzeCodeBlock(method.getBody(), manager);
  }

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> allProblems = null;
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] problems = analyzeCodeBlock(initializer.getBody(), manager);
      if (problems != null) {
        if (allProblems == null) {
          allProblems = new ArrayList<ProblemDescriptor>(1);
        }
        allProblems.addAll(Arrays.asList(problems));
      }
    }
    return allProblems == null ? null : allProblems.toArray(new ProblemDescriptor[allProblems.size()]);
  }

  private @Nullable ProblemDescriptor[] analyzeCodeBlock(final PsiCodeBlock body, InspectionManager manager) {
    if (body == null) return null;
    DataFlowRunner dfaRunner = new DataFlowRunner(SUGGEST_NULLABLE_ANNOTATIONS);
    if (dfaRunner.analyzeMethod(body)) {
      if (dfaRunner.problemsDetected()) {
        return createDescription(dfaRunner, manager);
      }
    }

    return null;
  }

  private static @Nullable LocalQuickFix createAssertNotNullFix(PsiExpression qualifier) {
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

  private static ProblemDescriptor[] createDescription(DataFlowRunner runner, InspectionManager manager) {
    HashSet<Instruction>[] constConditions = runner.getConstConditionalExpressions();
    HashSet<Instruction> trueSet = constConditions[0];
    HashSet<Instruction> falseSet = constConditions[1];
    Set<Instruction> npeSet = runner.getNPEInstructions();
    Set<Instruction> cceSet = runner.getCCEInstructions();
    Set<Instruction> redundantInstanceofs = runner.getRedundantInstanceofs();

    ArrayList<Instruction> allProblems = new ArrayList<Instruction>();
    for (Instruction instr : trueSet) {
      allProblems.add((Instruction)instr);
    }

    for (Instruction instr : falseSet) {
      allProblems.add(instr);
    }

    for (Instruction methodCallInstruction : npeSet) {
      allProblems.add(methodCallInstruction);
    }

    for (Instruction typeCastInstruction : cceSet) {
      allProblems.add(typeCastInstruction);
    }

    for (Instruction instruction : redundantInstanceofs) {
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
        if (mcInstruction.getCallExpression() instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression callExpression = (PsiMethodCallExpression)mcInstruction.getCallExpression();
          LocalQuickFix fix = createAssertNotNullFix(callExpression.getMethodExpression().getQualifierExpression());

          descriptions.add(manager.createProblemDescriptor(mcInstruction.getCallExpression(),
                                                           InspectionsBundle.message("dataflow.message.npe.method.invocation"),
                                                           fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
      else if (instruction instanceof FieldReferenceInstruction) {
        FieldReferenceInstruction frInstruction = (FieldReferenceInstruction)instruction;
        PsiExpression expression = frInstruction.getExpression();
        if (expression instanceof PsiArrayAccessExpression) {
          LocalQuickFix fix = createAssertNotNullFix(((PsiArrayAccessExpression)expression).getArrayExpression());
          descriptions.add(manager.createProblemDescriptor(expression,
                                                           InspectionsBundle.message("dataflow.message.npe.array.access"),
                                                           fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        else {
          LocalQuickFix fix = createAssertNotNullFix(((PsiReferenceExpression)expression).getQualifierExpression());
          descriptions.add(manager.createProblemDescriptor(expression,
                                                           InspectionsBundle.message("dataflow.message.npe.field.access"),
                                                           fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
      else if (instruction instanceof TypeCastInstruction) {
        TypeCastInstruction tcInstruction = (TypeCastInstruction)instruction;
        PsiTypeCastExpression typeCast = tcInstruction.getCastExpression();
        descriptions.add(manager.createProblemDescriptor(typeCast.getCastType(),
                                                         InspectionsBundle.message("dataflow.message.cce", typeCast.getOperand().getText()),
                                                         (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
      else if (instruction instanceof BranchingInstruction) {
        PsiElement psiAnchor = ((BranchingInstruction)instruction).getPsiAnchor();
        if (instruction instanceof BinopInstruction && ((BinopInstruction)instruction).isInstanceofRedundant()) {
          if (((BinopInstruction)instruction).canBeNull()) {
            descriptions.add(manager.createProblemDescriptor(psiAnchor,
                                                             InspectionsBundle.message("dataflow.message.redundant.instanceof"),
                                                             new RedundantInstanceofFix(),
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
          else {
            final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(psiAnchor, true);
            descriptions.add(manager.createProblemDescriptor(psiAnchor,
                                                             InspectionsBundle.message("dataflow.message.constant.condition", Boolean.toString(true)),
                                                             localQuickFix,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
        else if (psiAnchor instanceof PsiSwitchLabelStatement) {
          if (falseSet.contains(instruction)) {
            descriptions.add(manager.createProblemDescriptor(psiAnchor,
                                                             InspectionsBundle.message("dataflow.message.unreachable.switch.label"),
                                                             (LocalQuickFix [])null,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
        else if (psiAnchor != null) {
          if (!reportedAnchors.contains(psiAnchor)) {
            if (onTheLeftSideOfConditionalAssignemnt(psiAnchor)) {
              descriptions.add(manager.createProblemDescriptor(psiAnchor, InspectionsBundle.message("dataflow.message.pointless.assignment.expression", Boolean.toString(trueSet.contains(instruction))),
                                                               (LocalQuickFix)null,
                                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
            else {
              final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(psiAnchor, trueSet.contains(instruction));
              descriptions.add(manager.createProblemDescriptor(psiAnchor,
                                                               InspectionsBundle.message("dataflow.message.constant.condition",
                                                                                         Boolean.toString(trueSet.contains(instruction))),
                                                               localQuickFix,
                                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
            reportedAnchors.add(psiAnchor);
          }
        }
      }
    }

    Set<PsiExpression> exprs = runner.getNullableArguments();
    for (PsiExpression expr : exprs) {
      final String text = isNullLiteralExpression(expr)
                          ? InspectionsBundle.message("dataflow.message.passing.null.argument")
                          : InspectionsBundle.message("dataflow.message.passing.nullable.argument");

      descriptions.add(manager.createProblemDescriptor(expr, text, (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
    }

    exprs = runner.getNullableAssignments();
    for (PsiExpression expr : exprs) {
      final String text = isNullLiteralExpression(expr)
                              ? InspectionsBundle.message("dataflow.message.assigning.null")
                              : InspectionsBundle.message("dataflow.message.assigning.nullable");
      descriptions.add(manager.createProblemDescriptor(expr, text, (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
    }

    final HashSet<PsiReturnStatement> statements = runner.getNullableReturns();
    for (PsiReturnStatement statement : statements) {
      final PsiExpression expr = statement.getReturnValue();
      if (runner.isInNotNullMethod()) {
        final String text = isNullLiteralExpression(expr)
                                ? InspectionsBundle.message("dataflow.message.return.null.from.notnull")
                                : InspectionsBundle.message("dataflow.message.return.nullable.from.notnull");
        descriptions.add(manager.createProblemDescriptor(expr, text, (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
      else if (AnnotationUtil.isAnnotatingApplicable(statement)) {
        final String text = isNullLiteralExpression(expr)
                                ? InspectionsBundle.message("dataflow.message.return.null.from.notnullable")
                                : InspectionsBundle.message("dataflow.message.return.nullable.from.notnullable");
        descriptions.add(manager.createProblemDescriptor(expr, text,
                                                         new AnnotateMethodFix(AnnotationUtil.NULLABLE),
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING));

      }
    }

    return descriptions.toArray(new ProblemDescriptor[descriptions.size()]);
  }

  private static boolean isNullLiteralExpression(PsiExpression expr) {
    if (expr instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expr;
      return literalExpression.getType() == PsiType.NULL;
    }
    return false;
  }

  private static boolean onTheLeftSideOfConditionalAssignemnt(final PsiElement psiAnchor) {
    final PsiElement parent = psiAnchor.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression expression = (PsiAssignmentExpression)parent;
      if (expression.getLExpression() == psiAnchor) return true;
    }
    return false;
  }

  private static @Nullable LocalQuickFix createSimplifyBooleanExpressionFix(PsiElement element, final boolean value) {
    if (!(element instanceof PsiExpression)) return null;
    final PsiExpression expression = (PsiExpression)element;
    while (element.getParent() instanceof PsiExpression) {
      element = element.getParent();
    }
    final SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expression, Boolean.valueOf(value));
    // simplify intention already active
    if (!fix.isAvailable(element.getProject(), null, element.getContainingFile()) 
        || SimplifyBooleanExpressionFix.canBeSimplified((PsiExpression)element)) return null;
    return new LocalQuickFix() {
      public String getName() {
        return fix.getText();
      }

      public void applyFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement psiElement = descriptor.getPsiElement();
        try {
          LOG.assertTrue(psiElement.isValid());
          fix.invoke(project, null, psiElement.getContainingFile());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      public String getFamilyName() {
        return InspectionsBundle.message("inspection.data.flow.simplify.boolean.expression.quickfix");
      }
    };
  }

  private static class RedundantInstanceofFix implements LocalQuickFix {
    public String getName() {
      return InspectionsBundle.message("inspection.data.flow.redundant.instanceof.quickfix");
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

    public String getFamilyName() {
      return getName();
    }
  }


  public String getDisplayName() {
    return InspectionsBundle.message("inspection.data.flow.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox mySuggestNullables;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      //mySuggestNullables = new JCheckBox("Suggest @Nullable annotation for method possibly return null.\n Requires JDK5.0 and annotations.jar from IDEA distribution");
      mySuggestNullables = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.nullable.quickfix.option", ApplicationNamesInfo.getInstance().getProductName()));
      mySuggestNullables.setSelected(SUGGEST_NULLABLE_ANNOTATIONS);
      mySuggestNullables.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          SUGGEST_NULLABLE_ANNOTATIONS = mySuggestNullables.isSelected();
        }
      });
      gc.insets = new Insets(0, 0, 15, 0);
      gc.gridy = 0;
      add(mySuggestNullables, gc);
    }
  }
}
