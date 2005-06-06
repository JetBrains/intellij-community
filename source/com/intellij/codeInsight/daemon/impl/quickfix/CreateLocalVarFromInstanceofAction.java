package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.actions.EnterAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.ide.DataManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;

/**
 * @author cdr
 */
public class CreateLocalVarFromInstanceofAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalVarFromInstanceofAction");

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(editor, file);
    if (instanceOfExpression != null) {
      PsiTypeElement checkType = instanceOfExpression.getCheckType();
      if (checkType == null) return false;
      PsiType type = checkType.getType();
      if (type == null) return false;
      String castTo = type.getPresentableText();
      setText("Insert '("+castTo+")"+instanceOfExpression.getOperand().getText()+"' declaration");

      PsiStatement statement = PsiTreeUtil.getParentOfType(instanceOfExpression, PsiStatement.class);
      return statement instanceof PsiIfStatement && PsiTreeUtil.isAncestor(((PsiIfStatement)statement).getCondition(), instanceOfExpression, false)
             || statement instanceof PsiWhileStatement && PsiTreeUtil.isAncestor(((PsiWhileStatement)statement).getCondition(), instanceOfExpression, false);
    }
    return false;
  }

  private static PsiInstanceOfExpression getInstanceOfExpressionAtCaret(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    PsiInstanceOfExpression expression = PsiTreeUtil.getParentOfType(element, PsiInstanceOfExpression.class);
    if (expression != null) {
      return expression;
    }
    PsiStatement statement = (PsiStatement)PsiTreeUtil.getParentOfType(element, new Class[]{PsiIfStatement.class, PsiWhileStatement.class});
    if (statement instanceof PsiIfStatement) {
      PsiExpression condition = ((PsiIfStatement)statement).getCondition();
      if (condition instanceof PsiInstanceOfExpression && atSameLine(condition, editor)) return (PsiInstanceOfExpression)condition;
    }
    else if (statement instanceof PsiWhileStatement) {
      PsiExpression condition = ((PsiWhileStatement)statement).getCondition();
      if (condition instanceof PsiInstanceOfExpression && atSameLine(condition, editor)) return (PsiInstanceOfExpression)condition;
    }
    return null;
  }

  private static boolean atSameLine(final PsiExpression condition, final Editor editor) {
    int line = editor.getCaretModel().getLogicalPosition().line;
    return editor.offsetToLogicalPosition(condition.getTextOffset()).line == line;
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(editor, file);

    try {
      final PsiDeclarationStatement decl = createLocalVariableDeclaration(instanceOfExpression);
      PsiLocalVariable localVariable = (PsiLocalVariable)decl.getDeclaredElements()[0];
      TemplateBuilder builder = new TemplateBuilder(localVariable);
      builder.setEndVariableAfter(localVariable.getNameIdentifier());

      Template template = generateTemplate(project, localVariable.getInitializer(), localVariable.getType());

      Editor newEditor = CreateFromUsageBaseAction.positionCursor(project, file, localVariable.getNameIdentifier());
      TextRange range = localVariable.getNameIdentifier().getTextRange();
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      CreateFromUsageBaseAction.startTemplate(newEditor, template, project, new TemplateStateListener() {
        public void templateFinished(Template template) {
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

          CaretModel caretModel = editor.getCaretModel();
          PsiElement elementAt = file.findElementAt(caretModel.getOffset());
          PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(elementAt, PsiDeclarationStatement.class);
          if (declarationStatement != null) {
            caretModel.moveToOffset(declarationStatement.getTextRange().getEndOffset());
          }
          new EnterAction().actionPerformed(editor, DataManager.getInstance().getDataContext());
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiDeclarationStatement createLocalVariableDeclaration(final PsiInstanceOfExpression instanceOfExpression) throws IncorrectOperationException {
    PsiElementFactory factory = instanceOfExpression.getManager().getElementFactory();
    PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(a)b", instanceOfExpression);
    PsiType castType = instanceOfExpression.getCheckType().getType();
    cast.getCastType().replace(factory.createTypeElement(castType));
    cast.getOperand().replace(instanceOfExpression.getOperand());
    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement("xxx", castType, cast);
    PsiDeclarationStatement element = insertAtAnchor(instanceOfExpression, decl);

    return element;
  }

  private static PsiDeclarationStatement insertAtAnchor(final PsiInstanceOfExpression instanceOfExpression, PsiDeclarationStatement toInsert) throws IncorrectOperationException {
    PsiElement element = instanceOfExpression.getParent();
    while (element instanceof PsiParenthesizedExpression) {
      element = element.getParent();
    }
    boolean negated = element instanceof PsiPrefixExpression &&
                      ((PsiPrefixExpression)element).getOperationSign().getTokenType() ==
                      JavaTokenType.EXCL;
    PsiStatement statement = PsiTreeUtil.getParentOfType(instanceOfExpression, PsiStatement.class);
    PsiElementFactory factory = toInsert.getManager().getElementFactory();
    PsiElement anchorAfter = null;
    PsiBlockStatement emptyBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", instanceOfExpression);
    if (statement instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)statement;
      if (negated) {
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch == null) {
          anchorAfter = ifStatement;
        }
        else if (!(elseBranch instanceof PsiBlockStatement)) {
          emptyBlockStatement.add(elseBranch);
          PsiBlockStatement newBranch = (PsiBlockStatement)elseBranch.replace(emptyBlockStatement);
          anchorAfter = newBranch.getCodeBlock().getLBrace();
        }
        else {
          anchorAfter = ((PsiBlockStatement)elseBranch).getCodeBlock().getLBrace();
        }
      }
      else {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        if (thenBranch == null) {
          ifStatement.setThenBranch(emptyBlockStatement);
          anchorAfter = ((PsiBlockStatement)ifStatement.getThenBranch()).getCodeBlock().getLBrace();
        }
        else if (!(thenBranch instanceof PsiBlockStatement)) {
          emptyBlockStatement.add(thenBranch);
          PsiBlockStatement newBranch = (PsiBlockStatement)thenBranch.replace(emptyBlockStatement);
          anchorAfter = newBranch.getCodeBlock().getLBrace();
        }
        else {
          anchorAfter = ((PsiBlockStatement)thenBranch).getCodeBlock().getLBrace();
        }
      }
    }
    if (statement instanceof PsiWhileStatement) {
      PsiWhileStatement whileStatement = (PsiWhileStatement)statement;
      LOG.assertTrue(whileStatement.getLParenth() != null);
      LOG.assertTrue(whileStatement.getCondition() != null);
      if (whileStatement.getRParenth() == null) {
        PsiWhileStatement statementPattern = (PsiWhileStatement)factory.createStatementFromText("while (){}", instanceOfExpression);
        whileStatement.addAfter(statementPattern.getRParenth(), whileStatement.getCondition());
      }
      if (negated) {
        anchorAfter = whileStatement;
      }
      else {
        PsiStatement body = whileStatement.getBody();
        if (body == null) {
          whileStatement.add(emptyBlockStatement);
        }
        else if (!(body instanceof PsiBlockStatement)) {
          emptyBlockStatement.add(body);
          whileStatement.getBody().replace(emptyBlockStatement);
        }
        anchorAfter = ((PsiBlockStatement)whileStatement.getBody()).getCodeBlock().getLBrace();
      }
    }
    if (anchorAfter == null) {
      return null;
    }
    return (PsiDeclarationStatement)anchorAfter.getParent().addAfter(toInsert, anchorAfter);
  }

  private static Template generateTemplate(Project project, PsiExpression initializer, PsiType type) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    SuggestedNameInfo suggestedNameInfo = CodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null,
                                                                                                    initializer, type);
    List<String> uniqueNames = new ArrayList<String>();
    for (String name : suggestedNameInfo.names) {
      if (PsiUtil.isVariableNameUnique(name, initializer)) {
        uniqueNames.add(name);
      }
    }
    if (uniqueNames.size() == 0 && suggestedNameInfo.names.length != 0) {
      uniqueNames.add(suggestedNameInfo.names[0]);
    }

    LinkedHashSet<LookupItem> itemSet = new LinkedHashSet<LookupItem>();
    for (String name : uniqueNames) {
      LookupItemUtil.addLookupItem(itemSet, name, "");
    }
    final LookupItem[] lookupItems = itemSet.toArray(new LookupItem[itemSet.size()]);
    final Result result = suggestedNameInfo.names.length > 0 ? new TextResult(suggestedNameInfo.names[0]) : null;

    Expression expr = new Expression() {
      public LookupItem[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }
    };
    template.addVariable("", expr, expr, true);
    template.addEndVariable();

    return template;
  }

  protected void invokeImpl(PsiClass targetClass) {
  }

  public String getFamilyName() {
    return "Create Local Var from instanceof Usage";
  }
}
