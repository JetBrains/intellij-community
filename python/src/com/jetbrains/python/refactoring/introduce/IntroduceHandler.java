/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.introduce;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.NameSuggesterUtil;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.inspections.PyStringFormatParser.*;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Alexey.Ivanov
 * @author vlan
 */
abstract public class IntroduceHandler implements RefactoringActionHandler {
  protected static PsiElement findAnchor(List<PsiElement> occurrences) {
    PsiElement anchor = occurrences.get(0);
    final Pair<PsiElement, TextRange> data = anchor.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    // Search anchor in the origin file, not in dummy.py, if selection breaks statement and thus element was generated
    if (data != null && occurrences.size() == 1) {
      return PsiTreeUtil.getParentOfType(data.getFirst(), PyStatement.class);
    }
    next:
    do {
      final PyStatement statement = PsiTreeUtil.getParentOfType(anchor, PyStatement.class);
      if (statement != null) {
        final PsiElement parent = statement.getParent();
        for (PsiElement element : occurrences) {
          if (!PsiTreeUtil.isAncestor(parent, element, true)) {
            anchor = statement;
            continue next;
          }
        }
      }
      return statement;
    }
    while (true);
  }

  protected static void ensureName(IntroduceOperation operation) {
    if (operation.getName() == null) {
      final Collection<String> suggestedNames = operation.getSuggestedNames();
      if (suggestedNames.size() > 0) {
        operation.setName(suggestedNames.iterator().next());
      }
      else {
        operation.setName("x");
      }
    }
  }

  @Nullable
  protected static PsiElement findOccurrenceUnderCaret(List<PsiElement> occurrences, Editor editor) {
    if (occurrences.isEmpty()) {
      return null;
    }
    int offset = editor.getCaretModel().getOffset();
    for (PsiElement occurrence : occurrences) {
      if (occurrence != null && occurrence.getTextRange().contains(offset)) {
        return occurrence;
      }
    }
    int line = editor.getDocument().getLineNumber(offset);
    for (PsiElement occurrence : occurrences) {
      PyPsiUtils.assertValid(occurrence);
      if (occurrence.isValid() && editor.getDocument().getLineNumber(occurrence.getTextRange().getStartOffset()) == line) {
        return occurrence;
      }
    }
    for (PsiElement occurrence : occurrences) {
      PyPsiUtils.assertValid(occurrence);
      return occurrence;
    }
    return null;
  }

  public enum InitPlace {
    SAME_METHOD,
    CONSTRUCTOR,
    SET_UP
  }

