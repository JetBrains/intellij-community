/*
 * Class EvaluatorBuilderImpl
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.ConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;

import java.util.ArrayList;
import java.util.List;

public class EvaluatorBuilderImpl implements EvaluatorBuilder {
  private static final EvaluatorBuilderImpl ourInstance = new EvaluatorBuilderImpl();

  private EvaluatorBuilderImpl() {
  }

  public static EvaluatorBuilder getInstance() {
    return ourInstance;
  }

  public ExpressionEvaluator build(final TextWithImports text, final PsiElement contextElement) throws EvaluateException {
    if (contextElement == null) {
      throw EvaluateExceptionUtil.CANNOT_FIND_SOURCE_CLASS;
    }

    final Project project = contextElement.getProject();

    PsiCodeFragment codeFragment = text.createCodeFragment(contextElement, project);
    DebuggerUtils.checkSyntax(codeFragment);

    if(codeFragment == null) throw EvaluateExceptionUtil.INVALID_EXPRESSION(((TextWithImportsImpl)text).getText());

    return build(codeFragment);
  }

  public ExpressionEvaluator build(final PsiElement element) throws EvaluateException {
    return new Builder().buildElement(element);
  }

  private static class Builder extends PsiElementVisitor {
    private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl");
    private Evaluator myResult = null;
    private PsiClass myContextPsiClass;
    private CodeFragmentEvaluator myCurrentFragmentEvaluator;

    public void visitCodeFragment(PsiCodeFragment codeFragment) {
      ArrayList<Evaluator> evaluators = new ArrayList<Evaluator>();

      CodeFragmentEvaluator oldFragmentEvaluator = myCurrentFragmentEvaluator;
      myCurrentFragmentEvaluator = new CodeFragmentEvaluator(oldFragmentEvaluator);

      for (PsiElement child = codeFragment.getFirstChild(); child != null; child = child.getNextSibling()) {
        child.accept(this);
        if(myResult != null) {
          evaluators.add(myResult);
        }
        myResult = null;
      }

      myCurrentFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[evaluators.size()]));
      myResult = myCurrentFragmentEvaluator;

      myCurrentFragmentEvaluator = oldFragmentEvaluator;
    }

    public void visitErrorElement(PsiErrorElement element) {
      throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(element.getText()));
    }

    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      PsiExpression rExpression = expression.getRExpression();
      if(rExpression == null) throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));

      rExpression.accept(this);
      Evaluator rEvaluator = myResult;

      if(expression.getOperationSign().getTokenType() != JavaTokenType.EQ) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Operation " + expression.getOperationSign().getText() + "= is not supported."));
      }

      PsiExpression lExpression = expression.getLExpression();
      if(lExpression == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
      }

      if(lExpression.getType() == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.UNKNOWN_TYPE(lExpression.getText()));
      }

      if(!TypeConversionUtil.areTypesAssignmentCompatible(lExpression.getType(), rExpression)) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Incompatible types " + expression.getText()));
      }
      lExpression.accept(this);
      Evaluator lEvaluator = myResult;

      myResult = new AssignmentEvaluator(lEvaluator, rEvaluator);
    }

    public void visitStatement(PsiStatement statement) {
      throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Statement is not supported : " + statement.getText()));
    }

    public void visitBlockStatement(PsiBlockStatement statement) {
      PsiStatement[] statements = statement.getCodeBlock().getStatements();
      Evaluator [] evaluators = new Evaluator[statements.length];
      for (int i = 0; i < statements.length; i++) {
        PsiStatement psiStatement = statements[i];
        psiStatement.accept(this);
        evaluators[i] = myResult;
        myResult = null;
      }
      myResult = new BlockStatementEvaluator(evaluators);
    }

    public void visitWhileStatement(PsiWhileStatement statement) {
      PsiStatement body = statement.getBody();
      if(body == null) return;
      body.accept(this);
      Evaluator bodyEvaluator = myResult;

      PsiExpression condition = statement.getCondition();
      if(condition == null) return;
      condition.accept(this);
      String label = null;
      if(statement.getParent() instanceof PsiLabeledStatement) {
        label = ((PsiLabeledStatement)statement.getParent()).getLabelIdentifier().getText();
      }
      myResult = new WhileStatementEvaluator(myResult, bodyEvaluator, label);
    }

    public void visitForStatement(PsiForStatement statement) {
      PsiStatement initializer = statement.getInitialization();
      Evaluator initializerEvaluator = null;
      if(initializer != null){
        initializer.accept(this);
        initializerEvaluator = myResult;
      }

      PsiExpression condition = statement.getCondition();
      Evaluator conditionEvaluator = null;
      if(condition != null) {
        condition.accept(this);
        conditionEvaluator = myResult;
      }

      PsiStatement update = statement.getUpdate();
      Evaluator updateEvaluator = null;
      if(update != null){
        update.accept(this);
        updateEvaluator = myResult;
      }

      PsiStatement body = statement.getBody();
      if(body == null) return;
      body.accept(this);
      Evaluator bodyEvaluator = myResult;

      String label = null;
      if(statement.getParent() instanceof PsiLabeledStatement) {
        label = ((PsiLabeledStatement)statement.getParent()).getLabelIdentifier().getText();
      }
      myResult = new ForStatementEvaluator(initializerEvaluator, conditionEvaluator, updateEvaluator, bodyEvaluator, label);
    }

    public void visitIfStatement(PsiIfStatement statement) {
      PsiStatement thenBranch = statement.getThenBranch();
      if(thenBranch == null) return;
      thenBranch.accept(this);
      Evaluator thenEvaluator = myResult;

      PsiStatement elseBranch = statement.getElseBranch();
      Evaluator elseEvaluator = null;
      if(elseBranch != null){
        elseBranch.accept(this);
        elseEvaluator = myResult;
      }

      PsiExpression condition = statement.getCondition();
      if(condition == null) return;
      condition.accept(this);

      myResult = new IfStatementEvaluator(myResult, thenEvaluator, elseEvaluator);
    }

    public void visitBreakStatement(PsiBreakStatement statement) {
      PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      myResult = BreakContinueStatementEvaluator.createBreakEvaluator(labelIdentifier != null ? labelIdentifier.getText() : null);
    }

    public void visitContinueStatement(PsiContinueStatement statement) {
      PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      myResult = BreakContinueStatementEvaluator.createContinueEvaluator(labelIdentifier != null ? labelIdentifier.getText() : null);
    }

    public void visitExpressionStatement(PsiExpressionStatement statement) {
      statement.getExpression().accept(this);
    }

    public void visitExpression(PsiExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitExpression " + expression);
      }
    }

    public void visitBinaryExpression(PsiBinaryExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitBinaryExpression " + expression);
      }
      expression.getLOperand().accept(this);
      Evaluator lResult = myResult;
      PsiExpression rOperand = expression.getROperand();
      if(rOperand == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
      }
      rOperand.accept(this);
      IElementType opType = expression.getOperationSign().getTokenType();
      PsiType type = expression.getType();
      if (type == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.UNKNOWN_TYPE(expression.getText()));
      }
      myResult = new BinaryExpressionEvaluator(lResult, myResult, opType, type.getCanonicalText());
    }

    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      List<Evaluator> evaluators = new ArrayList<Evaluator>();

      PsiElement[] declaredElements = statement.getDeclaredElements();
      for (int i = 0; i < declaredElements.length; i++) {
        PsiElement declaredElement = declaredElements[i];
        if(declaredElement instanceof PsiLocalVariable) {
          if(myCurrentFragmentEvaluator != null) {
            PsiLocalVariable localVariable = ((PsiLocalVariable)declaredElement);

            PsiType type = localVariable.getType();

            if(type == null) {
              throw new EvaluateRuntimeException(EvaluateExceptionUtil.UNKNOWN_TYPE(localVariable.getName()));
            }

            PsiElementFactory elementFactory = localVariable.getManager().getElementFactory();
            try {
              PsiExpression initialValue = elementFactory.createExpressionFromText(CodeInsightUtil.getDefaultValueOfType(type), null);
              Object value = ConstantExpressionEvaluator.computeConstantExpression(initialValue, null, true);
              myCurrentFragmentEvaluator.setInitialValue(localVariable.getName(), value);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
            catch (EvaluateException e) {
              throw new EvaluateRuntimeException(e);
            }

            PsiExpression initializer = localVariable.getInitializer();
            if(initializer != null) {
              try {
                if(!TypeConversionUtil.areTypesAssignmentCompatible(localVariable.getType(), initializer)) {
                  throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Initializer for '" + localVariable.getName() + "' have incompatible type "));
                }
                initializer.accept(this);
                Evaluator rEvaluator = myResult;

                PsiExpression localVarReference = elementFactory.createExpressionFromText(localVariable.getName(), initializer);

                localVarReference.accept(this);
                Evaluator lEvaluator = myResult;

                evaluators.add(new AssignmentEvaluator(lEvaluator, rEvaluator));
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          } else {
            throw new EvaluateRuntimeException(new EvaluateException("Local variable declarations are supported here.", null));
          }
        } else {
          throw new EvaluateRuntimeException(new EvaluateException("Invalid declaration : " + declaredElement.getText() + "Only local variable declarations supported.", null));
        }
      }

      if(evaluators.size() > 0) {
        CodeFragmentEvaluator codeFragmentEvaluator = new CodeFragmentEvaluator(myCurrentFragmentEvaluator);
        codeFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[0]));
        myResult = codeFragmentEvaluator;
      } else {
        myResult = null;
      }
    }

    public void visitConditionalExpression(PsiConditionalExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitConditionalExpression " + expression);
      }
      if (expression.getThenExpression() == null || expression.getElseExpression() == null){
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
      }
      PsiExpression condition = expression.getCondition();
      condition.accept(this);
      if (myResult == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(condition.getText()));
      }
      Evaluator conditionEvaluator = myResult;
      expression.getThenExpression().accept(this);
      if (myResult == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getThenExpression().getText()));
      }
      Evaluator thenEvaluator = myResult;
      expression.getElseExpression().accept(this);
      if (myResult == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getElseExpression().getText()));
      }
      Evaluator elseEvaluator = myResult;
      myResult = new ConditionalExpressionEvaluator(conditionEvaluator, thenEvaluator, elseEvaluator);
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitReferenceExpression " + expression);
      }
      PsiExpression qualifier = expression.getQualifierExpression();
      PsiElement element = expression.resolve();

      if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
        //synthetic variable
        if(element.getContainingFile() instanceof PsiCodeFragment && myCurrentFragmentEvaluator != null) {
          myResult = new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, ((PsiVariable)element).getName());
          return;
        }
        // local variable
        PsiVariable psiVar = (PsiVariable)element;
        String localName = psiVar.getName();
        PsiClass variableClass = getContainingClass(psiVar);
        if (getContextPsiClass() == null || getContextPsiClass().equals(variableClass)) {
          myResult = new LocalVariableEvaluator(localName, ContextUtil.isJspImplicit(element));
          return;
        }
        else {
          // the expression references final var outside the context's class (in some of the outer classes)
          int iterationCount = 0;
          PsiClass aClass = getOuterClass(getContextPsiClass());
          while (aClass != null && !aClass.equals(variableClass)) {
            iterationCount++;
            aClass = getOuterClass(aClass);
          }
          if (aClass != null) {
            if(psiVar.getInitializer() != null) {
              Object value = psiVar.getManager().getConstantEvaluationHelper().computeConstantExpression(psiVar.getInitializer());
              if(value != null) {
                myResult = new LiteralEvaluator(value, psiVar.getType().getCanonicalText());
                return;
              }
            }
            Evaluator objectEvaluator = new ThisEvaluator(iterationCount);
            myResult = new FieldEvaluator(objectEvaluator, getContextPsiClass().getQualifiedName(), "val$" + localName);
            return;
          }
          else {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Local variable '" + localName + "' not found in class closure"));
          }
        }
      }
      else if (element instanceof PsiField) {
        PsiField psiField = (PsiField)element;
        PsiClass fieldClass = psiField.getContainingClass();
        if(fieldClass == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Cannot resolve class for the field : " + psiField.getName()));
        }
        Evaluator objectEvaluator;
        if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
          objectEvaluator = new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(fieldClass));
        }
        else if(qualifier != null) {
          qualifier.accept(this);
          objectEvaluator = myResult;
        }
        else if (fieldClass.equals(getContextPsiClass()) || getContextPsiClass().isInheritor(fieldClass, true)) {
            objectEvaluator = new ThisEvaluator();
        }
        else {  // myContextPsiClass != fieldClass && myContextPsiClass is not a subclass of fieldClass
          int iterationCount = 0;
          PsiClass aClass = getContextPsiClass();
          while (aClass != null && !(aClass.equals(fieldClass) || aClass.isInheritor(fieldClass, true))) {
            iterationCount++;
            aClass = getOuterClass(aClass);
          }
          if (aClass == null) {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Cannot find field's class sources for '" + psiField.getName() + "'"));
          }
          objectEvaluator = new ThisEvaluator(iterationCount);
        }
        myResult = new FieldEvaluator(objectEvaluator, fieldClass.getQualifiedName(), psiField.getName());
      } else {
        //let's guess what this could be
        PsiElement nameElement = expression.getReferenceNameElement(); // get "b" part
        String name;
        if (nameElement instanceof PsiIdentifier) {
          name = nameElement.getText();
        }
        else {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Identifier expected instead of '" + (nameElement != null ? nameElement.getText() : "(null)") + "'"));
        }

        if(qualifier != null) {
          if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass) {
            // this is a call to a 'static' field
            PsiClass psiClass = (PsiClass)((PsiReferenceExpression)qualifier).resolve();
            myResult = new FieldEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(psiClass)), psiClass.getQualifiedName(), name);
          }
          else {
            PsiType type = qualifier.getType();
            if(type == null)
              throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Type is unknown for '" + qualifier.getText() + "'"));

            qualifier.accept(this);
            if (myResult == null) {
              throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Cannot evaluate qualifier " + qualifier.getText()));
            }

            myResult = new FieldEvaluator(myResult, type.getCanonicalText(), name);
          }
        } else {
          myResult = new LocalVariableEvaluator(name, false);
        }
      }
    }

    public void visitSuperExpression(PsiSuperExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitSuperExpression " + expression);
      }
      throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("'super' is not supported"));
    }

    public void visitThisExpression(PsiThisExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitThisExpression " + expression);
      }
      PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      int iterationCount = 0;
      if (qualifier != null) {
        PsiElement targetClass = qualifier.resolve();
        if (targetClass == null || getContextPsiClass() == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(qualifier.getText()));
        }
        try {
          PsiClass aClass = getContextPsiClass();
          while (aClass != null && !aClass.equals(targetClass)) {
            iterationCount++;
            aClass = getOuterClass(aClass);
          }
        }
        catch (Exception e) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(e));
        }
      }
      myResult = new ThisEvaluator(iterationCount);
    }

    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitInstanceOfExpression " + expression);
      }
      PsiTypeElement checkType = expression.getCheckType();
      if(checkType == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
      }
      PsiType type = checkType.getType();
      if (type == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.UNKNOWN_TYPE(expression.getCheckType().getText()));
      }
      expression.getOperand().accept(this);
//    ClassObjectEvaluator typeEvaluator = new ClassObjectEvaluator(type.getCanonicalText());
      Evaluator operandEvaluator = myResult;
      myResult = new InstanceofEvaluator(operandEvaluator, new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)));
    }

    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitParenthesizedExpression " + expression);
      }
      PsiExpression expr = expression.getExpression();
      if (expr != null){
        expr.accept(this);
      }
    }

    public void visitPostfixExpression(PsiPostfixExpression expression) {
      expression.getOperand().accept(this);
      PsiType type = expression.getType();
      if(type == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.UNKNOWN_TYPE(expression.getText()));
      }
      myResult = new PostfixOperationEvaluator(myResult, expression.getOperationSign().getTokenType(), type.getCanonicalText());
    }

    public void visitPrefixExpression(PsiPrefixExpression expression) {
      PsiType type = expression.getType();
      if(type == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.UNKNOWN_TYPE(expression.getText()));
      }

      expression.getOperand().accept(this);
      Evaluator operand = myResult;

      IElementType tokenType = expression.getOperationSign().getTokenType();
      IElementType opType = expression.getOperationSign().getTokenType();

      if(tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS) {
        boolean isPlus = tokenType == JavaTokenType.PLUSPLUS;

        try {
          PsiElementFactory elementFactory = expression.getManager().getElementFactory();
          PsiExpression one = elementFactory.createExpressionFromText("1", null);
          one.accept(this);
          BinaryExpressionEvaluator operationEvaluator = new BinaryExpressionEvaluator(operand, myResult, isPlus ? JavaTokenType.PLUS : JavaTokenType.MINUS, type.getCanonicalText());

          myResult = new AssignmentEvaluator(operand, operationEvaluator);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      else {
        myResult = new UnaryExpressionEvaluator(opType, expression.getType().getCanonicalText(), operand);
      }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitMethodCallExpression " + expression);
      }
      PsiExpression[] argExpressions = expression.getArgumentList().getExpressions();
      List argumentEvaluators = new ArrayList(argExpressions.length);
      // evaluate arguments
      for (int idx = 0; idx < argExpressions.length; idx++) {
        PsiExpression psiExpression = argExpressions[idx];
        psiExpression.accept(this);
        if (myResult == null) {
          // cannot build evaluator
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(psiExpression.getText()));
        }
        argumentEvaluators.add(myResult);
      }
      PsiReferenceExpression methodExpr = expression.getMethodExpression();
      if(methodExpr == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
      }
      PsiMethod psiMethod = (PsiMethod)methodExpr.resolve();

      PsiExpression qualifier = methodExpr.getQualifierExpression();
      Evaluator objectEvaluator;
      JVMName contextClass;

      if(psiMethod != null) {
        PsiClass methodPsiClass = psiMethod.getContainingClass();
        contextClass =  JVMNameUtil.getJVMQualifiedName(methodPsiClass);
        if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
          objectEvaluator = new TypeEvaluator(contextClass);
        }
        else if (qualifier != null ){
          qualifier.accept(this);
          objectEvaluator = myResult;
        }
        else if (methodPsiClass.equals(getContextPsiClass()) || (getContextPsiClass() != null && getContextPsiClass().isInheritor(methodPsiClass, true))) {
            objectEvaluator = new ThisEvaluator();
          }
          else {
            int iterationCount = 0;
            PsiClass aClass = getContextPsiClass();
            while (aClass != null && !aClass.equals(methodPsiClass)) {
              iterationCount++;
              aClass = getOuterClass(aClass);
            }
            objectEvaluator = new ThisEvaluator(iterationCount);
          }
      } else {
        //trying to guess
        if (qualifier != null) {
          PsiType type = qualifier.getType();
          if(type == null)
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Type is unknown for '" + qualifier.getText() + "'"));

          contextClass = JVMNameUtil.getJVMQualifiedName(type);

          if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass) {
            // this is a call to a 'static' method
            objectEvaluator = new TypeEvaluator(contextClass);
          } else {
            qualifier.accept(this);
            objectEvaluator = myResult;
          }
        } else {
          objectEvaluator = new ThisEvaluator();
          if(myContextPsiClass != null) {
            contextClass = JVMNameUtil.getJVMQualifiedName(myContextPsiClass);
          } else {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Method " + methodExpr.getReferenceName() + " not found"));
          }
        }
      }

      if (objectEvaluator == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
      }

      try {
        myResult = new MethodEvaluator(objectEvaluator, contextClass, methodExpr.getReferenceName(), psiMethod != null ? JVMNameUtil.getJVMSignature(psiMethod) : null, argumentEvaluators);
      }
      catch (EvaluateException e) {
        throw new EvaluateRuntimeException(e);
      }
    }

    public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if(expression.getParsingError() != null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(expression.getParsingError()));
      }
      myResult = new LiteralEvaluator(value, expression.getType().getCanonicalText());
    }

    public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
      if(expression.getIndexExpression() == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
      }
      expression.getIndexExpression().accept(this);
      Evaluator indexEvaluator = myResult;
      expression.getArrayExpression().accept(this);
      Evaluator arrayEvaluator = myResult;
      myResult = new ArrayAccessEvaluator(arrayEvaluator, indexEvaluator);
    }

    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      expression.getOperand().accept(this);
      PsiType castType = expression.getCastType().getType();
      myResult = new TypeCastEvaluator(myResult, castType.getCanonicalText(), castType instanceof PsiPrimitiveType);
    }

    public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
      PsiType type = expression.getOperand().getType();
      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Cannot resolve class \""+type.getCanonicalText()+"\""));
      }
      myResult = new ClassObjectEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(psiClass)));
    }

    public void visitNewExpression(PsiNewExpression expression) {
      PsiType expressionPsiType = expression.getType();
      if (expressionPsiType instanceof PsiArrayType) {
        Evaluator dimensionEvaluator = null;
        PsiExpression[] dimensions = expression.getArrayDimensions();
        if (dimensions.length == 1){
          PsiExpression dimensionExpression = dimensions[0];
          dimensionExpression.accept(this);
          if (myResult != null) {
            dimensionEvaluator = myResult;
          }
          else {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Invalid expression for array dimension: " + dimensionExpression.getText()));
          }
        }
        else if (dimensions.length > 1){
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Multi-dimensional arrays new is not supported"));
        }

        Evaluator initializerEvaluator = null;
        PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
        if (arrayInitializer != null) {
          if (dimensionEvaluator != null) { // initializer already exists
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
          }
          arrayInitializer.accept(this);
          if (myResult != null) {
            initializerEvaluator = myResult;
          }
          else {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(arrayInitializer.getText()));
          }
          /*
          PsiExpression[] initializers = arrayInitializer.getInitializers();
          initializerEvaluators = new Evaluator[initializers.length];
          for (int idx = 0; idx < initializers.length; idx++) {
            PsiExpression initializer = initializers[idx];
            initializer.accept(this);
            if (myResult instanceof Evaluator) {
              initializerEvaluators[idx] = myResult;
            }
            else {
              throw new EvaluateException("Invalid expression for array initializer: " + initializer.getText(), true);
            }
          }
          */
        }
        if (dimensionEvaluator == null && initializerEvaluator == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
        }
        myResult = new NewArrayInstanceEvaluator(
          new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expressionPsiType)),
          dimensionEvaluator,
          initializerEvaluator
        );
      }
      else { // must be a class ref
        LOG.assertTrue(expressionPsiType instanceof PsiClassType);
        PsiClass aClass = ((PsiClassType)expressionPsiType).resolve();
        if(aClass instanceof PsiAnonymousClass) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException("Anonymous class evaluation is not supported"));
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(expression.getText()));
        }
        PsiExpression[] argExpressions = argumentList.getExpressions();
        PsiMethod constructor = expression.resolveConstructor();
        if (constructor == null && argExpressions.length > 0) {
          throw new EvaluateRuntimeException(new EvaluateException("Cannot resolve constructor '" + expression.getText() + "'", null));
        }
        Evaluator[] argumentEvaluators = new Evaluator[argExpressions.length];
        // evaluate arguments
        for (int idx = 0; idx < argExpressions.length; idx++) {
          PsiExpression argExpression = argExpressions[idx];
          argExpression.accept(this);
          if (myResult != null) {
            argumentEvaluators[idx] = myResult;
          }
          else {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(argExpression.getText()));
          }
        }
        try {
          JVMName signature = (constructor != null)? JVMNameUtil.getJVMSignature(constructor) : JVMNameUtil.getJVMRawText("()V");
          myResult = new NewClassInstanceEvaluator(
            new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expressionPsiType)),
            signature,
            argumentEvaluators
          );
        }
        catch (EvaluateException e) {
          throw new EvaluateRuntimeException(e);
        }
      }
    }

    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      PsiExpression[] initializers = expression.getInitializers();
      Evaluator[] evaluators = new Evaluator[initializers.length];
      for (int idx = 0; idx < initializers.length; idx++) {
        PsiExpression initializer = initializers[idx];
        initializer.accept(this);
        if (myResult != null) {
          evaluators[idx] = myResult;
        }
        else {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.INVALID_EXPRESSION(initializer.getText()));
        }
      }
      myResult = new ArrayInitializerEvaluator(evaluators);
    }

    private PsiClass getOuterClass(PsiClass aClass) {
      if(aClass == null) return null;
      return PsiTreeUtil.getContextOfType(aClass, PsiClass.class, true);
    }

    private PsiClass getContainingClass(PsiVariable variable) {
      PsiElement element = PsiTreeUtil.getParentOfType(variable.getParent(), PsiClass.class, false);
      return element == null ? getContextPsiClass() : (PsiClass)element;
    }

    public PsiClass getContextPsiClass() {
      return myContextPsiClass;
    }

    protected ExpressionEvaluator buildElement(final PsiElement element) throws EvaluateException {
      LOG.assertTrue(element.isValid());

      myContextPsiClass = PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
      try {
        element.accept(this);
      }
      catch (EvaluateRuntimeException e) {
        throw e.getCause();
      }
      if (myResult == null) {
        throw EvaluateExceptionUtil.INVALID_EXPRESSION(element.toString());
      }
      return new ExpressionEvaluatorImpl(myResult);
    }
  }
}
