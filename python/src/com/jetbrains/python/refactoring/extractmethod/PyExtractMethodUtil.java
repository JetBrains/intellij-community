// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.extractmethod;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.*;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.AbstractVariableData;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.codeFragment.PyCodeFragment;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author oleg
 */
public class PyExtractMethodUtil {
  public static final String NAME = "extract.method.name";

  private PyExtractMethodUtil() {
  }

  public static void extractFromStatements(@NotNull final Project project,
                                           @NotNull final Editor editor,
                                           @NotNull final PyCodeFragment fragment,
                                           @NotNull final PsiElement statement1,
                                           @NotNull final PsiElement statement2) {
    if (!fragment.getOutputVariables().isEmpty() && fragment.isReturnInstructionInside()) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          PyBundle.message("refactoring.extract.method.error.local.variable.modifications.and.returns"),
                                          RefactoringBundle.message("error.title"), "refactoring.extractMethod");
      return;
    }

    final PyFunction function = PsiTreeUtil.getParentOfType(statement1, PyFunction.class);
    final PyUtil.MethodFlags flags = function == null ? null : PyUtil.MethodFlags.of(function);
    final boolean isClassMethod = flags != null && flags.isClassMethod();
    final boolean isStaticMethod = flags != null && flags.isStaticMethod();

    // collect statements
    final List<PsiElement> elementsRange = PsiTreeUtil.getElementsOfRange(statement1, statement2);
    if (elementsRange.isEmpty()) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          PyBundle.message("refactoring.extract.method.error.empty.fragment"),
                                          RefactoringBundle.message("extract.method.title"), "refactoring.extractMethod");
      return;
    }

    final Pair<String, AbstractVariableData[]> data = getNameAndVariableData(project, fragment, statement1, isClassMethod, isStaticMethod);
    if (data.first == null || data.second == null) {
      return;
    }

    final String methodName = data.first;
    final AbstractVariableData[] variableData = data.second;

    final SimpleDuplicatesFinder finder = new SimpleDuplicatesFinder(statement1, statement2, fragment.getOutputVariables(), variableData);

    CommandProcessor.getInstance().executeCommand(project, () ->  {
      final RefactoringEventData beforeData = new RefactoringEventData();
      beforeData.addElements(new PsiElement[]{statement1, statement2});
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .refactoringStarted(getRefactoringId(), beforeData);

      final StringBuilder builder = new StringBuilder();
      final boolean isAsync = fragment.isAsync();
      if (isAsync) {
        builder.append("async ");
      }
      builder.append("def f():\n    ");
      final List<PsiElement> newMethodElements = new ArrayList<>(elementsRange);
      final boolean hasOutputVariables = !fragment.getOutputVariables().isEmpty();

      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final LanguageLevel languageLevel = LanguageLevel.forElement(statement1);
      if (hasOutputVariables) {
        // Generate return modified variables statements
        final String outputVariables = StringUtil.join(fragment.getOutputVariables(), ", ");
        final String newMethodText = builder + "return " + outputVariables;
        builder.append(outputVariables);

        final PyFunction function1 = generator.createFromText(languageLevel, PyFunction.class, newMethodText);
        final PsiElement returnStatement = function1.getStatementList().getStatements()[0];
        newMethodElements.add(returnStatement);
      }

      // Generate method
      final PyFunction generatedMethod = generateMethodFromElements(project, methodName, variableData, newMethodElements, flags, isAsync);
      final PyFunction insertedMethod = WriteAction.compute(() -> insertGeneratedMethod(statement1, generatedMethod));

      // Process parameters
      final PsiElement firstElement = elementsRange.get(0);
      final boolean isMethod = PyPsiUtils.isMethodContext(firstElement);
      WriteAction.run(() -> {
        processParameters(project, insertedMethod, variableData, isMethod, isClassMethod, isStaticMethod);
        processGlobalWrites(insertedMethod, fragment);
        processNonlocalWrites(insertedMethod, fragment);
      });

      // Generate call element
      if (hasOutputVariables) {
        builder.append(" = ");
      }
      else if (fragment.isReturnInstructionInside()) {
        builder.append("return ");
      }
      if (isAsync) {
        builder.append("await ");
      }
      else if (fragment.isYieldInside()) {
        builder.append("yield from ");
      }
      if (isMethod) {
        appendSelf(firstElement, builder, isStaticMethod);
      }
      builder.append(methodName).append("(");
      builder.append(createCallArgsString(variableData)).append(")");
      final PyFunction function1 = generator.createFromText(languageLevel, PyFunction.class, builder.toString());
      final PsiElement callElement = function1.getStatementList().getStatements()[0];

      // Both statements are used in finder, so should be valid at this moment
      PyPsiUtils.assertValid(statement1);
      PyPsiUtils.assertValid(statement2);
      final List<SimpleMatch> duplicates = collectDuplicates(finder, statement1, insertedMethod);

      // replace statements with call
      PsiElement insertedCallElement = WriteAction.compute(() -> replaceElements(elementsRange, callElement));
      insertedCallElement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedCallElement);

      if (insertedCallElement != null) {
        processDuplicates(duplicates, insertedCallElement, editor);
      }

      // Set editor
      setSelectionAndCaret(editor, insertedCallElement);

      final RefactoringEventData afterData = new RefactoringEventData();
      afterData.addElement(insertedMethod);
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .refactoringDone(getRefactoringId(), afterData);
    }, PyBundle.message("refactoring.extract.method"), null);
  }

  @NotNull
  private static List<SimpleMatch> collectDuplicates(@NotNull SimpleDuplicatesFinder finder,
                                                     @NotNull PsiElement originalScopeAnchor,
                                                     @NotNull PyFunction generatedMethod) {
    final List<PsiElement> scopes = collectScopes(originalScopeAnchor, generatedMethod);
    return ExtractMethodHelper.collectDuplicates(finder, scopes, generatedMethod);
  }

  @NotNull
  private static List<PsiElement> collectScopes(@NotNull PsiElement anchor, @NotNull PyFunction generatedMethod) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(anchor);
    if (owner instanceof PsiFile) return Collections.emptyList();
    final List<PsiElement> scope = new ArrayList<>();
    if (owner instanceof PyFunction) {
      scope.add(owner);
      final PyClass containingClass = ((PyFunction)owner).getContainingClass();
      if (containingClass != null) {
        for (PyFunction function : containingClass.getMethods()) {
          if (!function.equals(owner) && !function.equals(generatedMethod)) {
            scope.add(function);
          }
        }
      }
    }
    return scope;
  }

  private static void processDuplicates(@NotNull List<SimpleMatch> duplicates,
                                        @NotNull PsiElement replacement,
                                        @NotNull Editor editor) {
    ExtractMethodHelper.replaceDuplicates(replacement, editor, pair -> replaceElements(pair.first, pair.second.copy()), duplicates);
  }

  private static void processGlobalWrites(@NotNull final PyFunction function, @NotNull final PyCodeFragment fragment) {
    final Set<String> globalWrites = fragment.getGlobalWrites();
    final Set<String> newGlobalNames = new LinkedHashSet<>();
    final Scope scope = ControlFlowCache.getScope(function);
    for (String name : globalWrites) {
      if (!scope.isGlobal(name)) {
        newGlobalNames.add(name);
      }
    }
    if (!newGlobalNames.isEmpty()) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(function.getProject());
      final PyGlobalStatement globalStatement = generator.createFromText(LanguageLevel.forElement(function),
                                                                         PyGlobalStatement.class,
                                                                         "global " + StringUtil.join(newGlobalNames, ", "));
      final PyStatementList statementList = function.getStatementList();
      statementList.addBefore(globalStatement, statementList.getFirstChild());
    }
  }

  private static void processNonlocalWrites(@NotNull PyFunction function, @NotNull PyCodeFragment fragment) {
    final Set<String> nonlocalWrites = fragment.getNonlocalWrites();
    final Set<String> newNonlocalNames = new LinkedHashSet<>();
    final Scope scope = ControlFlowCache.getScope(function);
    for (String name : nonlocalWrites) {
      if (!scope.isNonlocal(name)) {
        newNonlocalNames.add(name);
      }
    }
    if (!newNonlocalNames.isEmpty()) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(function.getProject());
      final PyNonlocalStatement nonlocalStatement = generator.createFromText(LanguageLevel.forElement(function),
                                                                             PyNonlocalStatement.class,
                                                                             "nonlocal " + StringUtil.join(newNonlocalNames, ", "));
      final PyStatementList statementList = function.getStatementList();
      statementList.addBefore(nonlocalStatement, statementList.getFirstChild());
    }
  }


  private static void appendSelf(@NotNull PsiElement firstElement, @NotNull StringBuilder builder, boolean staticMethod) {
    if (staticMethod) {
      final PyClass containingClass = PsiTreeUtil.getParentOfType(firstElement, PyClass.class);
      assert containingClass != null;
      builder.append(containingClass.getName());
    }
    else {
      builder.append(PyUtil.getFirstParameterName(PsiTreeUtil.getParentOfType(firstElement, PyFunction.class)));
    }
    builder.append(".");
  }

  public static void extractFromExpression(@NotNull final Project project,
                                           @NotNull final Editor editor,
                                           @NotNull final PyCodeFragment fragment,
                                           @NotNull final PsiElement expression) {
    if (!fragment.getOutputVariables().isEmpty()) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          PyBundle.message("refactoring.extract.method.error.local.variable.modifications"),
                                          RefactoringBundle.message("error.title"), "refactoring.extractMethod");
      return;
    }

    if (fragment.isReturnInstructionInside()) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          PyBundle.message("refactoring.extract.method.error.returns"),
                                          RefactoringBundle.message("error.title"), "refactoring.extractMethod");
      return;
    }

    final PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
    final PyUtil.MethodFlags flags = function == null ? null : PyUtil.MethodFlags.of(function);
    final boolean isClassMethod = flags != null && flags.isClassMethod();
    final boolean isStaticMethod = flags != null && flags.isClassMethod();

    final Pair<String, AbstractVariableData[]> data = getNameAndVariableData(project, fragment, expression, isClassMethod, isStaticMethod);
    if (data.first == null || data.second == null) {
      return;
    }

    final String methodName = data.first;
    final AbstractVariableData[] variableData = data.second;

    final SimpleDuplicatesFinder finder = new SimpleDuplicatesFinder(expression, expression, fragment.getOutputVariables(), variableData);
    if (fragment.getOutputVariables().isEmpty()) {
      CommandProcessor.getInstance().executeCommand(project, () -> {
        // Generate method
        final boolean isAsync = fragment.isAsync();
        final PyFunction generatedMethod = generateMethodFromExpression(project, methodName, variableData, expression, flags, isAsync);
        final PyFunction insertedMethod = WriteAction.compute(() -> insertGeneratedMethod(expression, generatedMethod));

        // Process parameters
        final boolean isMethod = PyPsiUtils.isMethodContext(expression);
        WriteAction.run(() -> processParameters(project, insertedMethod, variableData, isMethod, isClassMethod, isStaticMethod));

        // Generating call element
        final StringBuilder builder = new StringBuilder();
        if (isAsync) {
          builder.append("async ");
        }
        builder.append("def f():\n    ");
        if (isAsync) {
          builder.append("await ");
        }
        else if (fragment.isYieldInside()) {
          builder.append("yield from ");
        }
        else {
          builder.append("return ");
        }
        if (isMethod) {
          appendSelf(expression, builder, isStaticMethod);
        }
        builder.append(methodName);
        builder.append("(").append(createCallArgsString(variableData)).append(")");
        final PyElementGenerator generator = PyElementGenerator.getInstance(project);
        final PyFunction function1 = generator.createFromText(LanguageLevel.forElement(expression), PyFunction.class,
                                                              builder.toString());
        final PyElement generated = function1.getStatementList().getStatements()[0];
        final PsiElement callElement;
        if (generated instanceof PyReturnStatement) {
          callElement = ((PyReturnStatement)generated).getExpression();
        }
        else if (generated instanceof PyExpressionStatement) {
          callElement = ((PyExpressionStatement)generated).getExpression();
        }
        else {
          callElement = null;
        }

        PyPsiUtils.assertValid(expression);
        final List<SimpleMatch> duplicates = collectDuplicates(finder, expression, insertedMethod);

        // replace statements with call
        PsiElement insertedCallElement = null;
        if (callElement != null) {
          insertedCallElement = WriteAction.compute(() -> PyReplaceExpressionUtil.replaceExpression(expression, callElement));
          if (insertedCallElement != null) {
            processDuplicates(duplicates, insertedCallElement, editor);
          }
        }
        setSelectionAndCaret(editor, insertedCallElement);
        // Set editor
      }, PyBundle.message("refactoring.extract.method"), null);
    }
  }

  private static void setSelectionAndCaret(@NotNull Editor editor, @Nullable final PsiElement callElement) {
    editor.getSelectionModel().removeSelection();
    if (callElement != null) {
      final int offset = callElement.getTextOffset();
      editor.getCaretModel().moveToOffset(offset);
    }
  }

  @NotNull
  private static PsiElement replaceElements(@NotNull final List<PsiElement> elementsRange, @NotNull PsiElement callElement) {
    callElement = elementsRange.get(0).replace(callElement);
    if (elementsRange.size() > 1) {
      callElement.getParent().deleteChildRange(elementsRange.get(1), elementsRange.get(elementsRange.size() - 1));
    }
    return callElement;
  }

  @NotNull
  private static PsiElement replaceElements(@NotNull final SimpleMatch match, @NotNull final PsiElement element) {
    final List<PsiElement> elementsRange = PsiTreeUtil.getElementsOfRange(match.getStartElement(), match.getEndElement());
    final Map<String, String> changedParameters = match.getChangedParameters();
    PsiElement callElement = element;
    final PyElementGenerator generator = PyElementGenerator.getInstance(callElement.getProject());
    if (element instanceof PyAssignmentStatement) {
      final PyExpression value = ((PyAssignmentStatement)element).getAssignedValue();
      if (value != null) callElement = value;
      final PyExpression[] targets = ((PyAssignmentStatement)element).getTargets();
      if (targets.length == 1) {
        final String output = match.getChangedOutput();
        final PyExpression text = generator.createFromText(LanguageLevel.forElement(callElement), PyAssignmentStatement.class,
                                                           output + " = 1").getTargets()[0];
        targets[0].replace(text);
      }
    }
    if (element instanceof PyExpressionStatement) {
      callElement = ((PyExpressionStatement)element).getExpression();
    }
    if (callElement instanceof PyCallExpression) {
      final Set<String> keys = changedParameters.keySet();
      final PyArgumentList argumentList = ((PyCallExpression)callElement).getArgumentList();
      if (argumentList != null) {
        for (PyExpression arg : argumentList.getArguments()) {
          final String argText = arg.getText();
          if (argText != null && keys.contains(argText)) {
            arg.replace(generator.createExpressionFromText(LanguageLevel.forElement(callElement), changedParameters.get(argText)));
          }
        }
      }
    }
    return replaceElements(elementsRange, element);
  }

  // Creates string for call
  @NotNull
  private static String createCallArgsString(@NotNull final AbstractVariableData[] variableDatas) {
    return StringUtil.join(ContainerUtil.mapNotNull(variableDatas, data -> data.isPassAsParameter() ? data.getOriginalName() : null), ",");
  }

  private static void processParameters(@NotNull final Project project,
                                        @NotNull final PyFunction generatedMethod,
                                        @NotNull final AbstractVariableData[] variableData,
                                        final boolean isMethod,
                                        final boolean isClassMethod,
                                        final boolean isStaticMethod) {
    final Map<String, String> map = createMap(variableData);
    // Rename parameters
    for (PyParameter parameter : generatedMethod.getParameterList().getParameters()) {
      final String name = parameter.getName();
      final String newName = map.get(name);
      if (name != null && newName != null && !name.equals(newName)) {
        final Map<PsiElement, String> allRenames = new java.util.HashMap<>();
        allRenames.put(parameter, newName);
        final UsageInfo[] usages = RenameUtil.findUsages(parameter, newName, false, false, allRenames);
        try {
          RenameUtil.doRename(parameter, newName, usages, project, new RefactoringElementListenerComposite());
        }
        catch (IncorrectOperationException e) {
          RenameUtil.showErrorMessage(e, parameter, project);
          return;
        }
      }
    }
    // Change signature according to pass settings and
    final PyFunctionBuilder builder = new PyFunctionBuilder("foo", generatedMethod);
    if (isClassMethod) {
      builder.parameter("cls");
    }
    else if (isMethod && !isStaticMethod) {
      builder.parameter("self");
    }
    for (AbstractVariableData data : variableData) {
      if (data.isPassAsParameter()) {
        builder.parameter(data.getName());
      }
    }
    final PyParameterList pyParameterList = builder.buildFunction(project, LanguageLevel.forElement(generatedMethod)).getParameterList();
    generatedMethod.getParameterList().replace(pyParameterList);
  }

  @NotNull
  private static Map<String, String> createMap(@NotNull final AbstractVariableData[] variableData) {
    final Map<String, String> map = new HashMap<>();
    for (AbstractVariableData data : variableData) {
      map.put(data.getOriginalName(), data.getName());
    }
    return map;
  }

  @NotNull
  private static PyFunction insertGeneratedMethod(@NotNull PsiElement anchor, @NotNull final PyFunction generatedMethod) {
    final Pair<PsiElement, TextRange> data = anchor.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (data != null) {
      anchor = data.first;
    }
    final PsiNamedElement parent = PsiTreeUtil.getParentOfType(anchor, PyFile.class, PyClass.class, PyFunction.class);

    final PsiElement result;
    // The only safe case to insert extracted function *after* the original scope owner is when it's function.
    if (parent instanceof PyFunction) {
      result = parent.getParent().addAfter(generatedMethod, parent);
    }
    else {
      final PsiElement target = parent instanceof PyClass ? ((PyClass)parent).getStatementList() : parent;
      final PsiElement insertionAnchor = PyPsiUtils.getParentRightBefore(anchor, target);
      assert insertionAnchor != null;
      final List<PsiComment> comments = PyPsiUtils.getPrecedingComments(insertionAnchor);
      result = insertionAnchor.getParent().addBefore(generatedMethod, !comments.isEmpty() ? comments.get(0) : insertionAnchor);
    }
    // to ensure correct reformatting, mark the entire method as generated
    result.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        CodeEditUtil.setNodeGenerated(element.getNode(), true);
      }
    });
    return (PyFunction)result;
  }

  @NotNull
  private static PyFunction generateMethodFromExpression(@NotNull final Project project,
                                                         @NotNull final String methodName,
                                                         @NotNull final AbstractVariableData[] variableData,
                                                         @NotNull final PsiElement expression,
                                                         @Nullable final PyUtil.MethodFlags flags, boolean isAsync) {
    final PyFunctionBuilder builder = new PyFunctionBuilder(methodName, expression);
    addDecorators(builder, flags);
    addFakeParameters(builder, variableData);
    if (isAsync) {
      builder.makeAsync();
    }
    final String text;
    if (expression instanceof PyYieldExpression) {
      text = String.format("(%s)", expression.getText());
    }
    else {
      text = expression.getText();
    }
    builder.statement("return " + text);
    return builder.buildFunction(project, LanguageLevel.forElement(expression));
  }

  @NotNull
  private static PyFunction generateMethodFromElements(@NotNull final Project project,
                                                       @NotNull final String methodName,
                                                       @NotNull final AbstractVariableData[] variableData,
                                                       @NotNull final List<PsiElement> elementsRange,
                                                       @Nullable PyUtil.MethodFlags flags,
                                                       boolean isAsync) {
    assert !elementsRange.isEmpty() : "Empty statements list was selected!";

    final PyFunctionBuilder builder = new PyFunctionBuilder(methodName, elementsRange.get(0));
    if (isAsync) {
      builder.makeAsync();
    }
    addDecorators(builder, flags);
    addFakeParameters(builder, variableData);
    final PyFunction method = builder.buildFunction(project, LanguageLevel.forElement(elementsRange.get(0)));
    final PyStatementList statementList = method.getStatementList();
    for (PsiElement element : elementsRange) {
      if (element instanceof PsiWhiteSpace) {
        continue;
      }
      statementList.add(element);
    }
    // remove last instruction
    final PsiElement child = statementList.getFirstChild();
    if (child != null) {
      child.delete();
    }
    PsiElement last = statementList;
    while (last != null) {
      last = last.getLastChild();
      if (last instanceof PsiWhiteSpace) {
        last.delete();
      }
    }
    return method;
  }

  private static void addDecorators(@NotNull PyFunctionBuilder builder, @Nullable PyUtil.MethodFlags flags) {
    if (flags != null) {
      if (flags.isClassMethod()) {
        builder.decorate(PyNames.CLASSMETHOD);
      }
      else if (flags.isStaticMethod()) {
        builder.decorate(PyNames.STATICMETHOD);
      }
    }
  }

  private static void addFakeParameters(@NotNull PyFunctionBuilder builder, @NotNull AbstractVariableData[] variableData) {
    for (AbstractVariableData data : variableData) {
      builder.parameter(data.getOriginalName());
    }
  }

  @NotNull
  private static Pair<String, AbstractVariableData[]> getNameAndVariableData(@NotNull final Project project,
                                                                             @NotNull final CodeFragment fragment,
                                                                             @NotNull final PsiElement element,
                                                                             final boolean isClassMethod,
                                                                             final boolean isStaticMethod) {
    final ExtractMethodValidator validator = new PyExtractMethodValidator(element, project);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String name = System.getProperty(NAME);
      if (name == null) {
        name = "foo";
      }
      final String error = validator.check(name);
      if (error != null) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          throw new CommonRefactoringUtil.RefactoringErrorHintException(error);
        }
        if (Messages.showOkCancelDialog(error + ". " + RefactoringBundle.message("do.you.wish.to.continue"),
                                        RefactoringBundle.message("warning.title"), Messages.getWarningIcon()) != Messages.OK) {
          throw new CommonRefactoringUtil.RefactoringErrorHintException(error);
        }
      }
      final List<AbstractVariableData> data = new ArrayList<>();
      for (String in : fragment.getInputVariables()) {
        final AbstractVariableData d = new AbstractVariableData();
        d.name = in + "_new";
        d.originalName = in;
        d.passAsParameter = true;
        data.add(d);
      }
      return Pair.create(name, data.toArray(new AbstractVariableData[data.size()]));
    }

    final boolean isMethod = PyPsiUtils.isMethodContext(element);
    final ExtractMethodDecorator<Object> decorator = new ExtractMethodDecorator<Object>() {
      @NotNull
      public String createMethodSignature(@NotNull ExtractMethodSettings<Object> settings) {
        final StringBuilder builder = new StringBuilder();
        if (isClassMethod) {
          builder.append("cls");
        }
        else if (isMethod && !isStaticMethod) {
          builder.append("self");
        }
        for (AbstractVariableData variableData : settings.getAbstractVariableData()) {
          if (variableData.passAsParameter) {
            if (builder.length() != 0) {
              builder.append(", ");
            }
            builder.append(variableData.name);
          }
        }
        builder.insert(0, "(");
        builder.insert(0, settings.getMethodName());
        builder.insert(0, "def ");
        builder.append(")");
        return builder.toString();
      }
    };

    final AbstractExtractMethodDialog<?> dialog = new AbstractExtractMethodDialog<Object>(project, "method_name", fragment,
                                                                                          ArrayUtil.EMPTY_OBJECT_ARRAY, validator,
                                                                                          decorator, PythonFileType.INSTANCE) {
      @Override
      protected String getHelpId() {
        return "python.reference.extractMethod";
      }
    };
    dialog.show();

    //return if don`t want to extract method
    if (!dialog.isOK()) {
      return Pair.empty();
    }

    return Pair.create(dialog.getMethodName(), dialog.getAbstractVariableData());
  }

  @NotNull
  public static String getRefactoringId() {
    return "refactoring.python.extract.method";
  }

  private static class PyExtractMethodValidator implements ExtractMethodValidator {
    private final PsiElement myElement;
    private final Project myProject;
    @Nullable private final Function<String, Boolean> myFunction;

    public PyExtractMethodValidator(final PsiElement element, final Project project) {
      myElement = element;
      myProject = project;
      final ScopeOwner parent = ScopeUtil.getScopeOwner(myElement);
      myFunction = s -> {
        ScopeOwner owner = parent;
        while (owner != null) {
          if (owner instanceof PyClass) {
            if (((PyClass)owner).findMethodByName(s, true, null) != null) {
              return false;
            }
          }
          final Scope scope = ControlFlowCache.getScope(owner);
          if (scope.containsDeclaration(s)) {
            return false;
          }
          owner = ScopeUtil.getScopeOwner(owner);
        }
        return true;
      };
    }

    @Nullable
    public String check(final String name) {
      if (myFunction != null && !myFunction.fun(name)) {
        return PyBundle.message("refactoring.extract.method.error.name.clash");
      }
      return null;
    }

    public boolean isValidName(@NotNull final String name) {
      final NamesValidator validator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance());
      assert validator != null;
      return validator.isIdentifier(name, myProject);
    }
  }
}

