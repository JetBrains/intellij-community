package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.LinkedHashSet;

/**
 * @author cdr
 */
public class CreateLocalVarFromInstanceofAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalVarFromInstanceofAction");

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpression(editor, file);
    if (instanceOfExpression != null) {
      String castTo = instanceOfExpression.getCheckType().getType().getPresentableText();
      setText("Insert '("+castTo+")"+instanceOfExpression.getOperand().getText()+"' declaration");
    }
    return instanceOfExpression != null;
  }

  private static PsiInstanceOfExpression getInstanceOfExpression(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    PsiInstanceOfExpression expression = PsiTreeUtil.getParentOfType(element, PsiInstanceOfExpression.class);
    return expression;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpression(editor, file);

    try {
      PsiLocalVariable localVariable = createLocalVariable(instanceOfExpression);
      TemplateBuilder builder = new TemplateBuilder(localVariable);
      builder.setEndVariableAfter(localVariable.getNameIdentifier());

      Template template = generateTemplate(project, localVariable.getInitializer(), localVariable.getType());

      Editor newEditor = CreateFromUsageBaseAction.positionCursor(project, file, localVariable.getNameIdentifier());
      TextRange range = localVariable.getNameIdentifier().getTextRange();
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      CreateFromUsageBaseAction.startTemplate(newEditor, template, project);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiLocalVariable createLocalVariable(final PsiInstanceOfExpression instanceOfExpression) throws IncorrectOperationException {
    PsiElementFactory factory = instanceOfExpression.getManager().getElementFactory();
    PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(a)b", instanceOfExpression);
    PsiType castType = instanceOfExpression.getCheckType().getType();
    cast.getCastType().replace(factory.createTypeElement(castType));
    cast.getOperand().replace(instanceOfExpression.getOperand());
    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement("xxx", castType, cast);
    PsiDeclarationStatement element = insertAtAnchor(instanceOfExpression, decl);
    PsiLocalVariable localVariable = (PsiLocalVariable)element.getDeclaredElements()[0];
    return localVariable;
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
    PsiElement anchorAfter;
    if (statement instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)statement;
      PsiBlockStatement codeBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", ifStatement);
      if (negated) {
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch == null) {
          anchorAfter = ifStatement;
        }
        else if (!(elseBranch instanceof PsiBlockStatement)) {
          codeBlockStatement.add(elseBranch);
          PsiBlockStatement newBranch = (PsiBlockStatement)elseBranch.replace(codeBlockStatement);
          anchorAfter = newBranch.getCodeBlock().getLBrace();
        }
        else {
          anchorAfter = ((PsiBlockStatement)elseBranch).getCodeBlock().getLBrace();
        }
      }
      else {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        if (thenBranch == null) {
          ifStatement.setThenBranch(codeBlockStatement);
          anchorAfter = ((PsiBlockStatement)ifStatement.getThenBranch()).getCodeBlock().getLBrace();
        }
        else if (!(thenBranch instanceof PsiBlockStatement)) {
          codeBlockStatement.add(thenBranch);
          PsiBlockStatement newBranch = (PsiBlockStatement)thenBranch.replace(codeBlockStatement);
          anchorAfter = newBranch.getCodeBlock().getLBrace();
        }
        else {
          anchorAfter = ((PsiBlockStatement)thenBranch).getCodeBlock().getLBrace();
        }
      }
      return (PsiDeclarationStatement)anchorAfter.getParent().addAfter(toInsert, anchorAfter);

    }
    return null;
  }

  private static Template generateTemplate(Project project, PsiExpression initializer, PsiType type) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    SuggestedNameInfo suggestedNameInfo = CodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null,
                                                                                                    initializer, type);
    LinkedHashSet<LookupItem> itemSet = new LinkedHashSet<LookupItem>();
    for (String name : suggestedNameInfo.names) {
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
