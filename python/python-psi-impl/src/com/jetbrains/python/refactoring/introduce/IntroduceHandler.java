/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.template.impl.TemplateManagerUtilBase;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.NameSuggesterUtil;
import com.jetbrains.python.refactoring.PyRefactoringUiService;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.PyStringFormatParser.*;
import static com.jetbrains.python.psi.PyUtil.as;

abstract public class IntroduceHandler implements RefactoringActionHandler {
  protected static PsiElement findAnchor(List<? extends PsiElement> occurrences) {
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
  protected static PsiElement findOccurrenceUnderCaret(List<? extends PsiElement> occurrences, Editor editor) {
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
  protected final @DialogTitle String myDialogTitle;

  protected IntroduceHandler(@NotNull final IntroduceValidator validator, @NotNull final @DialogTitle String dialogTitle) {
    myValidator = validator;
    myDialogTitle = dialogTitle;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performAction(new IntroduceOperation(project, editor, file, null));
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
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
    Collection<String> candidates = new LinkedHashSet<>() {
      @Override
      public boolean add(String s) {
        if (PyNames.isReserved(s)) {
          return false;
        }
        return super.add(s);
      }
    };
    String text = PyStringLiteralUtil.getStringValue(expression);
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

    Optional
      .ofNullable(PsiTreeUtil.getParentOfType(expression, PyArgumentList.class))
      .map(PyArgumentList::getCallExpression)
      .ifPresent(
        call -> StreamEx
          .of(call.multiMapArguments(PyResolveContext.defaultContext(context)))
          .map(mapping -> mapping.getMappedParameters().get(expression))
          .nonNull()
          .map(PyCallableParameter::getName)
          .nonNull()
          .forEach(candidates::add)
      );

    return candidates;
  }

  public void performAction(IntroduceOperation operation) {
    final PsiFile file = operation.getFile();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) {
      return;
    }
    final Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      final TemplateState templateState = (TemplateState)TemplateManagerUtilBase.getTemplateState(operation.getEditor());
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
    if (element1 == null || referencesComprehensionIteratorValue(element1)) {
      showCannotPerformError(project, editor);
      return;
    }

    if (singleElementSelection && element1 instanceof PyStringLiteralExpression literal) {
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

  private static boolean referencesComprehensionIteratorValue(@NotNull PsiElement element) {
    PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(element, PyComprehensionElement.class, true);
    if (comprehension != null) {
      List<PyExpression> iteratorVariables = ContainerUtil.map(comprehension.getForComponents(), it -> it.getIteratorVariable());
      List<PsiElement> referencedInSelection = collectReferencedDefinitionsInSameFile(element, element.getContainingFile());
      if (iteratorVariables.contains(element) || ContainerUtil.intersects(referencedInSelection, iteratorVariables)) {
        return true;
      }
    }
    return false;
  }

  protected static @NotNull List<PsiElement> collectReferencedDefinitionsInSameFile(@NotNull PsiElement element, @NotNull PsiFile file) {
    PsiElement selectionElement = getOriginalSelectionCoveringElement(element);
    TextRange textRange = getTextRangeForOperationElement(element);

    return StreamEx.of(PsiTreeUtil.collectElementsOfType(selectionElement, PyReferenceExpression.class))
      .filter(it -> textRange.contains(it.getTextRange()))
      .filter(ref -> !ref.isQualified())
      .flatMap(expr -> PyResolveUtil.resolveLocally(expr).stream())
      .filter(it -> it != null && it.getContainingFile() == file)
      .toList();
  }

  protected static @NotNull TextRange getTextRangeForOperationElement(@NotNull PsiElement operationElement) {
    var userData = operationElement.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (userData == null || userData.first == null || userData.second == null) {
      return operationElement.getTextRange();
    }
    else {
      return userData.second.shiftRight(userData.first.getTextOffset());
    }
  }

  protected static @NotNull PsiElement getOriginalSelectionCoveringElement(@NotNull PsiElement operationElement) {
    var userData = operationElement.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    return userData == null ? operationElement : userData.first;
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
    CommonRefactoringUtil.showErrorHint(project, editor, PyPsiBundle.message("refactoring.introduce.selection.error"), myDialogTitle,
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
      PyRefactoringUiService.getInstance().showIntroduceTargetChooser(operation, editor, expressions, this::performActionOnElement);
      return true;
    }
    return false;
  }

  protected boolean checkIntroduceContext(PsiFile file, Editor editor, PsiElement element) {
    if (!isValidIntroduceContext(element)) {
      CommonRefactoringUtil.showErrorHint(file.getProject(), editor, PyPsiBundle.message("refactoring.introduce.selection.error"),
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
    if (referencesComprehensionIteratorValue(element)) {
      return false;
    }
    return true;
  }

  private void performActionOnElement(IntroduceOperation operation) {
    if (!checkEnabled(operation)) {
      showCanNotIntroduceErrorHint(operation.getProject(), operation.getEditor());
      return;
    }
    final PsiElement element = operation.getElement();
    final PyExpression initializer = getInitializerForElement(element);
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

  protected void showCanNotIntroduceErrorHint(@NotNull Project project, @NotNull Editor editor) {}

  protected void performActionOnElementOccurrences(final IntroduceOperation operation) {
    final Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      ensureName(operation);
      if (operation.isReplaceAll() != null) {
        performInplaceIntroduce(operation);
      }
      else {
        PyRefactoringUiService.getInstance().showOccurrencesChooser(operation, editor, this::performInplaceIntroduce);
      }
    }
    else {
      PyRefactoringUiService.getInstance().performIntroduceWithDialog(operation, myDialogTitle, myValidator, getHelpId(),
                                                                      o -> {
                                                                        PsiElement declaration = performRefactoring(o);
                                                                        final Editor editor1 = o.getEditor();
                                                                        editor1.getCaretModel()
                                                                          .moveToOffset(declaration.getTextRange().getEndOffset());
                                                                        editor1.getSelectionModel().removeSelection();
                                                                      });
    }
  }


  protected @Nullable PyExpression getInitializerForElement(@Nullable PsiElement element) {
    if (element == null) return null;
    final PsiElement parent = element.getParent();
    return parent instanceof PyAssignmentStatement ? ((PyAssignmentStatement)parent).getAssignedValue() :
           element instanceof PyExpression ? (PyExpression)element : null;
  }

  protected void performInplaceIntroduce(IntroduceOperation operation) {
    final PsiElement statement = performRefactoring(operation);
    PyRefactoringUiService.getInstance().performInplaceIntroduceVariable(operation, statement);
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
    private final boolean myPreserveFormatting;

    InitializerTextBuilder(@NotNull PyExpression expression) {
      myPreserveFormatting = shouldPreserveFormatting(expression);
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
    public void visitWhiteSpace(@NotNull PsiWhiteSpace space) {
      final String text = space.getText();
      myResult.append(myPreserveFormatting ? text : text.replace('\n', ' ').replace("\\", ""));
    }

    @Override
    public void visitPyStringLiteralExpression(@NotNull PyStringLiteralExpression node) {
      final Pair<PsiElement, TextRange> data = node.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
      if (data != null) {
        final PsiElement parent = data.getFirst();
        final String text = parent.getText();
        final Pair<String, String> detectedQuotes = PyStringLiteralCoreUtil.getQuotes(text);
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
    public void visitElement(@NotNull PsiElement element) {
      if (element.getChildren().length == 0) {
        myResult.append(element.getText());
      }
      else {
        super.visitElement(element);
      }
    }

    private static boolean shouldPreserveFormatting(@NotNull PyExpression expression) {
      // A collection literal in brackets
      if (expression instanceof PyParenthesizedExpression) {
        return ((PyParenthesizedExpression)expression).getContainedExpression() instanceof PyTupleExpression;
      }
      return expression instanceof PySequenceExpression && !(expression instanceof PyTupleExpression);
    }

    private static boolean needToWrapTopLevelExpressionInParenthesis(@NotNull PyExpression node) {
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
    final SmartPsiElementPointer<PsiElement> result =
      WriteCommandAction.writeCommandAction(project, expression.getContainingFile()).compute(() -> {
        final PsiElement insertedDeclaration;
        try {
          final RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(declaration);
          project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
            .refactoringStarted(getRefactoringId(), afterData);

          insertedDeclaration = addDeclaration(operation, declaration);

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
        return ObjectUtils.doIfNotNull(insertedDeclaration, SmartPointerManager::createPointer);
      });
    return ObjectUtils.doIfNotNull(result, SmartPsiElementPointer::getElement);
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


}
