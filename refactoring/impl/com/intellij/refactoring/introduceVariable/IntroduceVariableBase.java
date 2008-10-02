/**
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Nov 15, 2002
 * Time: 5:21:33 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.refactoring.util.occurences.NotInSuperCallOccurenceFilter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class IntroduceVariableBase extends IntroduceHandlerBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceVariable.IntroduceVariableBase");
  protected static String REFACTORING_NAME = RefactoringBundle.message("introduce.variable.title");

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    if (!editor.getSelectionModel().hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement[] statementsInRange = findStatementsAtOffset(editor, file, offset);
      if (statementsInRange.length == 1 && PsiUtil.hasErrorElementChild(statementsInRange[0])) {
        editor.getSelectionModel().selectLineAtCaret();
      } else {
        final PsiElement elementAtCaret = file.findElementAt(offset);
        final List<PsiExpression> expressions = new ArrayList<PsiExpression>();
        PsiExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
        while (expression != null) {
          if (!(expression instanceof PsiReferenceExpression) && !(expression instanceof PsiParenthesizedExpression) && !(expression instanceof PsiSuperExpression) && expression.getType() != PsiType.VOID) {
            expressions.add(expression);
          }
          expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
        }
        if (expressions.isEmpty()) {
          editor.getSelectionModel().selectLineAtCaret();
        } else if (expressions.size() == 1) {
          final TextRange textRange = expressions.get(0).getTextRange();
          editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }
        else {
          showChooser(editor, expressions, new Pass<PsiExpression>(){
            public void pass(final PsiExpression selectedValue) {
              invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
            }
          });
          return;
        }
      }
    }
    if (invoke(project, editor, file, editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd())) {
      editor.getSelectionModel().removeSelection();
    }
  }

  public static PsiElement[] findStatementsAtOffset(final Editor editor, final PsiFile file, final int offset) {
    final Document document = editor.getDocument();
    final int lineNumber = document.getLineNumber(offset);
    final int lineStart = document.getLineStartOffset(lineNumber);
    final int lineEnd = document.getLineEndOffset(lineNumber);

    return CodeInsightUtil.findStatementsInRange(file, lineStart, lineEnd);
  }

  public static void showChooser(final Editor editor, final List<PsiExpression> expressions, final Pass<PsiExpression> callback) {
    final ScopeHighlighter highlighter = new ScopeHighlighter(editor);
    final DefaultListModel model = new DefaultListModel();
    for (PsiExpression expr : expressions) {
      model.addElement(expr);
    }
    final JList list = new JList(model);
    list.setCellRenderer(new DefaultListCellRenderer() {
      void appendText(PsiExpression expr, StringBuffer buf) {
        if (expr instanceof PsiNewExpression) {
          final PsiAnonymousClass anonymousClass = ((PsiNewExpression)expr).getAnonymousClass();
          if (anonymousClass != null) {
            buf.append("new ").append(anonymousClass.getBaseClassType().getPresentableText()).append("(...) {...}");
          } else {
            buf.append(expr.getText());
          }
        } else if (expr instanceof PsiReferenceExpression) {
          final PsiExpression qualifierExpression = ((PsiReferenceExpression)expr).getQualifierExpression();
          if (qualifierExpression != null) {
            appendText(qualifierExpression, buf);
            buf.append(".");
          }
          buf.append(((PsiReferenceExpression)expr).getReferenceName());
        } else if (expr instanceof PsiMethodCallExpression) {
          appendText(((PsiMethodCallExpression)expr).getMethodExpression(), buf);
          final PsiExpression[] args = ((PsiMethodCallExpression)expr).getArgumentList().getExpressions();
          if (args.length > 0) {
            buf.append("(...)");
          } else {
            buf.append("()");
          }
        } else {
          buf.append(expr.getText());
        }
      }

      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        final StringBuffer buf = new StringBuffer();
        appendText((PsiExpression)value, buf);
        setText(buf.toString());
        return rendererComponent;
      }
    });

    list.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        highlighter.dropHighlight();
        final int index = list.getSelectedIndex();
        if (index < 0 ) return;
        final PsiExpression expr = (PsiExpression)model.get(index);
        final ArrayList<PsiElement> toExtract = new ArrayList<PsiElement>();
        toExtract.add(expr);
        highlighter.highlight(expr, toExtract);
      }
    });

    JBPopupFactory.getInstance().createListPopupBuilder(list)
          .setTitle("Expressions")
          .setMovable(false)
          .setResizable(false)
          .setRequestFocus(true)
          .setItemChoosenCallback(new Runnable() {
                                    public void run() {
                                      callback.pass((PsiExpression)list.getSelectedValue());
                                    }
                                  })
          .addListener(new JBPopupAdapter() {
                          @Override
                          public void onClosed(JBPopup popup) {
                            highlighter.dropHighlight();
                          }
                       })
          .createPopup().showInBestPositionFor(editor);
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

    if (tempExpr == null) {
      tempExpr = getSelectedExpression(project, file, startOffset, endOffset);
    }
    return invokeImpl(project, tempExpr, editor);
  }

  public static PsiExpression getSelectedExpression(final Project project, final PsiFile file, final int startOffset, final int endOffset) {
    PsiExpression tempExpr;
    final PsiElement elementAt = PsiTreeUtil.findCommonParent(file.findElementAt(startOffset), file.findElementAt(endOffset - 1));
    final PsiLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(elementAt, PsiLiteralExpression.class);
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    try {
      String text = file.getText().subSequence(startOffset, endOffset).toString();
      String prefix = null;
      String suffix = null;
      if (literalExpression != null) {

        final String stripped = StringUtil.stripQuotesAroundValue(text);

        boolean primitive = false;
        if (stripped.equals("true") || stripped.equals("false")) {
          primitive = true;
        } else {
          try {
            Integer.parseInt(stripped);
            primitive = true;
          }
          catch (NumberFormatException e1) {
            //then not primitive
          }
        }

        text = primitive ? stripped : ("\"" + stripped + "\"");

        final int offset = literalExpression.getTextOffset();
        if (offset + 1 < startOffset) {
          prefix = "\" + ";
        }

        if (offset + literalExpression.getTextLength() - 1 > endOffset) {
          suffix = " + \"";
        }
      } else {
        text = text.trim();
      }

      tempExpr = elementFactory.createExpressionFromText(text, file);

      tempExpr.putUserData(ElementToWorkOn.PREFIX, prefix);
      tempExpr.putUserData(ElementToWorkOn.SUFFIX, suffix);

      tempExpr.putUserData(ElementToWorkOn.TEXT_RANGE, FileDocumentManager.getInstance().getDocument(file.getVirtualFile()).createRangeMarker(startOffset, endOffset));

      tempExpr.putUserData(ElementToWorkOn.PARENT, literalExpression != null ? literalExpression : elementAt);
    }
    catch (IncorrectOperationException e) {
      tempExpr = null;
    }
    return tempExpr;
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
      showErrorMessage(project, editor, message);
      return false;
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();


    PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (originalType == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("unknown.expression.type"));
      showErrorMessage(project, editor, message);
      return false;
    }

    if(originalType == PsiType.VOID) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(project, editor, message);
      return false;
    }


    final PsiElement physicalElement = expr.getUserData(ElementToWorkOn.PARENT);

    PsiElement anchorStatement = RefactoringUtil.getParentStatement(physicalElement != null ? physicalElement : expr, false);

    if (anchorStatement == null) {
      return parentStatementNotFound(project, editor);
    }
    if (anchorStatement instanceof PsiExpressionStatement) {
      PsiExpression enclosingExpr = ((PsiExpressionStatement)anchorStatement).getExpression();
      if (enclosingExpr instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)enclosingExpr).resolveMethod();
        if (method != null && method.isConstructor()) {
          //This is either 'this' or 'super', both must be the first in the respective contructor
          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invalid.expression.context"));
          showErrorMessage(project, editor, message);
          return false;
        }
      }
    }

    PsiElement tempContainer = anchorStatement.getParent();

    if (!(tempContainer instanceof PsiCodeBlock) && !isLoopOrIf(tempContainer)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
      showErrorMessage(project, editor, message);
      return false;
    }

    if(!NotInSuperCallOccurenceFilter.INSTANCE.isOK(expr)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.introduce.variable.in.super.constructor.call"));
      showErrorMessage(project, editor, message);
      return false;
    }

    final PsiFile file = anchorStatement.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");

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
        LogicalPosition pos = new LogicalPosition(line, col);
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
              replace(expr1, ref, file);
            }
          }

          declaration = (PsiDeclarationStatement) putStatementInLoopBody(declaration, container, finalAnchorStatement);
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

  public static PsiElement replace(final PsiExpression expr1, final PsiExpression ref, final PsiFile file)
    throws IncorrectOperationException {
    final PsiExpression expr2 = RefactoringUtil.outermostParenthesizedExpression(expr1);
    if (expr2.isPhysical()) {
      return expr2.replace(ref);
    }
    else {
      final String prefix  = expr1.getUserData(ElementToWorkOn.PREFIX);
      final String suffix  = expr1.getUserData(ElementToWorkOn.SUFFIX);
      final PsiElement parent = expr1.getUserData(ElementToWorkOn.PARENT);
      final RangeMarker rangeMarker = expr1.getUserData(ElementToWorkOn.TEXT_RANGE);

      final String allText = parent.getContainingFile().getText();
      final TextRange parentRange = parent.getTextRange();

      String beg = allText.substring(parentRange.getStartOffset(), rangeMarker.getStartOffset());
      if (StringUtil.stripQuotesAroundValue(beg).length() == 0) beg = "";

      String end = allText.substring(rangeMarker.getEndOffset(), parentRange.getEndOffset());
      if (StringUtil.stripQuotesAroundValue(end).length() == 0) end = "";

      final String text = beg + (prefix != null ? prefix : "") + ref.getText() + (suffix != null ? suffix : "") + end;
      final PsiExpression el = JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createExpressionFromText(text, file);
      return parent.replace(el);
    }
  }

  public static PsiStatement putStatementInLoopBody(PsiStatement declaration, PsiElement container, PsiElement finalAnchorStatement)
    throws IncorrectOperationException {
    if(isLoopOrIf(container)) {
      PsiStatement loopBody = getLoopBody(container, finalAnchorStatement);
      PsiStatement loopBodyCopy = loopBody != null ? (PsiStatement) loopBody.copy() : null;
      PsiBlockStatement blockStatement = (PsiBlockStatement)JavaPsiFacade.getInstance(container.getProject()).getElementFactory()
        .createStatementFromText("{}", null);
      blockStatement = (PsiBlockStatement) CodeStyleManager.getInstance(container.getProject()).reformat(blockStatement);
      final PsiElement prevSibling = loopBody.getPrevSibling();
      if(prevSibling instanceof PsiWhiteSpace) {
        final PsiElement pprev = prevSibling.getPrevSibling();
        if (!(pprev instanceof PsiComment) || !((PsiComment)pprev).getTokenType().equals(JavaTokenType.END_OF_LINE_COMMENT)) {
          prevSibling.delete();
        }
      }
      blockStatement = (PsiBlockStatement) loopBody.replace(blockStatement);
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      declaration = (PsiStatement) codeBlock.add(declaration);
      JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);
      if (loopBodyCopy != null) codeBlock.add(loopBodyCopy);
    }
    return declaration;
  }

  private boolean parentStatementNotFound(final Project project, Editor editor) {
    String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
    showErrorMessage(project, editor, message);
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

  protected abstract void showErrorMessage(Project project, Editor editor, String message);

  @Nullable
  private static PsiStatement getLoopBody(PsiElement container, PsiElement anchorStatement) {
    if(container instanceof PsiLoopStatement) {
      return ((PsiLoopStatement) container).getBody();
    }
    else if (container instanceof PsiIfStatement) {
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


  public static boolean isLoopOrIf(PsiElement element) {
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
        final String message = RefactoringBundle.message("is.modified.in.loop.body", RefactoringUIUtil.getDescription(variable, false));
        conflicts.add(ConflictsUtil.capitalize(message));
      }
      conflicts.add(RefactoringBundle.message("introducing.variable.may.break.code.logic"));
    }
  }


}
