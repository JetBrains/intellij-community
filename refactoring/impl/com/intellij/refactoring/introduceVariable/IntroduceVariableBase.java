/**
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Nov 15, 2002
 * Time: 5:21:33 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.FieldConflictsResolver;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.refactoring.util.occurences.NotInSuperCallOccurenceFilter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class IntroduceVariableBase extends IntroduceHandlerBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceVariable.IntroduceVariableBase");
  protected static String REFACTORING_NAME = RefactoringBundle.message("introduce.variable.title");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    if (invoke(project, editor, file, editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd())) {
      editor.getSelectionModel().removeSelection();
    }
  }

  private boolean invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable");
    PsiDocumentManager.getInstance(project).commitAllDocuments();


    PsiExpression tempExpr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (tempExpr == null) {
      PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        tempExpr = ((PsiExpressionStatement) statements[0]).getExpression();
      }
    }
    return invokeImpl(project, tempExpr, editor);
  }

  protected boolean invokeImpl(final Project project, final PsiExpression expr,
                               final Editor editor) {
    if (expr != null && expr.getParent() instanceof PsiExpressionStatement) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable.incompleteStatement");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    if (expr == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.an.expression"));
      showErrorMessage(message, project);
      return false;
    }
    final PsiFile file = expr.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");
    final PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();


    PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (originalType == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("unknown.expression.type"));
      showErrorMessage(message, project);
      return false;
    }

    if(originalType == PsiType.VOID) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(message, project);
      return false;
    }

    PsiElement anchorStatement = RefactoringUtil.getParentStatement(expr, false);
    if (anchorStatement == null) {
        return parentStatementNotFound(project);
    }
    if (anchorStatement instanceof PsiExpressionStatement) {
      PsiExpression enclosingExpr = ((PsiExpressionStatement)anchorStatement).getExpression();
      if (enclosingExpr instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)enclosingExpr).resolveMethod();
        if (method != null && method.isConstructor()) {
          //This is either 'this' or 'super', both must be the first in the respective contructor
          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invalid.expression.context"));
          showErrorMessage(message, project);
          return false;
        }
      }
    }

    PsiElement tempContainer = anchorStatement.getParent();

    if (!(tempContainer instanceof PsiCodeBlock) && !isLoopOrIf(tempContainer)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
      showErrorMessage(message, project);
      return false;
    }

    if(!NotInSuperCallOccurenceFilter.INSTANCE.isOK(expr)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.introduce.variable.in.super.constructor.call"));
      showErrorMessage(message, project);
      return false;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    PsiElement containerParent = tempContainer;
    PsiElement lastScope = tempContainer;
    while (true) {
      if (containerParent instanceof PsiFile) break;
      if (containerParent instanceof PsiMethod) break;
      containerParent = containerParent.getParent();
      if (containerParent instanceof PsiCodeBlock) {
        lastScope = containerParent;
      }
    }

    ExpressionOccurenceManager occurenceManager = new ExpressionOccurenceManager(expr, lastScope,
                                                                                 NotInSuperCallOccurenceFilter.INSTANCE);
    final PsiExpression[] occurrences = occurenceManager.getOccurences();
    final PsiElement anchorStatementIfAll = occurenceManager.getAnchorStatementForAll();
    boolean declareFinalIfAll = occurenceManager.isInFinalContext();


    boolean anyAssignmentLHS = false;
    for (PsiExpression occurrence : occurrences) {
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        anyAssignmentLHS = true;
        break;
      }
    }


    IntroduceVariableSettings settings = getSettings(project, editor, expr, occurrences, anyAssignmentLHS, declareFinalIfAll,
                                                     originalType,
                                                     new TypeSelectorManagerImpl(project, originalType, expr, occurrences),
                                                     new InputValidator(this, project, anchorStatementIfAll, anchorStatement, occurenceManager));

    if (!settings.isOK()) {
      return false;
    }

    final String variableName = settings.getEnteredName();

    final PsiType type = settings.getSelectedType();
    final boolean replaceAll = settings.isReplaceAllOccurrences();
    final boolean replaceWrite = settings.isReplaceLValues();
    final boolean declareFinal = replaceAll && declareFinalIfAll || settings.isDeclareFinal();
    if (replaceAll) {
      anchorStatement = anchorStatementIfAll;
      tempContainer = anchorStatement.getParent();
    }

    final PsiElement container = tempContainer;

    PsiElement child = anchorStatement;
    if (!isLoopOrIf(container)) {
      child = locateAnchor(child);
    }
    final PsiElement anchor = child == null ? anchorStatement : child;

    boolean tempDeleteSelf = false;
    final boolean replaceSelf = replaceWrite || !RefactoringUtil.isAssignmentLHS(expr);
    if (!isLoopOrIf(container)) {
      if (expr.getParent() instanceof PsiExpressionStatement && anchor.equals(anchorStatement)) {
        PsiStatement statement = (PsiStatement) expr.getParent();
        PsiElement parent = statement.getParent();
        if (parent instanceof PsiCodeBlock ||
            //fabrique
            parent instanceof PsiCodeFragment) {
          tempDeleteSelf = true;
        }
      }
      tempDeleteSelf &= replaceSelf;
    }
    final boolean deleteSelf = tempDeleteSelf;


    final int col = editor != null ? editor.getCaretModel().getLogicalPosition().column : 0;
    final int line = editor != null ? editor.getCaretModel().getLogicalPosition().line : 0;
    if (deleteSelf) {
      if (editor != null) {
        LogicalPosition pos = new LogicalPosition(0, 0);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }
    }

    final PsiCodeBlock newDeclarationScope = PsiTreeUtil.getParentOfType(container, PsiCodeBlock.class, false);
    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(variableName, newDeclarationScope);

    final PsiElement finalAnchorStatement = anchorStatement;
    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          PsiStatement statement = null;
          final boolean isInsideLoop = isLoopOrIf(container);
          if (!isInsideLoop && deleteSelf) {
            statement = (PsiStatement) expr.getParent();
          }
          final PsiExpression expr1 = fieldConflictsResolver.fixInitializer(expr);
          PsiExpression initializer = RefactoringUtil.unparenthesizeExpression(expr1);
          if (expr1 instanceof PsiNewExpression) {
            final PsiNewExpression newExpression = (PsiNewExpression)expr1;
            if (newExpression.getArrayInitializer() != null) {
              initializer = newExpression.getArrayInitializer();
            }
          }
          PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(variableName, type, initializer);
          if (!isInsideLoop) {
            declaration = (PsiDeclarationStatement) container.addBefore(declaration, anchor);
            LOG.assertTrue(expr1.isValid());
            if (deleteSelf) { // never true
              final PsiElement lastChild = statement.getLastChild();
              if (lastChild instanceof PsiComment) { // keep trailing comment
                declaration.addBefore(lastChild, null);
              }
              statement.delete();
              if (editor != null) {
                LogicalPosition pos = new LogicalPosition(line, col);
                editor.getCaretModel().moveToLogicalPosition(pos);
                editor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                editor.getSelectionModel().removeSelection();
              }
            }
          }

          PsiExpression ref = factory.createExpressionFromText(variableName, null);
          if (replaceAll) {
            ArrayList<PsiElement> array = new ArrayList<PsiElement>();
            for (PsiExpression occurrence : occurrences) {
              if (deleteSelf && occurrence.equals(expr)) continue;
              if (occurrence.equals(expr)) {
                occurrence = expr1;
              }
              if (occurrence != null) {
                occurrence = RefactoringUtil.outermostParenthesizedExpression(occurrence);
              }
              if (replaceWrite || !RefactoringUtil.isAssignmentLHS(occurrence)) {
                array.add(occurrence.replace(ref));
              }
            }

            if (editor != null) {
              final PsiElement[] replacedOccurences = array.toArray(new PsiElement[array.size()]);
              highlightReplacedOccurences(project, editor, replacedOccurences);
            }
          } else {
            if (!deleteSelf && replaceSelf) {
              final PsiExpression expr2 = RefactoringUtil.outermostParenthesizedExpression(expr1);
              expr2.replace(ref);
            }
          }

          if(isLoopOrIf(container)) {
            PsiStatement loopBody = getLoopBody(container, finalAnchorStatement);
            PsiStatement loopBodyCopy = loopBody != null ? (PsiStatement) loopBody.copy() : null;
            PsiBlockStatement blockStatement = (PsiBlockStatement) factory.createStatementFromText("{}", null);
            blockStatement = (PsiBlockStatement) CodeStyleManager.getInstance(project).reformat(blockStatement);
            final PsiElement prevSibling = loopBody.getPrevSibling();
            if(prevSibling instanceof PsiWhiteSpace) {
              final PsiElement pprev = prevSibling.getPrevSibling();
              if (!(pprev instanceof PsiComment) || !((PsiComment)pprev).getTokenType().equals(JavaTokenType.END_OF_LINE_COMMENT)) {
                prevSibling.delete();
              }
            }
            blockStatement = (PsiBlockStatement) loopBody.replace(blockStatement);
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            declaration = (PsiDeclarationStatement) codeBlock.add(declaration);
            declaration.getManager().getCodeStyleManager().shortenClassReferences(declaration);
            if (loopBodyCopy != null) codeBlock.add(loopBodyCopy);
          }
          PsiVariable var = (PsiVariable) declaration.getDeclaredElements()[0];
          var.getModifierList().setModifierProperty(PsiModifier.FINAL, declareFinal);

          fieldConflictsResolver.fix();
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
      }, REFACTORING_NAME, null);
    return true;
  }

  private boolean parentStatementNotFound(final Project project) {
    String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
    showErrorMessage(message, project);
    return false;
  }

  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    throw new UnsupportedOperationException();
  }

  private static PsiElement locateAnchor(PsiElement child) {
    while (child != null) {
      PsiElement prev = child.getPrevSibling();
      if (prev instanceof PsiStatement) break;
      if (prev instanceof PsiJavaToken && ((PsiJavaToken)prev).getTokenType() == JavaTokenType.LBRACE) break;
      child = prev;
    }

    while (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
      child = child.getNextSibling();
    }
    return child;
  }

  protected abstract void highlightReplacedOccurences(Project project, Editor editor, PsiElement[] replacedOccurences);

  protected abstract IntroduceVariableSettings getSettings(Project project, Editor editor, PsiExpression expr, final PsiElement[] occurrences,
                                                           boolean anyAssignmentLHS, final boolean declareFinalIfAll, final PsiType type,
                                                           TypeSelectorManagerImpl typeSelectorManager, InputValidator validator);

  protected abstract void showErrorMessage(String message, Project project);

  @Nullable
  private static PsiStatement getLoopBody(PsiElement container, PsiElement anchorStatement) {
    if(container instanceof PsiWhileStatement) {
      return ((PsiWhileStatement) container).getBody();
    } else if(container instanceof PsiForStatement) {
      return ((PsiForStatement) container).getBody();
    } else if (container instanceof PsiForeachStatement) {
      return ((PsiForeachStatement)container).getBody();
    } else if (container instanceof PsiIfStatement) {
      final PsiStatement thenBranch = ((PsiIfStatement)container).getThenBranch();
      if (thenBranch != null && PsiTreeUtil.isAncestor(thenBranch, anchorStatement, false)) {
        return thenBranch;
      }
      final PsiStatement elseBranch = ((PsiIfStatement)container).getElseBranch();
      if (elseBranch != null && PsiTreeUtil.isAncestor(elseBranch, anchorStatement, false)) {
        return elseBranch;
      }
      LOG.assertTrue(false);
    }
    LOG.assertTrue(false);
    return null;
  }


  private static boolean isLoopOrIf(PsiElement element) {
    return element instanceof PsiLoopStatement || element instanceof PsiIfStatement;
  }

  public interface Validator {
    boolean isOK(IntroduceVariableSettings dialog);
  }

  protected abstract boolean reportConflicts(ArrayList<String> conflicts, final Project project);


  public static void checkInLoopCondition(PsiExpression occurence, List<String> conflicts) {
    final PsiElement loopForLoopCondition = RefactoringUtil.getLoopForLoopCondition(occurence);
    if (loopForLoopCondition == null) return;
    final List<PsiVariable> referencedVariables = RefactoringUtil.collectReferencedVariables(occurence);
    final List<PsiVariable> modifiedInBody = new ArrayList<PsiVariable>();
    for (PsiVariable psiVariable : referencedVariables) {
      if (RefactoringUtil.isModifiedInScope(psiVariable, loopForLoopCondition)) {
        modifiedInBody.add(psiVariable);
      }
    }

    if (!modifiedInBody.isEmpty()) {
      for (PsiVariable variable : modifiedInBody) {
        final String message = RefactoringBundle.message("is.modified.in.loop.body", ConflictsUtil.getDescription(variable, false));
        conflicts.add(ConflictsUtil.capitalize(message));
      }
      conflicts.add(RefactoringBundle.message("introducing.variable.may.break.code.logic"));
    }
  }


}
