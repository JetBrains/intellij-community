package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.ElementNeedsThis;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.MatchProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ExtractMethodProcessor implements MatchProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractMethod.ExtractMethodProcessor");

  private final Project myProject;
  private final Editor myEditor;
  private final PsiElement[] myElements;
  private final PsiBlockStatement myEnclosingBlockStatement;
  private final PsiType myForcedReturnType;
  private final String myRefactoringName;
  private final String myInitialMethodName;
  private final String myHelpId;

  private final PsiManager myManager;
  private final PsiElementFactory myElementFactory;
  private final CodeStyleManager myStyleManager;

  private PsiExpression myExpression;

  private PsiElement myCodeFragementMember; // parent of myCodeFragment

  private String myMethodName; // name for extracted method
  private PsiType myReturnType; // return type for extracted method
  private PsiTypeParameterList myTypeParameterList; //type parameter list of extracted method
  private ParameterTablePanel.VariableData[] myVariableDatum; // parameter data for extracted method
  private PsiClassType[] myThrownExceptions; // exception to declare as thrown by extracted method
  private boolean myStatic; // whether to declare extracted method static

  private PsiClass myTargetClass; // class to create the extracted method in
  private PsiElement myAnchor; // anchor to insert extracted method after it

  private ControlFlow myControlFlow; // control flow of myCodeFragment
  private int myFlowStart; // offset in control flow corresponding to the start of the code to be extracted
  private int myFlowEnd; // offset in control flow corresponding to position just after the end of the code to be extracted

  private PsiVariable[] myInputVariables; // input variables
  private PsiVariable[] myOutputVariables; // output variables
  private PsiVariable myOutputVariable; // the only output variable
  private List<PsiStatement> myExitStatements;

  private boolean myHasReturnStatement; // there is a return statement
  private boolean myHasReturnStatementOutput; // there is a return statement and its type is not void
  private boolean myHasExpressionOutput; // extracted code is an expression with non-void type
  private boolean myNeedChangeContext; // target class is not immediate container of the code to be extracted

  private boolean myShowErrorDialogs = true;
  private boolean myCanBeStatic;
  private List<Match> myDuplicates;
  private String myMethodVisibility = PsiModifier.PRIVATE;
  private boolean myGenerateConditionalExit;
  private PsiStatement myFirstExitStatementCopy;
  private PsiMethod myExtractedMethod;

  public ExtractMethodProcessor(Project project,
                                Editor editor,
                                PsiElement[] elements,
                                PsiType forcedReturnType,
                                String refactoringName,
                                String initialMethodName,
                                String helpId) {
    myProject = project;
    myEditor = editor;
    if (elements.length != 1 || (elements.length == 1 && !(elements[0] instanceof PsiBlockStatement))) {
      myElements = elements;
      myEnclosingBlockStatement = null;
    }
    else {
      myEnclosingBlockStatement = (PsiBlockStatement)elements[0];
      PsiElement[] codeBlockChildren = myEnclosingBlockStatement.getCodeBlock().getChildren();
      myElements = processCodeBlockChildren(codeBlockChildren);
    }
    myForcedReturnType = forcedReturnType;
    myRefactoringName = refactoringName;
    myInitialMethodName = initialMethodName;
    myHelpId = helpId;

    myManager = PsiManager.getInstance(myProject);
    myElementFactory = myManager.getElementFactory();
    myStyleManager = CodeStyleManager.getInstance(myProject);
  }

  private static PsiElement[] processCodeBlockChildren(PsiElement[] codeBlockChildren) {
    int resultStart = 0;
    int resultLast = codeBlockChildren.length;

    if (codeBlockChildren.length == 0) return PsiElement.EMPTY_ARRAY;

    final PsiElement first = codeBlockChildren[0];
    if (first instanceof PsiJavaToken && ((PsiJavaToken)first).getTokenType() == JavaTokenType.LBRACE) {
      resultStart++;
    }
    final PsiElement last = codeBlockChildren[codeBlockChildren.length - 1];
    if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RBRACE) {
      resultLast--;
    }
    final ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    for (int i = resultStart; i < resultLast; i++) {
      PsiElement element = codeBlockChildren[i];
      if (!(element instanceof PsiWhiteSpace)) {
        result.add(element);
      }
    }

    return result.toArray(new PsiElement[result.size()]);
  }

  /**
   * Method for test purposes
   */
  public void setShowErrorDialogs(boolean showErrorDialogs) {
    myShowErrorDialogs = showErrorDialogs;
  }

  private boolean areExitStatementsTheSame() {
    if (myExitStatements.size() == 0) return false;
    PsiStatement firstExitStatement = myExitStatements.get(0);
    for (int i = 1; i < myExitStatements.size(); i++) {
      PsiStatement statement = myExitStatements.get(i);
      if (!PsiEquivalenceUtil.areElementsEquivalent(firstExitStatement, statement)) return false;
    }

    myFirstExitStatementCopy = (PsiStatement)firstExitStatement.copy();
    return true;
  }

  /**
   * Invoked in atomic action
   */
  public boolean prepare() throws PrepareFailedException {
    myExpression = null;
    if (myElements.length == 1 && myElements[0] instanceof PsiExpression) {
      final PsiExpression expression = (PsiExpression)myElements[0];
      if (expression.getParent() instanceof PsiExpressionStatement) {
        myElements[0] = expression.getParent();
      }
      else {
        myExpression = expression;
      }
    }

    final PsiElement codeFragment = ControlFlowUtil.findCodeFragment(myElements[0]);
    myCodeFragementMember = codeFragment.getParent();

    try {
      myControlFlow = ControlFlowFactory.getControlFlow(codeFragment, new LocalsControlFlowPolicy(codeFragment), false, false);
    }
    catch (AnalysisCanceledException e) {
      throw new PrepareFailedException(RefactoringBundle.message("extract.method.control.flow.analysis.failed"), e.getErrorElement());
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(myControlFlow.toString());
    }

    calculateFlowStartAndEnd();

    IntArrayList exitPoints = new IntArrayList();
    myExitStatements = new ArrayList<PsiStatement>();

    ControlFlowUtil.findExitPointsAndStatements(myControlFlow, myFlowStart, myFlowEnd, exitPoints, myExitStatements,
                                                ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);

    if (LOG.isDebugEnabled()) {
      LOG.debug("exit points:");
      for (int i = 0; i < exitPoints.size(); i++) {
        LOG.debug("  " + exitPoints.get(i));
      }
      LOG.debug("exit statements:");
      for (PsiStatement exitStatement : myExitStatements) {
        LOG.debug("  " + exitStatement);
      }
    }
    if (exitPoints.size() == 0) {
      // if the fragment never exits assume as if it exits in the end
      exitPoints.add(myControlFlow.getEndOffset(myElements[myElements.length - 1]));
    }

    if (exitPoints.size() != 1) {
      if (!areExitStatementsTheSame()) {
        showMultipleExitPointsMessage();
        return false;
      }
      myGenerateConditionalExit = true;
    }
    else {
      myHasReturnStatement = myExpression == null && ControlFlowUtil.returnPresentBetween(myControlFlow, myFlowStart, myFlowEnd);
    }

    myOutputVariables = ControlFlowUtil.getOutputVariables(myControlFlow, myFlowStart, myFlowEnd, exitPoints.toArray());
    if (myGenerateConditionalExit) {
      //variables declared in selected block used in return statements are to be considered output variables when extracting guard methods
      final Set<PsiVariable> outputVariables = new HashSet<PsiVariable>(Arrays.asList(myOutputVariables));
      for (PsiStatement statement : myExitStatements) {
        statement.accept(new PsiRecursiveElementVisitor() {

          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiVariable && isDeclaredInside((PsiVariable)resolved)) {
              outputVariables.add((PsiVariable)resolved);
            }
          }
        });
      }

      myOutputVariables = outputVariables.toArray(new PsiVariable[outputVariables.size()]);
    }

    List<PsiVariable> inputVariables =
      new ArrayList<PsiVariable>(Arrays.asList(ControlFlowUtil.getInputVariables(myControlFlow, myFlowStart, myFlowEnd)));
    if (myGenerateConditionalExit) {
      removeParametersUsedInExitsOnly(codeFragment, myExitStatements, myControlFlow, myFlowStart, myFlowEnd, inputVariables);
    }
    myInputVariables = inputVariables.toArray(new PsiVariable[inputVariables.size()]);

    //varargs variables go last
    Arrays.sort(myInputVariables, new Comparator<PsiVariable>() {
      public int compare(final PsiVariable v1, final PsiVariable v2) {
        return v1.getType() instanceof PsiEllipsisType ? 1 : v2.getType() instanceof PsiEllipsisType ? -1 : 0;
      }
    });


    chooseTargetClass();

    PsiType expressionType = null;
    if (myExpression != null) {
      if (myForcedReturnType != null) {
        expressionType = myForcedReturnType;
      }
      else {
        expressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(myExpression);
      }
    }
    if (expressionType == null) {
      expressionType = PsiType.VOID;
    }
    myHasExpressionOutput = expressionType != PsiType.VOID;

    PsiType returnStatementType = null;
    if (myHasReturnStatement) {
      returnStatementType = myCodeFragementMember instanceof PsiMethod ? ((PsiMethod)myCodeFragementMember).getReturnType() : null;
    }
    myHasReturnStatementOutput = returnStatementType != null && returnStatementType != PsiType.VOID;

    if (!myHasReturnStatementOutput) {
      int outputCount = (myHasExpressionOutput ? 1 : 0) + (myGenerateConditionalExit ? 1 : 0) + myOutputVariables.length;
      if (outputCount > 1) {
        showMultipleOutputMessage(expressionType);
        return false;
      }
    }

    myOutputVariable = myOutputVariables.length > 0 ? myOutputVariables[0] : null;
    if (myHasReturnStatementOutput) {
      myReturnType = returnStatementType;
    }
    else if (myOutputVariable != null) {
      myReturnType = myOutputVariable.getType();
    }
    else if (myGenerateConditionalExit) {
      myReturnType = PsiType.BOOLEAN;
    }
    else {
      myReturnType = expressionType;
    }

    PsiElement container = PsiTreeUtil.getParentOfType(myElements[0], PsiClass.class, PsiMethod.class);
    myTypeParameterList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(container);

    myThrownExceptions = ExceptionUtil.getThrownCheckedExceptions(myElements);
    myStatic = shouldBeStatic();

    if (myTargetClass.getContainingClass() == null || myTargetClass.hasModifierProperty(PsiModifier.STATIC)) {
      ElementNeedsThis needsThis = new ElementNeedsThis(myTargetClass);
      for (int i = 0; i < myElements.length && !needsThis.usesMembers(); i++) {
        PsiElement element = myElements[i];
        element.accept(needsThis);
      }
      myCanBeStatic = !needsThis.usesMembers();
    }
    else {
      myCanBeStatic = false;
    }

    final DuplicatesFinder duplicatesFinder;
    if (myExpression != null) {
      duplicatesFinder = new DuplicatesFinder(myElements, Arrays.asList(myInputVariables), new ArrayList<PsiVariable>());
      myDuplicates = duplicatesFinder.findDuplicates(myTargetClass);
    }
    else {
      duplicatesFinder = new DuplicatesFinder(myElements, Arrays.asList(myInputVariables), Arrays.asList(myOutputVariables));
      myDuplicates = duplicatesFinder.findDuplicates(myTargetClass);
    }

    return true;
  }

  private static void removeParametersUsedInExitsOnly(PsiElement codeFragment,
                                               List<PsiStatement> exitStatements,
                                               ControlFlow controlFlow,
                                               int startOffset,
                                               int endOffset,
                                               List<PsiVariable> inputVariables) {
    LocalSearchScope scope = new LocalSearchScope(codeFragment);
    Variables:
    for (Iterator<? extends PsiVariable> iterator = inputVariables.iterator(); iterator.hasNext();) {
      PsiVariable variable = iterator.next();
      Collection<PsiReference> refs = ReferencesSearch.search(variable, scope).findAll();
      for (PsiReference ref : refs) {
        PsiElement element = ref.getElement();
        int elementOffset = controlFlow.getStartOffset(element);
        if (elementOffset >= startOffset && elementOffset <= endOffset) {
          if (!isInExitStatements(element, exitStatements)) continue Variables;
        }
      }
      iterator.remove();
    }
  }

  private static boolean isInExitStatements(PsiElement element, List<PsiStatement> exitStatements) {
    for (PsiStatement exitStatement : exitStatements) {
      if (PsiTreeUtil.isAncestor(exitStatement, element, false)) return true;
    }
    return false;
  }

  private boolean shouldBeStatic() {
    PsiElement codeFragementMember = myCodeFragementMember;
    while (codeFragementMember != null && PsiTreeUtil.isAncestor(myTargetClass, codeFragementMember, true)) {
      if (((PsiModifierListOwner)codeFragementMember).hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
      codeFragementMember = PsiTreeUtil.getParentOfType(codeFragementMember, PsiModifierListOwner.class, true);
    }
    return false;
  }

  public boolean showDialog() {
    ExtractMethodDialog dialog = new ExtractMethodDialog(myProject, myTargetClass, myInputVariables, myReturnType, myTypeParameterList,
                                                         myThrownExceptions, myStatic, myCanBeStatic, myInitialMethodName,
                                                         myRefactoringName, myHelpId);
    dialog.show();
    if (!dialog.isOK()) return false;
    myMethodName = dialog.getChoosenMethodName();
    myVariableDatum = dialog.getChoosenParameters();
    myStatic |= dialog.isMakeStatic();
    myMethodVisibility = dialog.getVisibility();

    return true;
  }

  public void testRun() throws IncorrectOperationException {
    ExtractMethodDialog dialog = new ExtractMethodDialog(myProject, myTargetClass, myInputVariables, myReturnType, myTypeParameterList,
                                                         myThrownExceptions, myStatic, myCanBeStatic, myInitialMethodName,
                                                         myRefactoringName, myHelpId);
    myMethodName = dialog.getChoosenMethodName();
    myVariableDatum = dialog.getChoosenParameters();
    doRefactoring();
  }

  /**
   * Invoked in command and in atomic action
   */
  public void doRefactoring() throws IncorrectOperationException {
    chooseAnchor();

    int col = myEditor.getCaretModel().getLogicalPosition().column;
    int line = myEditor.getCaretModel().getLogicalPosition().line;
    LogicalPosition pos = new LogicalPosition(0, 0);
    myEditor.getCaretModel().moveToLogicalPosition(pos);

    PsiMethodCallExpression methodCall = doExtract();

    LogicalPosition pos1 = new LogicalPosition(line, col);
    myEditor.getCaretModel().moveToLogicalPosition(pos1);
    int offset = methodCall.getMethodExpression().getTextRange().getStartOffset();
    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();
    myEditor.getSelectionModel().removeSelection();
  }

  private PsiMethodCallExpression doExtract() throws IncorrectOperationException {
    renameInputVariables();

    PsiMethod newMethod = generateEmptyMethod(myThrownExceptions, myStatic);

    LOG.assertTrue(myElements[0].isValid());

    PsiCodeBlock body = newMethod.getBody();
    PsiMethodCallExpression methodCall = generateMethodCall(null);

    LOG.assertTrue(myElements[0].isValid());

    if (myExpression == null) {
      String outVariableName = myOutputVariable != null ? getNewVariableName(myOutputVariable) : null;
      PsiReturnStatement returnStatement;
      if (myOutputVariable != null) {
        returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return " + outVariableName + ";", null);
      }
      else if (myGenerateConditionalExit) {
        returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return true;", null);
      }
      else {
        returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return;", null);
      }

      boolean hasNormalExit = false;
      PsiElement lastElement = myElements[myElements.length - 1];
      if (!(lastElement instanceof PsiReturnStatement || lastElement instanceof PsiBreakStatement ||
            lastElement instanceof PsiContinueStatement)) {
        hasNormalExit = true;
      }

      PsiStatement exitStatementCopy = null;
      // replace all exit-statements such as break's or continue's with appropriate return
      for (PsiStatement exitStatement : myExitStatements) {
        if (exitStatement instanceof PsiReturnStatement) {
          if (!myGenerateConditionalExit) continue;
        }
        else if (exitStatement instanceof PsiBreakStatement) {
          PsiStatement statement = ((PsiBreakStatement)exitStatement).findExitedStatement();
          if (statement == null) continue;
          int startOffset = myControlFlow.getStartOffset(statement);
          int endOffset = myControlFlow.getEndOffset(statement);
          if (myFlowStart <= startOffset && endOffset <= myFlowEnd) continue;
        }
        else if (exitStatement instanceof PsiContinueStatement) {
          PsiStatement statement = ((PsiContinueStatement)exitStatement).findContinuedStatement();
          if (statement == null) continue;
          int startOffset = myControlFlow.getStartOffset(statement);
          int endOffset = myControlFlow.getEndOffset(statement);
          if (myFlowStart <= startOffset && endOffset <= myFlowEnd) continue;
        }
        else {
          LOG.assertTrue(false, exitStatement.toString());
          continue;
        }

        int index = -1;
        for (int j = 0; j < myElements.length; j++) {
          if (exitStatement.equals(myElements[j])) {
            index = j;
            break;
          }
        }
        if (exitStatementCopy == null) {
          exitStatementCopy = (PsiStatement)exitStatement.copy();
        }
        PsiElement result = exitStatement.replace(returnStatement);
        if (index >= 0) {
          myElements[index] = result;
        }
      }

      declareNecessaryVariablesInsideBody(myFlowStart, myFlowEnd, body);

      if (myNeedChangeContext) {
        for (PsiElement element : myElements) {
          ChangeContextUtil.encodeContextInfo(element, false);
        }
      }

      body.addRange(myElements[0], myElements[myElements.length - 1]);
      if (myGenerateConditionalExit) {
        body.add(myElementFactory.createStatementFromText("return false;", null));
      }
      else if (!myHasReturnStatement && hasNormalExit && myOutputVariable != null) {
        body.add(returnStatement);
      }

      if (myGenerateConditionalExit) {
        PsiIfStatement ifStatement = (PsiIfStatement)myElementFactory.createStatementFromText("if (a) b;", null);
        ifStatement = (PsiIfStatement)addToMethodCallLocation(ifStatement);
        methodCall = (PsiMethodCallExpression)ifStatement.getCondition().replace(methodCall);
        ifStatement.getThenBranch().replace(myFirstExitStatementCopy);
      }
      else if (myOutputVariable != null) {
        String name = myOutputVariable.getName();
        boolean toDeclare = isDeclaredInside(myOutputVariable);
        if (!toDeclare) {
          PsiExpressionStatement statement = (PsiExpressionStatement)myElementFactory.createStatementFromText(name + "=x;", null);
          statement = (PsiExpressionStatement)myStyleManager.reformat(statement);
          statement = (PsiExpressionStatement)addToMethodCallLocation(statement);
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
          methodCall = (PsiMethodCallExpression)assignment.getRExpression().replace(methodCall);
        }
        else {
          PsiDeclarationStatement statement =
            myElementFactory.createVariableDeclarationStatement(name, myOutputVariable.getType(), methodCall);
          statement = (PsiDeclarationStatement)addToMethodCallLocation(statement);
          PsiVariable var = (PsiVariable)statement.getDeclaredElements()[0];
          methodCall = (PsiMethodCallExpression)var.getInitializer();
          var.getModifierList().replace(myOutputVariable.getModifierList());
        }
      }
      else if (myHasReturnStatementOutput) {
        PsiStatement statement = myElementFactory.createStatementFromText("return x;", null);
        statement = (PsiStatement)addToMethodCallLocation(statement);
        methodCall = (PsiMethodCallExpression)((PsiReturnStatement)statement).getReturnValue().replace(methodCall);
      }
      else {
        PsiStatement statement = myElementFactory.createStatementFromText("x();", null);
        statement = (PsiStatement)addToMethodCallLocation(statement);
        methodCall = (PsiMethodCallExpression)((PsiExpressionStatement)statement).getExpression().replace(methodCall);
      }
      if (myHasReturnStatement && !myHasReturnStatementOutput && !hasNormalExit) {
        PsiStatement statement = myElementFactory.createStatementFromText("return;", null);
        addToMethodCallLocation(statement);
      }
      else if (!myGenerateConditionalExit && exitStatementCopy != null) {
        addToMethodCallLocation(exitStatementCopy);
      }

      declareNecessaryVariablesAfterCall(myFlowEnd, myOutputVariable);

      deleteExtracted();
    }
    else {
      if (myHasExpressionOutput) {
        PsiReturnStatement returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return x;", null);
        final PsiExpression returnValue;
        returnValue = RefactoringUtil.convertInitializerToNormalExpression(myExpression, myForcedReturnType);
        returnStatement.getReturnValue().replace(returnValue);
        body.add(returnStatement);
      }
      else {
        PsiExpressionStatement statement = (PsiExpressionStatement)myElementFactory.createStatementFromText("x;", null);
        statement.getExpression().replace(myExpression);
        body.add(statement);
      }
      methodCall = (PsiMethodCallExpression)myExpression.replace(methodCall);
    }

    if (myAnchor instanceof PsiField) {
      ((PsiField)myAnchor).normalizeDeclaration();
    }

    adjustFinalParameters(newMethod);

    myExtractedMethod = (PsiMethod)myTargetClass.addAfter(newMethod, myAnchor);
    if (myNeedChangeContext) {
      ChangeContextUtil.decodeContextInfo(myExtractedMethod, myTargetClass, RefactoringUtil.createThisExpression(myManager, null));
    }

    return methodCall;
  }

  private void adjustFinalParameters(final PsiMethod method) throws IncorrectOperationException {
    final IncorrectOperationException[] exc = new IncorrectOperationException[1];
    exc[0] = null;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length > 0) {
      if (CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS) {
        method.accept(new PsiRecursiveElementVisitor() {

          public void visitReferenceExpression(PsiReferenceExpression expression) {
            final PsiElement resolved = expression.resolve();
            if (resolved != null) {
              final int index = ArrayUtil.find(parameters, resolved);
              if (index >= 0) {
                final PsiParameter param = parameters[index];
                if (param.hasModifierProperty(PsiModifier.FINAL) && PsiUtil.isAccessedForWriting(expression)) {
                  try {
                    param.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
                  }
                  catch (IncorrectOperationException e) {
                    exc[0] = e;
                  }
                }
              }
            }
            super.visitReferenceExpression(expression);
          }
        });
      }
      else {
        method.accept(new PsiRecursiveElementVisitor() {
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            final PsiElement resolved = expression.resolve();
            final int index = ArrayUtil.find(parameters, resolved);
            if (index >= 0) {
              final PsiParameter param = parameters[index];
              if (!param.hasModifierProperty(PsiModifier.FINAL) && RefactoringUtil.isInsideAnonymous(expression, method)) {
                try {
                  param.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
                }
                catch (IncorrectOperationException e) {
                  exc[0] = e;
                }
              }
            }
            super.visitReferenceExpression(expression);
          }
        });
      }
      if (exc[0] != null) {
        throw exc[0];
      }
    }
  }

  public List<Match> getDuplicates() {
    return myDuplicates;
  }

  public void processMatch(Match match) throws IncorrectOperationException {
    if (RefactoringUtil.isInStaticContext(match.getMatchStart())) {
      myExtractedMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }
    final PsiMethodCallExpression methodCallExpression = generateMethodCall(match.getInstanceExpression());
    final PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();

    ArrayList<ParameterTablePanel.VariableData> datas = new ArrayList<ParameterTablePanel.VariableData>();
    for (final ParameterTablePanel.VariableData variableData : myVariableDatum) {
      if (variableData.passAsParameter) {
        datas.add(variableData);
      }
    }
    LOG.assertTrue(expressions.length == datas.size());
    for (int i = 0; i < datas.size(); i++) {
      ParameterTablePanel.VariableData data = datas.get(i);
      final PsiElement parameterValue = match.getParameterValue(data.variable);
      expressions[i].replace(parameterValue);
    }
    match.replace(methodCallExpression, myOutputVariable);
  }

  private void deleteExtracted() throws IncorrectOperationException {
    if (myEnclosingBlockStatement == null) {
      myElements[0].getParent().deleteChildRange(myElements[0], myElements[myElements.length - 1]);
    }
    else {
      myEnclosingBlockStatement.delete();
    }
  }

  private PsiElement addToMethodCallLocation(PsiElement statement) throws IncorrectOperationException {
    if (myEnclosingBlockStatement == null) {
      return myElements[0].getParent().addBefore(statement, myElements[0]);
    }
    else {
      return myEnclosingBlockStatement.getParent().addBefore(statement, myEnclosingBlockStatement);
    }
  }


  private void calculateFlowStartAndEnd() {
    int index = 0;
    myFlowStart = -1;
    while (index < myElements.length) {
      myFlowStart = myControlFlow.getStartOffset(myElements[index]);
      if (myFlowStart >= 0) break;
      index++;
    }
    if (myFlowStart < 0) {
      // no executable code
      myFlowStart = 0;
      myFlowEnd = 0;
    }
    else {
      index = myElements.length - 1;
      while (true) {
        myFlowEnd = myControlFlow.getEndOffset(myElements[index]);
        if (myFlowEnd >= 0) break;
        index--;
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("start offset:" + myFlowStart);
      LOG.debug("end offset:" + myFlowEnd);
    }
  }

  private void renameInputVariables() throws IncorrectOperationException {
    for (ParameterTablePanel.VariableData data : myVariableDatum) {
      PsiVariable variable = data.variable;
      if (!data.name.equals(variable.getName())) {
        for (PsiElement element : myElements) {
          RefactoringUtil.renameVariableReferences(variable, data.name, new LocalSearchScope(element));
        }
      }
    }
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  private PsiMethod generateEmptyMethod(PsiClassType[] exceptions, boolean isStatic) throws IncorrectOperationException {
    PsiMethod newMethod = myElementFactory.createMethod(myMethodName, myReturnType);
    newMethod.getModifierList().setModifierProperty(myMethodVisibility, true);
    newMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, isStatic);
    if (myTypeParameterList != null) {
      newMethod.getTypeParameterList().replace(myTypeParameterList);
    }
    PsiCodeBlock body = newMethod.getBody();
    LOG.assertTrue(body != null);

    boolean isFinal = CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS;
    PsiParameterList list = newMethod.getParameterList();
    for (ParameterTablePanel.VariableData data : myVariableDatum) {
      if (data.passAsParameter) {
        PsiParameter parm = myElementFactory.createParameter(data.name, data.type);
        if (isFinal) {
          parm.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }
        list.add(parm);
      }
      else {
        @NonNls StringBuffer buffer = new StringBuffer();
        if (isFinal) {
          buffer.append("final ");
        }
        buffer.append("int ");
        buffer.append(data.name);
        buffer.append("=;");
        String text = buffer.toString();

        PsiDeclarationStatement declaration = (PsiDeclarationStatement)myElementFactory.createStatementFromText(text, null);
        declaration = (PsiDeclarationStatement)myStyleManager.reformat(declaration);
        final PsiTypeElement typeElement = myElementFactory.createTypeElement(data.type);
        ((PsiVariable)declaration.getDeclaredElements()[0]).getTypeElement().replace(typeElement);
        declaration = (PsiDeclarationStatement)body.add(declaration);
      }
    }

    PsiReferenceList throwsList = newMethod.getThrowsList();
    for (PsiClassType exception : exceptions) {
      throwsList.add(myManager.getElementFactory().createReferenceElementByType(exception));
    }

    return (PsiMethod)myStyleManager.reformat(newMethod);
  }

  private PsiMethodCallExpression generateMethodCall(PsiExpression instanceQualifier) throws IncorrectOperationException {
    @NonNls StringBuffer buffer = new StringBuffer();

    final boolean skipInstanceQualifier = instanceQualifier == null || instanceQualifier instanceof PsiThisExpression;
    if (skipInstanceQualifier) {
      if (myNeedChangeContext) {
        boolean needsThisQualifier = false;
        PsiElement parent = myCodeFragementMember;
        while (!myTargetClass.equals(parent)) {
          if (parent instanceof PsiMethod) {
            String methodName = ((PsiMethod)parent).getName();
            if (methodName.equals(myMethodName)) {
              needsThisQualifier = true;
              break;
            }
          }
          parent = parent.getParent();
        }
        if (needsThisQualifier) {
          buffer.append(myTargetClass.getName());
          buffer.append(".this.");
        }
      }
    }
    else {
      buffer.append("qqq.");
    }

    buffer.append(myMethodName);
    buffer.append("(");
    int count = 0;
    for (ParameterTablePanel.VariableData data : myVariableDatum) {
      if (data.passAsParameter) {
        if (count > 0) {
          buffer.append(",");
        }
        buffer.append(data.variable.getName());
        count++;
      }
    }
    buffer.append(")");
    String text = buffer.toString();

    PsiMethodCallExpression expr = (PsiMethodCallExpression)myElementFactory.createExpressionFromText(text, null);
    expr = (PsiMethodCallExpression)myStyleManager.reformat(expr);
    if (!skipInstanceQualifier) {
      PsiExpression qualifierExpression = expr.getMethodExpression().getQualifierExpression();
      LOG.assertTrue(qualifierExpression != null);
      qualifierExpression.replace(instanceQualifier);
    }
    return expr;
  }

  private void declareNecessaryVariablesInsideBody(int start, int end, PsiCodeBlock body) throws IncorrectOperationException {
    PsiVariable[] usedVariables = ControlFlowUtil.getUsedVariables(myControlFlow, start, end);
    for (PsiVariable variable : usedVariables) {
      boolean toDeclare = !isDeclaredInside(variable) && !contains(myInputVariables, variable);
      if (toDeclare) {
        String name = variable.getName();
        PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, variable.getType(), null);
        body.add(statement);
      }
    }
  }

  private void declareNecessaryVariablesAfterCall(int end, PsiVariable outputVariable) throws IncorrectOperationException {
    PsiVariable[] usedVariables = ControlFlowUtil.getUsedVariables(myControlFlow, end, myControlFlow.getSize());
    for (PsiVariable variable : usedVariables) {
      boolean toDeclare = isDeclaredInside(variable) && !variable.equals(outputVariable);
      if (toDeclare) {
        String name = variable.getName();
        PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, variable.getType(), null);
        addToMethodCallLocation(statement);
      }
    }
  }

  private boolean isDeclaredInside(PsiVariable variable) {
    if (variable instanceof ImplicitVariable) return false;
    int startOffset = myElements[0].getTextRange().getStartOffset();
    int endOffset = myElements[myElements.length - 1].getTextRange().getEndOffset();
    PsiIdentifier nameIdentifier = variable.getNameIdentifier();
    if (nameIdentifier == null) return false;
    final TextRange range = nameIdentifier.getTextRange();
    if (range == null) return false;
    int offset = range.getStartOffset();
    return startOffset <= offset && offset <= endOffset;
  }

  private String getNewVariableName(PsiVariable variable) {
    for (ParameterTablePanel.VariableData data : myVariableDatum) {
      if (data.variable.equals(variable)) {
        return data.name;
      }
    }
    return variable.getName();
  }

  private static boolean contains(Object[] array, Object object) {
    for (Object elem : array) {
      if (Comparing.equal(elem, object)) return true;
    }
    return false;
  }

  private void chooseTargetClass() {
    myNeedChangeContext = false;
    myTargetClass = (PsiClass)myCodeFragementMember.getParent();
    if (myTargetClass instanceof PsiAnonymousClass) {
      PsiElement target = myTargetClass.getParent();
      PsiElement targetMember = myTargetClass;
      while (true) {
        if (target instanceof PsiFile) break;
        if (target instanceof PsiClass && !(target instanceof PsiAnonymousClass)) break;
        targetMember = target;
        target = target.getParent();
      }
      if (target instanceof PsiClass) {
        PsiClass newTargetClass = (PsiClass)target;
        List<PsiVariable> array = new ArrayList<PsiVariable>();
        boolean success = true;
        for (PsiElement element : myElements) {
          if (!ControlFlowUtil.collectOuterLocals(array, element, myCodeFragementMember, targetMember)) {
            success = false;
            break;
          }
        }
        if (success) {
          myTargetClass = newTargetClass;
          PsiVariable[] newInputVariables = new PsiVariable[myInputVariables.length + array.size()];
          System.arraycopy(myInputVariables, 0, newInputVariables, 0, myInputVariables.length);
          for (int i = 0; i < array.size(); i++) {
            newInputVariables[myInputVariables.length + i] = array.get(i);
          }
          myInputVariables = newInputVariables;
          myNeedChangeContext = true;
        }
      }
    }
  }

  private void chooseAnchor() {
    myAnchor = myCodeFragementMember;
    while (!myAnchor.getParent().equals(myTargetClass)) {
      myAnchor = myAnchor.getParent();
    }
  }

  private void showMultipleExitPointsMessage() {
    if (myShowErrorDialogs) {
      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      PsiStatement[] exitStatementsArray = myExitStatements.toArray(new PsiStatement[myExitStatements.size()]);
      EditorColorsManager manager = EditorColorsManager.getInstance();
      TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      highlightManager.addOccurrenceHighlights(myEditor, exitStatementsArray, attributes, true, null);
      String message = RefactoringBundle
        .getCannotRefactorMessage(RefactoringBundle.message("there.are.multiple.exit.points.in.the.selected.code.fragment"));
      CommonRefactoringUtil.showErrorMessage(myRefactoringName, message, myHelpId, myProject);
      WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
    }
  }

  private void showMultipleOutputMessage(PsiType expressionType) {
    if (myShowErrorDialogs) {
      StringBuffer buffer = new StringBuffer();
      buffer.append(RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("there.are.multiple.output.values.for.the.selected.code.fragment")));
      buffer.append("\n");
      if (myHasExpressionOutput) {
        buffer.append("    ").append(RefactoringBundle.message("expression.result")).append(": ");
        buffer.append(PsiFormatUtil.formatType(expressionType, 0, PsiSubstitutor.EMPTY));
        buffer.append(",\n");
      }
      if (myGenerateConditionalExit) {
        buffer.append("    ").append(RefactoringBundle.message("boolean.method.result"));
        buffer.append(",\n");
      }
      for (int i = 0; i < myOutputVariables.length; i++) {
        PsiVariable var = myOutputVariables[i];
        buffer.append("    ");
        buffer.append(var.getName());
        buffer.append(" : ");
        buffer.append(PsiFormatUtil.formatType(var.getType(), 0, PsiSubstitutor.EMPTY));
        if (i < myOutputVariables.length - 1) {
          buffer.append(",\n");
        }
        else {
          buffer.append(".");
        }
      }
      CommonRefactoringUtil.showErrorMessage(myRefactoringName, buffer.toString(), myHelpId, myProject);
    }
  }

  public boolean hasDuplicates() {
    final List<Match> duplicates = getDuplicates();
    return duplicates != null && !duplicates.isEmpty();
  }

  @NotNull
  public String getConfirmDuplicatePrompt(Match match) {
    if (RefactoringUtil.isInStaticContext(match.getMatchStart()) && !myExtractedMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return RefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
    }
    return RefactoringBundle.message("replace.this.code.fragment");
  }
}