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
import com.intellij.psi.jsp.JspAction;
import com.intellij.psi.jsp.JspExpression;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.FieldConflictsResolver;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.refactoring.util.occurences.NotInSuperCallOccurenceFilter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class IntroduceVariableBase extends IntroduceHandlerBase implements RefactoringActionHandler {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceVariable.IntroduceVariableBase");
  protected static String REFACTORING_NAME = "Introduce Variable";

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
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
      if (statements != null && statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
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
    if (IntroduceVariableBase.LOG.isDebugEnabled()) {
      IntroduceVariableBase.LOG.debug("expression:" + expr);
    }

    if (expr == null) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Selected block should represent an expression.";
      showErrorMessage(message, project);
      return false;
    }
    final PsiFile file = expr.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");
    final PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();


    PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (originalType == null) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Unknown expression type.";
      showErrorMessage(message, project);
      return false;
    }

    if(originalType == PsiType.VOID) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Selected expression has void type.";
      showErrorMessage(message, project);
      return false;
    }

    PsiElement anchorStatement = RefactoringUtil.getParentStatement(expr, false);
    if (anchorStatement == null) {
        return parentStatementNotFound(project, expr, editor, file);
    }
    if (anchorStatement instanceof PsiExpressionStatement) {
      PsiExpression enclosingExpr = ((PsiExpressionStatement)anchorStatement).getExpression();
      if (enclosingExpr instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)enclosingExpr).resolveMethod();
        if (method != null && method.isConstructor()) {
          //This is either 'this' or 'super', both must be the first in the respective contructor
          String message =
            "Cannot perform the refactoring.\n" +
            "Invalid expression context.";
          showErrorMessage(message, project);
          return false;
        }
      }
    }

    PsiElement tempContainer = anchorStatement.getParent();

    if (invalidContainer(tempContainer)) {
      String message = IntroduceVariableBase.REFACTORING_NAME + " refactoring is not supported in the current context";
      showErrorMessage(message, project);
      return false;
    }

    if(!NotInSuperCallOccurenceFilter.INSTANCE.isOK(expr)) {
      String message = "Cannot introduce variable in super constructor call";
      showErrorMessage(message, project);
      return false;
    }

    if (!file.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, file);
      return false;
    }

    PsiElement containerParent = tempContainer;
    PsiElement lastScope = tempContainer;
    while (true) {
      if (containerParent instanceof PsiFile) break;
      if (containerParent instanceof PsiMethod) break;
      containerParent = containerParent.getParent();
      if (containerParent instanceof PsiCodeBlock
              || containerParent instanceof JspFile
              || containerParent instanceof JspAction
      ) {
        lastScope = containerParent;
      }
    }

    ExpressionOccurenceManager occurenceManager = new ExpressionOccurenceManager(expr, lastScope,
            NotInSuperCallOccurenceFilter.INSTANCE);
    final PsiExpression[] occurrences = occurenceManager.getOccurences();
    final PsiElement anchorStatementIfAll = occurenceManager.getAnchorStatementForAll();
    boolean declareFinalIfAll = occurenceManager.isInFinalContext();


    boolean anyAssignmentLHS = false;
    for (int i = 0; i < occurrences.length; i++) {
      PsiExpression occurrence = occurrences[i];
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        anyAssignmentLHS = true;
        break;
      }
    }


    IntroduceVariableSettings settings = getSettings(project, editor, expr, occurrences, anyAssignmentLHS, declareFinalIfAll,
            originalType,
            new TypeSelectorManagerImpl(project, originalType, expr, occurrences),
            new InputValidator(project, anchorStatementIfAll, anchorStatement, occurenceManager));

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
    if (!IntroduceVariableBase.isLoopOrIf(container)) {
      child = locateAnchor(child);
    }
    final PsiElement anchor = child == null ? anchorStatement : child;

    boolean tempDeleteSelf = false;
    final boolean replaceSelf = replaceWrite || !RefactoringUtil.isAssignmentLHS(expr);
    if (!IntroduceVariableBase.isLoopOrIf(container)) {
      if (expr.getParent() instanceof PsiExpressionStatement && anchor.equals(anchorStatement)) {
        PsiStatement statement = (PsiStatement) expr.getParent();
        if (statement.getParent() instanceof PsiCodeBlock
                || statement.getParent() instanceof JspFile
                || statement.getParent() instanceof JspAction) {
          tempDeleteSelf = true;
        }
      }
      tempDeleteSelf = tempDeleteSelf && replaceSelf;
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
          PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(variableName, type, expr1);
          if (!isInsideLoop) {
            declaration = (PsiDeclarationStatement) container.addBefore(declaration, anchor);
            if (deleteSelf) { // never true
              if (statement.getLastChild() instanceof PsiComment) { // keep trailing comment
                declaration.addBefore(statement.getLastChild(), null);
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
            for (int i = 0; i < occurrences.length; i++) {
              PsiElement occurrence = occurrences[i];
              if (deleteSelf && occurrence.equals(expr)) continue;
              if (occurrence.equals(expr)) {
                occurrence = expr1;
              }
              if (occurrence instanceof PsiExpression) {
                occurrence = RefactoringUtil.outermostParenthesizedExpression((PsiExpression) occurrence);
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

          if(IntroduceVariableBase.isLoopOrIf(container)) {
            PsiStatement loopBody = getLoopBody(container, finalAnchorStatement);
            PsiStatement loopBodyCopy = (PsiStatement) loopBody.copy();
            PsiBlockStatement blockStatement = (PsiBlockStatement) factory.createStatementFromText("{}", null);
            blockStatement = (PsiBlockStatement) CodeStyleManager.getInstance(project).reformat(blockStatement);
            final PsiElement prevSibling = loopBody.getPrevSibling();
            if(prevSibling instanceof PsiWhiteSpace) {
              prevSibling.delete();
            }
            blockStatement = (PsiBlockStatement) loopBody.replace(blockStatement);
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            declaration = (PsiDeclarationStatement) codeBlock.add(declaration);
            declaration.getManager().getCodeStyleManager().shortenClassReferences(declaration);
            codeBlock.add(loopBodyCopy);
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

    protected boolean parentStatementNotFound(final Project project, PsiExpression expr, Editor editor, PsiFile file) {
        String message = IntroduceVariableBase.REFACTORING_NAME + " refactoring is not supported in the current context";
        showErrorMessage(message, project);
        return false;
    }

    /**
     * @fabrique
     * @param tempContainer
     * @return
     */
    protected static boolean invalidContainer(PsiElement tempContainer) {
        return !(tempContainer instanceof PsiCodeBlock)
               && !(tempContainer instanceof JspFile)
               && !(tempContainer instanceof JspAction)
               && !IntroduceVariableBase.isLoopOrIf(tempContainer);
    }

    protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    throw new UnsupportedOperationException();
  }

  private static PsiElement locateAnchor(PsiElement child) {
    while (child != null) {
      PsiElement prev = child.getPrevSibling();
      if (prev instanceof PsiStatement) break;
      if (prev instanceof PsiJavaToken && ((PsiJavaToken)prev).getTokenType() == JavaTokenType.LBRACE) break;
      if (prev instanceof JspToken && ((JspToken) prev).getTokenType() == JspToken.JSP_SCRIPTLET_START) break;
      child = prev;
      if (child instanceof JspToken && ((JspToken) child).getTokenType() == JspToken.JSP_SCRIPTLET_END) break;
    }
    if (!(child instanceof JspToken && ((JspToken) child).getTokenType() == JspToken.JSP_SCRIPTLET_END)) {
      while (true) {
        if (!(child instanceof PsiWhiteSpace)
                && !(child instanceof PsiComment)
                && !(child instanceof JspExpression)
                && !(child instanceof JspToken)
        )
          break;
        child = child.getNextSibling();
      }
    }
    return child;
  }

  protected abstract void highlightReplacedOccurences(Project project, Editor editor, PsiElement[] replacedOccurences);

  protected abstract IntroduceVariableSettings getSettings(Project project, Editor editor, PsiExpression expr, PsiElement[] occurrences, boolean anyAssignmentLHS, boolean declareFinalIfAll, PsiType type, TypeSelectorManagerImpl typeSelectorManager, InputValidator validator);

  protected abstract void showErrorMessage(String message, Project project);

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
    IntroduceVariableBase.LOG.assertTrue(false);
    return null;
  }

  private static boolean isLoopOrIf(PsiElement element) {
    return PsiUtil.isLoopStatement(element)
           || element instanceof PsiIfStatement;
  }

  public interface Validator {
    boolean isOK(IntroduceVariableSettings dialog);
  }

  protected class InputValidator implements Validator {
    private final Project myProject;
    private final PsiElement myAnchorStatementIfAll;
    private final PsiElement myAnchorStatement;
    private final ExpressionOccurenceManager myOccurenceManager;

    public boolean isOK(IntroduceVariableSettings settings) {
      String name = settings.getEnteredName();
      final PsiElement anchor;
      final boolean replaceAllOccurrences = settings.isReplaceAllOccurrences();
      if (replaceAllOccurrences) {
        anchor = myAnchorStatementIfAll;
      } else {
        anchor = myAnchorStatement;
      }
      final PsiElement scope = anchor.getParent();
      if(scope == null) return true;
      final ArrayList<String> conflicts = new ArrayList<String>();
      final HashSet<PsiVariable> reportedVariables = new HashSet<PsiVariable>();
      RenameUtil.CollidingVariableVisitor visitor = new RenameUtil.CollidingVariableVisitor() {
        public void visitCollidingElement(PsiVariable collidingVariable) {
          if (collidingVariable instanceof PsiField) return;
          if (!reportedVariables.contains(collidingVariable)) {
            reportedVariables.add(collidingVariable);
            String message = "Introduced variable will conflict with " + ConflictsUtil.getDescription(collidingVariable, true);
            conflicts.add(message);
          }
        }
      };
      RenameUtil.visitLocalsCollisions(anchor, name, scope, anchor, visitor);
      if (replaceAllOccurrences) {
        final PsiExpression[] occurences = myOccurenceManager.getOccurences();
        for (int i = 0; i < occurences.length; i++) {
          PsiExpression occurence = occurences[i];
          checkInLoopCondition(occurence, conflicts);
        }
      } else {
        checkInLoopCondition(myOccurenceManager.getMainOccurence(), conflicts);
      }

      if (conflicts.size() > 0) {
        return reportConflicts(conflicts, myProject);
      } else {
        return true;
      }
    }


    public InputValidator(Project project, PsiElement anchorStatementIfAll, PsiElement anchorStatement,
                          ExpressionOccurenceManager occurenceManager) {
      myProject = project;
      myAnchorStatementIfAll = anchorStatementIfAll;
      myAnchorStatement = anchorStatement;
      myOccurenceManager = occurenceManager;
    }
  }

  protected abstract boolean reportConflicts(ArrayList<String> conflicts, final Project project);


  public static void checkInLoopCondition(PsiExpression occurence, List<String> conflicts) {
    final PsiElement loopForLoopCondition = RefactoringUtil.getLoopForLoopCondition(occurence);
    if (loopForLoopCondition == null) return;
    final List<PsiVariable> referencedVariables = RefactoringUtil.collectReferencedVariables(occurence);
    final List<PsiVariable> modifiedInBody = new ArrayList<PsiVariable>();
    for (Iterator<PsiVariable> iterator = referencedVariables.iterator(); iterator.hasNext();) {
      PsiVariable psiVariable = iterator.next();
      if (RefactoringUtil.isModifiedInScope(psiVariable, loopForLoopCondition)) {
        modifiedInBody.add(psiVariable);
      }
    }

    if (!modifiedInBody.isEmpty()) {
      for (Iterator<PsiVariable> iterator = modifiedInBody.iterator(); iterator.hasNext();) {
        PsiVariable variable = iterator.next();
        final String message = ConflictsUtil.getDescription(variable, false) + " is modified in loop body.\n";
        conflicts.add(ConflictsUtil.capitalize(message));
      }
      conflicts.add("Introducing variable may break code logic.");
    }
  }


}