  @Nullable
  protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation) {
    PyExpressionStatement statement = PsiTreeUtil.getParentOfType(expression, PyExpressionStatement.class);
    if (statement != null) {
      if (statement.getExpression() == expression && expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE) == null) {
        statement.delete();
        return null;
      }
    }
    return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
  }

  private final IntroduceValidator myValidator;
  protected final String myDialogTitle;

  protected IntroduceHandler(@NotNull final IntroduceValidator validator, @NotNull final String dialogTitle) {
    myValidator = validator;
    myDialogTitle = dialogTitle;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performAction(new IntroduceOperation(project, editor, file, null));
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
  }

  public Collection<String> getSuggestedNames(@NotNull final PyExpression expression) {
    Collection<String> candidates = generateSuggestedNames(expression);

    Collection<String> res = new ArrayList<>();
    for (String name : candidates) {
      if (myValidator.checkPossibleName(name, expression)) {
        res.add(name);
      }
    }

    if (res.isEmpty()) {  // no available names found, generate disambiguated suggestions
      for (String name : candidates) {
        int index = 1;
        while (!myValidator.checkPossibleName(name + index, expression)) {
          index++;
        }
        res.add(name + index);
      }
    }

    return res;
  }

  protected Collection<String> generateSuggestedNames(PyExpression expression) {
    Collection<String> candidates = new LinkedHashSet<String>() {
      @Override
      public boolean add(String s) {
        if (PyNames.isReserved(s)) {
          return false;
        }
        return super.add(s);
      }
    };
    String text = expression.getText();
    final Pair<PsiElement, TextRange> selection = expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (selection != null) {
      text = selection.getSecond().substring(selection.getFirst().getText());
    }
    if (expression instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)expression).getCallee();
      if (callee != null) {
        text = callee.getText();
      }
    }
    if (text != null) {
      candidates.addAll(NameSuggesterUtil.generateNames(text));
    }
    final TypeEvalContext context = TypeEvalContext.userInitiated(expression.getProject(), expression.getContainingFile());
    PyType type = context.getType(expression);
    if (type != null && type != PyNoneType.INSTANCE) {
      String typeName = type.getName();
      if (typeName != null) {
        if (type.isBuiltin()) {
          typeName = typeName.substring(0, 1);
        }
        candidates.addAll(NameSuggesterUtil.generateNamesByType(typeName));
      }
    }
    final PyKeywordArgument kwArg = PsiTreeUtil.getParentOfType(expression, PyKeywordArgument.class);
    if (kwArg != null && kwArg.getValueExpression() == expression) {
      candidates.add(kwArg.getKeyword());
    }

    final PyArgumentList argList = PsiTreeUtil.getParentOfType(expression, PyArgumentList.class);
    if (argList != null) {
      final PyCallExpression callExpr = argList.getCallExpression();
      if (callExpr != null) {
        final PyResolveContext resolveContext = PyResolveContext.noImplicits();
        final PyCallExpression.PyArgumentsMapping mapping = callExpr.mapArguments(resolveContext);
        if (mapping.getMarkedCallee() != null) {
          final PyNamedParameter namedParameter = mapping.getMappedParameters().get(expression);
          if (namedParameter != null) {
            candidates.add(namedParameter.getName());
          }
        }
      }
    }
    return candidates;
  }

  public void performAction(IntroduceOperation operation) {
    final PsiFile file = operation.getFile();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) {
      return;
    }
    final Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      final TemplateState templateState = TemplateManagerImpl.getTemplateState(operation.getEditor());
      if (templateState != null && !templateState.isFinished()) {
        return;
      }
    }

    PsiElement element1 = null;
    PsiElement element2 = null;
    final SelectionModel selectionModel = editor.getSelectionModel();
    boolean singleElementSelection = false;
    if (selectionModel.hasSelection()) {
      element1 = file.findElementAt(selectionModel.getSelectionStart());
      element2 = file.findElementAt(selectionModel.getSelectionEnd() - 1);
      if (element1 instanceof PsiWhiteSpace) {
        int startOffset = element1.getTextRange().getEndOffset();
        element1 = file.findElementAt(startOffset);
      }
      if (element2 instanceof PsiWhiteSpace) {
        int endOffset = element2.getTextRange().getStartOffset();
        element2 = file.findElementAt(endOffset - 1);
      }
      if (element1 == element2) {
        singleElementSelection = true;
      }
    }
    else {
      if (smartIntroduce(operation)) {
        return;
      }
      final CaretModel caretModel = editor.getCaretModel();
      final Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(caretModel.getOffset());
      if ((lineNumber >= 0) && (lineNumber < document.getLineCount())) {
        element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
        element2 = file.findElementAt(document.getLineEndOffset(lineNumber) - 1);
      }
    }
    final Project project = operation.getProject();
    if (element1 == null || element2 == null) {
      showCannotPerformError(project, editor);
      return;
    }

    element1 = PyRefactoringUtil.getSelectedExpression(project, file, element1, element2);
    final PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(element1, PyComprehensionElement.class, true);
    if (element1 == null || comprehension != null) {
      showCannotPerformError(project, editor);
      return;
    }

    if (singleElementSelection && element1 instanceof PyStringLiteralExpression) {
      final PyStringLiteralExpression literal = (PyStringLiteralExpression)element1;
      // Currently introduce for substrings of a multi-part string literals is not supported
      if (literal.getStringNodes().size() > 1) {
        showCannotPerformError(project, editor);
        return;
      }
      final int offset = element1.getTextOffset();
      final TextRange selectionRange = TextRange.create(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      final TextRange elementRange = element1.getTextRange();
      if (!elementRange.equals(selectionRange) && elementRange.contains(selectionRange)) {
        final TextRange innerRange = literal.getStringValueTextRange();
        final TextRange intersection = selectionRange.shiftRight(-offset).intersection(innerRange);
        final TextRange finalRange = intersection != null ? intersection : selectionRange;
        final String text = literal.getText();
        if (getFormatValueExpression(literal) != null && breaksStringFormatting(text, finalRange) ||
            getNewStyleFormatValueExpression(literal) != null && breaksNewStyleStringFormatting(text, finalRange) ||
            breaksStringEscaping(text, finalRange)) {
          showCannotPerformError(project, editor);
          return;
        }
        element1.putUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE, Pair.create(element1, finalRange));
      }
    }

    if (!checkIntroduceContext(file, editor, element1)) {
      return;
    }
    operation.setElement(element1);
    performActionOnElement(operation);
  }

  private static boolean breaksStringFormatting(@NotNull String s, @NotNull TextRange range) {
    return breaksRanges(substitutionsToRanges(filterSubstitutions(parsePercentFormat(s))), range);
  }

  private static boolean breaksNewStyleStringFormatting(@NotNull String s, @NotNull TextRange range) {
    return breaksRanges(substitutionsToRanges(filterSubstitutions(parseNewStyleFormat(s))), range);
  }

  private static boolean breaksStringEscaping(@NotNull String s, @NotNull TextRange range) {
    return breaksRanges(getEscapeRanges(s), range);
  }

  private static boolean breaksRanges(@NotNull List<TextRange> ranges, @NotNull TextRange range) {
    for (TextRange r : ranges) {
      if (range.contains(r)) {
        continue;
      }
      if (range.intersectsStrict(r)) {
        return true;
      }
    }
    return false;
  }

  private void showCannotPerformError(Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.introduce.selection.error"), myDialogTitle,
                                        "refactoring.extractMethod");
  }

  private boolean smartIntroduce(final IntroduceOperation operation) {
    final Editor editor = operation.getEditor();
    final PsiFile file = operation.getFile();
    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);
    if ((elementAtCaret instanceof PsiWhiteSpace && offset == elementAtCaret.getTextOffset() || elementAtCaret == null) && offset > 0) {
      elementAtCaret = file.findElementAt(offset - 1);
    }
    if (!checkIntroduceContext(file, editor, elementAtCaret)) return true;
    final List<PyExpression> expressions = new ArrayList<>();
    while (elementAtCaret != null) {
      if (elementAtCaret instanceof PyStatement || elementAtCaret instanceof PyFile) {
        break;
      }
      if (elementAtCaret instanceof PyExpression && isValidIntroduceVariant(elementAtCaret)) {
        expressions.add((PyExpression)elementAtCaret);
      }
      elementAtCaret = elementAtCaret.getParent();
    }
    if (expressions.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      operation.setElement(expressions.get(0));
      performActionOnElement(operation);
      return true;
    }
    else if (expressions.size() > 1) {
      IntroduceTargetChooser.showChooser(editor, expressions, new Pass<PyExpression>() {
        @Override
        public void pass(PyExpression pyExpression) {
          operation.setElement(pyExpression);
          performActionOnElement(operation);
        }
      }, pyExpression -> pyExpression.getText());
      return true;
    }
    return false;
  }

  protected boolean checkIntroduceContext(PsiFile file, Editor editor, PsiElement element) {
    if (!isValidIntroduceContext(element)) {
      CommonRefactoringUtil.showErrorHint(file.getProject(), editor, PyBundle.message("refactoring.introduce.selection.error"),
                                          myDialogTitle, "refactoring.extractMethod");
      return false;
    }
    return true;
  }

  protected boolean isValidIntroduceContext(PsiElement element) {
    final PyDecorator decorator = PsiTreeUtil.getParentOfType(element, PyDecorator.class);
    if (decorator != null && PsiTreeUtil.isAncestor(decorator.getCallee(), element, false)) {
      return false;
    }
    return PsiTreeUtil.getParentOfType(element, PyParameterList.class) == null;
  }

  private static boolean isValidIntroduceVariant(PsiElement element) {
    final PyCallExpression call = as(element.getParent(), PyCallExpression.class);
    if (call != null && call.getCallee() == element) {
      return false;
    }
    final PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(element, PyComprehensionElement.class, true);
    if (comprehension != null) {
      return false;
    }
    return true;
  }

  private void performActionOnElement(IntroduceOperation operation) {
    if (!checkEnabled(operation)) {
      return;
    }
    final PsiElement element = operation.getElement();

    final PsiElement parent = element.getParent();
    final PyExpression initializer = parent instanceof PyAssignmentStatement ?
                                    ((PyAssignmentStatement)parent).getAssignedValue() :
                                    (PyExpression)element;
    operation.setInitializer(initializer);

    if (initializer != null) {
      operation.setOccurrences(getOccurrences(element, initializer));
      operation.setSuggestedNames(getSuggestedNames(initializer));
    }
    if (operation.getOccurrences().size() == 0) {
      operation.setReplaceAll(false);
    }

    performActionOnElementOccurrences(operation);
  }

  protected void performActionOnElementOccurrences(final IntroduceOperation operation) {
    final Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      ensureName(operation);
      if (operation.isReplaceAll() != null) {
        performInplaceIntroduce(operation);
      }
      else {
        OccurrencesChooser.simpleChooser(editor).showChooser(operation.getElement(), operation.getOccurrences(), new Pass<OccurrencesChooser.ReplaceChoice>() {
          @Override
          public void pass(OccurrencesChooser.ReplaceChoice replaceChoice) {
            operation.setReplaceAll(replaceChoice == OccurrencesChooser.ReplaceChoice.ALL);
            performInplaceIntroduce(operation);
          }
        });
      }
    }
    else {
      performIntroduceWithDialog(operation);
    }
  }

  protected void performInplaceIntroduce(IntroduceOperation operation) {
    final PsiElement statement = performRefactoring(operation);
    if (statement instanceof PyAssignmentStatement) {
      PyTargetExpression target = (PyTargetExpression) ((PyAssignmentStatement)statement).getTargets() [0];
      final List<PsiElement> occurrences = operation.getOccurrences();
      final PsiElement occurrence = findOccurrenceUnderCaret(occurrences, operation.getEditor());
      PsiElement elementForCaret = occurrence != null ? occurrence : target;
      operation.getEditor().getCaretModel().moveToOffset(elementForCaret.getTextRange().getStartOffset());
      final InplaceVariableIntroducer<PsiElement> introducer =
              new PyInplaceVariableIntroducer(target, operation, occurrences);
      introducer.performInplaceRefactoring(new LinkedHashSet<>(operation.getSuggestedNames()));
    }
  }

  protected void performIntroduceWithDialog(IntroduceOperation operation) {
    final Project project = operation.getProject();
    if (operation.getName() == null) {
      PyIntroduceDialog dialog = new PyIntroduceDialog(project, myDialogTitle, myValidator, getHelpId(), operation);
      if (!dialog.showAndGet()) {
        return;
      }
      operation.setName(dialog.getName());
      operation.setReplaceAll(dialog.doReplaceAllOccurrences());
      operation.setInitPlace(dialog.getInitPlace());
    }

    PsiElement declaration = performRefactoring(operation);
    final Editor editor = operation.getEditor();
    editor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
    editor.getSelectionModel().removeSelection();
  }

  protected PsiElement performRefactoring(IntroduceOperation operation) {
    PsiElement declaration = createDeclaration(operation);

    declaration = performReplace(declaration, operation);
    declaration = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(declaration);
    return declaration;
  }

  public PyAssignmentStatement createDeclaration(IntroduceOperation operation) {
    final Project project = operation.getProject();
    final PyExpression initializer = operation.getInitializer();
    String assignmentText = operation.getName() + " = " + new InitializerTextBuilder(initializer).result();
    PsiElement anchor = operation.isReplaceAll()
                        ? findAnchor(operation.getOccurrences())
                        : PsiTreeUtil.getParentOfType(initializer, PyStatement.class);
    return createDeclaration(project, assignmentText, anchor);
  }

  private static class InitializerTextBuilder extends PyRecursiveElementVisitor {
    private final StringBuilder myResult = new StringBuilder();

    public InitializerTextBuilder(@NotNull PyExpression expression) {
      if (PsiTreeUtil.findChildOfType(expression, PsiComment.class) != null) {
        myResult.append(expression.getText());
      }
      else {
        expression.accept(this);
      }
      if (needToWrapTopLevelExpressionInParenthesis(expression)) {
        myResult.insert(0, "(").append(")");
      }
    }

    @Override
    public void visitWhiteSpace(PsiWhiteSpace space) {
      myResult.append(space.getText().replace('\n', ' ').replace("\\", ""));
    }

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      final Pair<PsiElement, TextRange> data = node.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
      if (data != null) {
        final PsiElement parent = data.getFirst();
        final String text = parent.getText();
        final Pair<String, String> detectedQuotes = PythonStringUtil.getQuotes(text);
        final Pair<String, String> quotes = detectedQuotes != null ? detectedQuotes : Pair.create("'", "'");
        final TextRange range = data.getSecond();
        final String substring = range.substring(text);
        myResult.append(quotes.getFirst()).append(substring).append(quotes.getSecond());
      }
      else {
        ASTNode child = node.getNode().getFirstChildNode();
        while (child != null) {
          String text = child.getText();
          if (child.getElementType() == TokenType.WHITE_SPACE) {
            if (text.contains("\n")) {
              if (!text.contains("\\")) {
                myResult.append("\\");
              }
              myResult.append(text);
            }
          }
          else {
            myResult.append(text);
          }
          child = child.getTreeNext();
        }
      }
    }

    @Override
    public void visitElement(PsiElement element) {
      if (element.getChildren().length == 0) {
        myResult.append(element.getText());
      }
      else {
        super.visitElement(element);
      }
    }

    private boolean needToWrapTopLevelExpressionInParenthesis(@NotNull PyExpression node) {
      if (node instanceof PyGeneratorExpression) {
        final PsiElement firstChild = node.getFirstChild();
        if (firstChild != null && firstChild.getNode().getElementType() != PyTokenTypes.LPAR) {
          return true;
        }
      }
      return false;
    }

    public String result() {
      return myResult.toString();
    }
  }

  protected abstract String getHelpId();

  protected PyAssignmentStatement createDeclaration(Project project, String assignmentText, PsiElement anchor) {
    LanguageLevel langLevel = ((PyFile) anchor.getContainingFile()).getLanguageLevel();
    return PyElementGenerator.getInstance(project).createFromText(langLevel, PyAssignmentStatement.class, assignmentText);
  }

  protected boolean checkEnabled(IntroduceOperation operation) {
    return true;
  }

  protected List<PsiElement> getOccurrences(PsiElement element, @NotNull final PyExpression expression) {
    return PyRefactoringUtil.getOccurrences(expression, ScopeUtil.getScopeOwner(expression));
  }

  private PsiElement performReplace(@NotNull final PsiElement declaration,
                                    final IntroduceOperation operation) {
    final PyExpression expression = operation.getInitializer();
    final Project project = operation.getProject();
    return new WriteCommandAction<PsiElement>(project, expression.getContainingFile()) {
      protected void run(@NotNull final Result<PsiElement> result) throws Throwable {
        try {
          final RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(declaration);
          project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
            .refactoringStarted(getRefactoringId(), afterData);

          result.setResult(addDeclaration(operation, declaration));

          PyExpression newExpression = createExpression(project, operation.getName(), declaration);

          if (operation.isReplaceAll()) {
            List<PsiElement> newOccurrences = new ArrayList<>();
            for (PsiElement occurrence : operation.getOccurrences()) {
              final PsiElement replaced = replaceExpression(occurrence, newExpression, operation);
              if (replaced != null) {
                newOccurrences.add(replaced);
              }
            }
            operation.setOccurrences(newOccurrences);
          }
          else {
            final PsiElement replaced = replaceExpression(expression, newExpression, operation);
            operation.setOccurrences(Collections.singletonList(replaced));
          }

          postRefactoring(operation.getElement());
        }
        finally {
          final RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(declaration);
          project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
            .refactoringDone(getRefactoringId(), afterData);
        }
      }
    }.execute().getResultObject();
  }

  protected abstract String getRefactoringId();

  @Nullable
  public PsiElement addDeclaration(IntroduceOperation operation, PsiElement declaration) {
    final PsiElement expression = operation.getInitializer();
    final Pair<PsiElement, TextRange> data = expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (data == null) {
      return addDeclaration(expression, declaration, operation);
    }
    else {
      return addDeclaration(data.first, declaration, operation);
    }
  }

  protected PyExpression createExpression(Project project, String name, PsiElement declaration) {
    return PyElementGenerator.getInstance(project).createExpressionFromText(LanguageLevel.forElement(declaration), name);
  }

  @Nullable
  protected abstract PsiElement addDeclaration(@NotNull final PsiElement expression,
                                               @NotNull final PsiElement declaration,
                                               @NotNull IntroduceOperation operation);

  protected void postRefactoring(PsiElement element) {
  }

  private static class PyInplaceVariableIntroducer extends InplaceVariableIntroducer<PsiElement> {
    private final PyTargetExpression myTarget;

    public PyInplaceVariableIntroducer(PyTargetExpression target,
                                       IntroduceOperation operation,
                                       List<PsiElement> occurrences) {
      super(target, operation.getEditor(), operation.getProject(), "Introduce Variable",
            occurrences.toArray(new PsiElement[occurrences.size()]), null);
      myTarget = target;
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myTarget.getContainingFile();
    }
  }
}
