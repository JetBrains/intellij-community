package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class VariableAccessFromInnerClassFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.VariableAccessFromInnerClassFix");
  private final PsiVariable myVariable;
  private final PsiClass myClass;
  private int myFixType;
  private static final int MAKE_ARRAY = 2;
  private static final int COPY_TO_FINAL = 1;
  private static final int MAKE_FINAL = 0;

  public VariableAccessFromInnerClassFix(PsiVariable variable, PsiClass aClass) {
    myVariable = variable;
    myClass = aClass;
    myFixType = getQuickFixType(variable);
  }


  public String getText() {
    final String message;
    switch (myFixType) {
      case MAKE_FINAL:
        message = "Make ''{0}'' final";
        break;
      case COPY_TO_FINAL:
        message = "Copy ''{0}'' to temp final variable";
        break;
      case MAKE_ARRAY:
        message = "Transform ''{0}'' into final one element array";
        break;
        default: message = null;
    }
    final String text = MessageFormat.format(message,
            new Object[]{
              myVariable.getName(),
            });
    return text;
  }

  public String getFamilyName() {
    return "Make Final";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myClass != null
        && myClass.isValid()
        && myClass.getManager().isInProject(myClass)
        && myVariable != null
        && myVariable.isValid()
        && myVariable.getType() != null
        && myFixType != -1
        && !inOwnInitializer (myVariable, myClass);
  }

  private boolean inOwnInitializer(PsiVariable variable, PsiClass aClass) {
    return PsiTreeUtil.isAncestor(variable, aClass, false);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    if (!CodeInsightUtil.prepareFileForWrite(myVariable.getContainingFile())) return;
    try {
      switch (myFixType) {
        case MAKE_FINAL:
          myVariable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
          break;
        case COPY_TO_FINAL:
          copyToFinal();
          break;
        case MAKE_ARRAY:
          makeArray();
          break;
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);

    }
  }

  private void makeArray() throws IncorrectOperationException {
    PsiType type = myVariable.getType();

    final PsiElementFactory factory = myClass.getManager().getElementFactory();
    final PsiType newType = type.createArrayType();

    final PsiDeclarationStatement variableDeclarationStatement;
    if (myVariable.hasInitializer()) {
      final PsiExpression init = factory.createExpressionFromText("new " + newType.getCanonicalText() + " { " + myVariable.getInitializer().getText() + " }", myVariable);
      variableDeclarationStatement = factory.createVariableDeclarationStatement(myVariable.getName(), newType, init);
    }
    else {
      String expression = "[1]";
      while (type instanceof PsiArrayType) {
        expression += "[1]";
        type = ((PsiArrayType) type).getComponentType();
      }
      final PsiExpression init = factory.createExpressionFromText("new " + type.getCanonicalText() + expression, myVariable);
      variableDeclarationStatement = factory.createVariableDeclarationStatement(myVariable.getName(), newType, init);
    }
    PsiVariable newVariable;
    newVariable = (PsiVariable) variableDeclarationStatement.getDeclaredElements()[0];
    newVariable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
    PsiElement newExpression = factory.createExpressionFromText(myVariable.getName() + "[0]", myVariable);

    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(myVariable, null);
    if (outerCodeBlock == null) return;
    List<PsiReferenceExpression> outerReferences = new ArrayList<PsiReferenceExpression>();
    collectReferences(outerCodeBlock, myVariable, outerReferences);
    replaceReferences(outerReferences, newExpression);
    myVariable.replace(newVariable);
  }

  private void copyToFinal() throws IncorrectOperationException {
    PsiManager psiManager = myClass.getManager();
    final PsiElementFactory factory = psiManager.getElementFactory();
    final PsiExpression initializer = factory.createExpressionFromText(myVariable.getName(), myClass);
    final String newName = suggestNewName(psiManager.getProject(), myVariable);
    final PsiType type = myVariable.getType();
    final PsiDeclarationStatement copyDecl = factory.createVariableDeclarationStatement(newName, type, initializer);
    PsiVariable newVariable = ((PsiVariable) copyDecl.getDeclaredElements()[0]);
    newVariable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
    PsiElement statement = PsiUtil.getEnclosingStatement(myClass);
    if (statement != null && statement.getParent() != null) {
      statement.getParent().addBefore(copyDecl, statement);
      PsiExpression newExpression = factory.createExpressionFromText(newName, myVariable);
      replaceReferences(myClass, myVariable, newExpression);
    }
  }

  private static String suggestNewName(Project project, PsiVariable variable) {
    // new name should not conflict with another variable at the variable declaration level and usage level
    String name = variable.getName();
    // trim last digit to suggest variable names like i1,i2, i3...
    if (name.length() > 1 && Character.isDigit(name.charAt(name.length()-1))) {
      name = name.substring(0,name.length()-1);
    }
    return CodeStyleManager.getInstance(project).suggestUniqueVariableName(name, variable, true);
  }


  private static void replaceReferences(PsiElement context, final PsiVariable variable, final PsiElement newExpression) {
    context.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable)
          try {
            expression.replace(newExpression);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static void replaceReferences(List<PsiReferenceExpression> references, PsiElement newExpression) throws IncorrectOperationException {
    for (int i = 0; i < references.size(); i++) {
      PsiElement reference = references.get(i);
      reference.replace(newExpression);
    }
  }

  private static void collectReferences(PsiElement context, final PsiVariable variable, final List<PsiReferenceExpression> references) {
    context.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static int getQuickFixType(PsiVariable variable) {
    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return -1;
    List<PsiReferenceExpression> outerReferences = new ArrayList<PsiReferenceExpression>();
    collectReferences(outerCodeBlock, variable, outerReferences);

    int type = MAKE_FINAL;
    for (int i = 0; i < outerReferences.size(); i++) {
      PsiReferenceExpression expression = outerReferences.get(i);

      // if it happens that variable referenced from another inner class, make sure it can be make final from there
      final PsiClass innerClass = HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(variable, expression);

      if (innerClass != null) {
        int thisType = MAKE_FINAL;
        if (writtenInside(variable, innerClass)) {
          // cannot make parameter array
          if (variable instanceof PsiParameter) return -1;
          thisType = MAKE_ARRAY;
        }
        if (thisType == MAKE_FINAL
            && !canBeFinal(variable, outerReferences))  {
          thisType = COPY_TO_FINAL;
        }
        type = Math.max(type, thisType);
      }
    }
    return type;
  }

  private static boolean canBeFinal(PsiVariable variable, List<PsiReferenceExpression> references) {
    // if there is at least one assignment to this variable, it cannot be final
    final HashMap<PsiElement, List<PsiReferenceExpression>> uninitializedVarProblems = new HashMap<PsiElement, List<PsiReferenceExpression>>();
    final HashMap<PsiElement, List<PsiElement>> finalVarProblems = new HashMap<PsiElement, List<PsiElement>>();
    for (int i = 0; i < references.size(); i++) {
      PsiReferenceExpression expression = references.get(i);

      if (ControlFlowUtil.isVariableAssignedInLoop(expression, variable)) return false;
      HighlightInfo highlightInfo = HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, variable, uninitializedVarProblems);
      if (highlightInfo != null) return false;
      highlightInfo = HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, finalVarProblems);
      if (highlightInfo != null) return false;
    }
    return true;
  }

  private static boolean writtenInside(PsiVariable variable, PsiElement element) {
    if (element instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = ((PsiAssignmentExpression) element);
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      if (lExpression instanceof PsiReferenceExpression
          && ((PsiReferenceExpression) lExpression).resolve() == variable)
        return true;
    }
    else if (PsiUtil.isIncrementDecrementOperation(element)) {
      PsiElement operand = element instanceof PsiPostfixExpression ?
          ((PsiPostfixExpression) element).getOperand() :
          ((PsiPrefixExpression) element).getOperand();
      if (operand instanceof PsiReferenceExpression
          && ((PsiReferenceExpression) operand).resolve() == variable)
        return true;
    }
    final PsiElement[] children = element.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (writtenInside(variable, child)) return true;
    }
    return false;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
