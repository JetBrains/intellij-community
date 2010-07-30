package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

/**
 * @author max
 */
public class ComparingReferencesInspection extends BaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ComparingReferencesInspection");

  private final LocalQuickFix myQuickFix = new MyQuickFix();

  @SuppressWarnings({"WeakerAccess"}) @NonNls public String CHECKED_CLASSES = "java.lang.String;java.util.Date";
  @NonNls private static final String DESCRIPTION_TEMPLATE = InspectionsBundle.message("inspection.comparing.references.problem.descriptor");

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.comparing.references.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  public String getShortName() {
    return "ComparingReferences";
  }

  private boolean isCheckedType(PsiType type) {
    if (!(type instanceof PsiClassType)) return false;

    StringTokenizer tokenizer = new StringTokenizer(CHECKED_CLASSES, ";");
    while (tokenizer.hasMoreTokens()) {
      String className = tokenizer.nextToken();
      if (type.equalsToText(className)) return true;
    }

    return false;
  }

  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return analyzeCode(method.getBody(), manager);
  }

  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    ArrayList<ProblemDescriptor> problemList = null;
    PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      ProblemDescriptor[] problemDescriptors = analyzeCode(initializer, manager);
      if (problemDescriptors != null) {
        if (problemList == null) problemList = new ArrayList<ProblemDescriptor>();
        problemList.addAll(Arrays.asList(problemDescriptors));
      }
    }

    return problemList == null
           ? null
           : problemList.toArray(new ProblemDescriptor[problemList.size()]);
  }

  private ProblemDescriptor[] analyzeCode(PsiElement where, final InspectionManager manager) {
    if (where == null) return null;

    final Ref<ArrayList<ProblemDescriptor>> problemList = new Ref<ArrayList<ProblemDescriptor>>();
    where.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitMethod(PsiMethod method) {}

      @Override public void visitClass(PsiClass aClass) {}

      @Override public void visitBinaryExpression(PsiBinaryExpression expression) {
        super.visitBinaryExpression(expression);
        IElementType opSign = expression.getOperationSign().getTokenType();
        if (opSign == JavaTokenType.EQEQ || opSign == JavaTokenType.NE) {
          PsiExpression lOperand = expression.getLOperand();
          PsiExpression rOperand = expression.getROperand();
          if (rOperand == null || isNullLiteral(lOperand) || isNullLiteral(rOperand)) return;

          PsiType lType = lOperand.getType();
          PsiType rType = rOperand.getType();

          if (isCheckedType(lType) || isCheckedType(rType)) {
            if (problemList.get() == null) problemList.set(new ArrayList<ProblemDescriptor>());
            problemList.get().add(manager.createProblemDescriptor(expression, DESCRIPTION_TEMPLATE,
                                                               myQuickFix,
                                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }
    });

    return problemList.get() == null
           ? null
           : problemList.get().toArray(new ProblemDescriptor[problemList.get().size()]);
  }

  private static boolean isNullLiteral(PsiExpression expr) {
    return expr instanceof PsiLiteralExpression && "null".equals(expr.getText());
  }

  private static class MyQuickFix implements LocalQuickFix {
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.comparing.references.use.quickfix");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      try {
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression)descriptor.getPsiElement();
        IElementType opSign = binaryExpression.getOperationSign().getTokenType();
        PsiExpression lExpr = binaryExpression.getLOperand();
        PsiExpression rExpr = binaryExpression.getROperand();
        if (rExpr == null)
          return;

        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiMethodCallExpression equalsCall = (PsiMethodCallExpression)factory.createExpressionFromText("a.equals(b)", null);

        equalsCall.getMethodExpression().getQualifierExpression().replace(lExpr);
        equalsCall.getArgumentList().getExpressions()[0].replace(rExpr);

        PsiExpression result = (PsiExpression)binaryExpression.replace(equalsCall);

        if (opSign == JavaTokenType.NE) {
          PsiPrefixExpression negation = (PsiPrefixExpression)factory.createExpressionFromText("!a", null);
          negation.getOperand().replace(result);
          result.replace(negation);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    final JTextField checkedClasses = new JTextField(CHECKED_CLASSES);
    checkedClasses.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        CHECKED_CLASSES = checkedClasses.getText();
      }
    });

    panel.add(checkedClasses);
    return panel;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
