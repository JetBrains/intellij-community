package com.jetbrains.python.refactoring.extractmethod;

import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.AbstractExtractMethodDialog;
import com.intellij.refactoring.extractMethod.AbstractVariableData;
import com.intellij.refactoring.extractMethod.ExtractMethodDecorator;
import com.intellij.refactoring.extractMethod.ExtractMethodValidator;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author oleg
 */
public class PyExtractMethodUtil {

  public static final String NAME = "extract.method.name";

  private PyExtractMethodUtil() {
  }

  public static void extractFromStatements(final Project project,
                                           final Editor editor,
                                           final CodeFragment fragment,
                                           final PsiElement statement1,
                                           final PsiElement statement2) {
    if (!fragment.getOutputVariables().isEmpty() && fragment.isReturnInstructionInside()) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          "Cannot perform refactoring from expression with local variables modifications and return instructions inside code fragment",
                                          RefactoringBundle.message("error.title"), "refactoring.extractMethod");
      return;
    }

    PyFunction function = PsiTreeUtil.getParentOfType(statement1, PyFunction.class);
    final PyUtil.MethodFlags flags = function == null ? null : PyUtil.MethodFlags.of(function);
    final boolean isClassMethod = flags != null && flags.isClassMethod();
    final boolean isStaticMethod = flags != null && flags.isStaticMethod();

    // collect statements
    final List<PsiElement> elementsRange = PyPsiUtils.collectElements(statement1, statement2);
    if (elementsRange.isEmpty()) {
      return;
    }

    final Pair<String, AbstractVariableData[]> data = getNameAndVariableData(project, fragment, statement1, isClassMethod, isStaticMethod);
    if (data.first == null || data.second == null) {
      return;
    }

    final String methodName = data.first;
    final AbstractVariableData[] variableData = data.second;

    if (fragment.getOutputVariables().isEmpty()) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              // Generate method
              PyFunction generatedMethod = generateMethodFromElements(project, methodName, variableData, elementsRange, flags);
              generatedMethod = insertGeneratedMethod(statement1, generatedMethod);

              // Process parameters
              final PsiElement firstElement = elementsRange.get(0);
              final boolean isMethod = PyPsiUtils.isMethodContext(firstElement);
              processParameters(project, generatedMethod, variableData, isMethod, isClassMethod, isStaticMethod);

              // Generating call element
              final StringBuilder builder = new StringBuilder();
              if (fragment.isReturnInstructionInside()) {
                builder.append("return ");
              }
              if (isMethod) {
                appendSelf(firstElement, builder, isStaticMethod);
              }
              builder.append(methodName);
              builder.append("(").append(createCallArgsString(variableData)).append(")");
              PsiElement callElement = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyCallExpression.class, builder.toString());

              //# replace statements with call
              callElement = replaceElements(elementsRange, callElement);

              // # Set editor
              setSelectionAndCaret(editor, callElement);
            }
          });
        }
      }, "Extract method", null);
    } else {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              // Generate return modified variables statements
              final StringBuilder builder = new StringBuilder();
              for (String s : fragment.getOutputVariables()) {
                if (builder.length() != 0) {
                  builder.append(", ");
                }
                builder.append(s);
              }
              final List<PsiElement> newMethodElements = new ArrayList<PsiElement>(elementsRange);
              final PsiElement returnStatement =
                PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyElement.class, "return " + builder.toString());
              newMethodElements.add(returnStatement);

              // Generate method
              PyFunction generatedMethod = generateMethodFromElements(project, methodName, variableData, newMethodElements, flags);
              generatedMethod = (PyFunction) CodeStyleManager.getInstance(project).reformat(generatedMethod);
              generatedMethod = insertGeneratedMethod(statement1, generatedMethod);

              // Process parameters
              final boolean isMethod = PyPsiUtils.isMethodContext(elementsRange.get(0));
              processParameters(project, generatedMethod, variableData, isMethod, isClassMethod, isStaticMethod);

              // Generate call element
              builder.append(" = ");
              if (isMethod){
                appendSelf(elementsRange.get(0), builder, isStaticMethod);
              }
              builder.append(methodName).append("(");
              builder.append(createCallArgsString(variableData)).append(")");
              PsiElement callElement = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyElement.class, builder.toString());

              // replace statements with call
              callElement = replaceElements(elementsRange, callElement);

              // Set editor
              setSelectionAndCaret(editor, callElement);
            }
          });
        }
      }, "Extract method", null);
    }
  }

  private static void appendSelf(PsiElement firstElement, StringBuilder builder, boolean staticMethod) {
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

  public static void extractFromExpression(final Project project,
                                           final Editor editor,
                                           final CodeFragment fragment,
                                           final PsiElement expression) {
    if (!fragment.getOutputVariables().isEmpty()){
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          "Cannot perform refactoring from expression with local variables modifications inside code fragment",
                                          RefactoringBundle.message("error.title"), "refactoring.extractMethod");
      return;
    }

    if (fragment.isReturnInstructionInside()){
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          "Cannot extract method with return instructions inside code fragment",
                                          RefactoringBundle.message("error.title"), "refactoring.extractMethod");
      return;
    }

    PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
    final PyUtil.MethodFlags flags = function == null ? null : PyUtil.MethodFlags.of(function);
    final boolean isClassMethod = flags != null && flags.isClassMethod();
    final boolean isStaticMethod = flags != null && flags.isClassMethod();

    final Pair<String, AbstractVariableData[]> data = getNameAndVariableData(project, fragment, expression, isClassMethod, isStaticMethod);
    if (data.first == null || data.second == null) {
      return;
    }

    final String methodName = data.first;
    final AbstractVariableData[] variableData = data.second;

    if (fragment.getOutputVariables().isEmpty()) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              // Generate method
              PyFunction generatedMethod = generateMethodFromExpression(project, methodName, variableData, expression, flags);
              generatedMethod = insertGeneratedMethod(expression, generatedMethod);

              // Process parameters
              final boolean isMethod = PyPsiUtils.isMethodContext(expression);
              processParameters(project, generatedMethod, variableData, isMethod, isClassMethod, isStaticMethod);

              // Generating call element
              final StringBuilder builder = new StringBuilder();
              builder.append("return ");
              if (isMethod){
                appendSelf(expression, builder, isStaticMethod);
              }
              builder.append(methodName);
              builder.append("(").append(createCallArgsString(variableData)).append(")");
              final PyReturnStatement returnStatement =
                (PyReturnStatement) PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyElement.class, builder.toString());
              PsiElement callElement = fragment.isReturnInstructionInside() ? returnStatement : returnStatement.getExpression();

              // replace statements with call
              callElement = PyPsiUtils.replaceExpression(project, expression, callElement);

              // Set editor
              setSelectionAndCaret(editor, callElement);
            }
          });
        }
      }, "Extract method", null);
    }
  }

  private static void setSelectionAndCaret(Editor editor, PsiElement callElement) {
    editor.getSelectionModel().removeSelection();
    editor.getCaretModel().moveToOffset(callElement.getTextOffset());
  }

  private static PsiElement replaceElements(final List<PsiElement> elementsRange, PsiElement callElement) {
    callElement = elementsRange.get(0).replace(callElement);
    if (elementsRange.size() > 1) {
      callElement.getParent().deleteChildRange(elementsRange.get(1), elementsRange.get(elementsRange.size() - 1));
    }
    return callElement;
  }

  // Creates string for call
  private static String createCallArgsString(AbstractVariableData[] variableDatas) {
    final StringBuilder builder = new StringBuilder();
    for (AbstractVariableData data : variableDatas) {
      if (data.isPassAsParameter()) {
        if (builder.length() != 0) {
          builder.append(", ");
        }
        builder.append(data.getOriginalName());
      }
    }
    return builder.toString();
  }

  private static void processParameters(final Project project,
                                        final PyFunction generatedMethod,
                                        final AbstractVariableData[] variableData,
                                        final boolean isMethod,
                                        final boolean isClassMethod,
                                        final boolean isStaticMethod) {
    final Map<String, String> map = createMap(variableData);
    // Rename parameters
    for (PyParameter parameter : generatedMethod.getParameterList().getParameters()) {
      final String name = parameter.getName();
      final String newName = map.get(name);
      if (name != null && newName != null && !name.equals(newName)){
        Map<PsiElement, String> allRenames = new java.util.HashMap<PsiElement, String>();
        allRenames.put(parameter, newName);
        UsageInfo[] usages = RenameUtil.findUsages(parameter, newName, false, false, allRenames);
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
    PyFunctionBuilder builder = new PyFunctionBuilder("foo");
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
    final PyParameterList pyParameterList = builder.buildFunction(project, LanguageLevel.getDefault()).getParameterList();
    generatedMethod.getParameterList().replace(pyParameterList);
  }

  private static Map<String, String> createMap(final AbstractVariableData[] variableData) {
    final Map<String, String> map = new HashMap<String, String>();
    for (AbstractVariableData data : variableData) {
      map.put(data.getOriginalName(), data.getName());
    }
    return map;
  }

  private static PyFunction insertGeneratedMethod(PsiElement anchor, final PyFunction generatedMethod) {
    final Pair<PsiElement, TextRange> data = anchor.getUserData(PyPsiUtils.SELECTION_BREAKS_AST_NODE);
    if (data != null) {
      anchor = data.first;
    }
    final PsiNamedElement parent = PsiTreeUtil.getParentOfType(anchor, PyFile.class, PyClass.class, PyFunction.class);

    PsiElement result;
    if (parent instanceof PyFile || parent instanceof PyClass) {
      PsiElement target = parent instanceof PyClass ? ((PyClass)parent).getStatementList() : parent;
      final PsiElement anchorStatement = PyPsiUtils.getStatement(target, anchor);
      result = target.addBefore(generatedMethod, anchorStatement);
    }
    else {
      result = parent.getParent().addBefore(generatedMethod, parent);
    }
    // to ensure correct reformatting, mark the entire method as generated
    result.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        CodeEditUtil.setNodeGenerated(element.getNode(), true);
      }
    });
    return (PyFunction)result;
  }

  private static PyFunction generateMethodFromExpression(final Project project,
                                                         final String methodName,
                                                         final AbstractVariableData[] variableData,
                                                         final PsiElement expression,
                                                         @Nullable final PyUtil.MethodFlags flags) {
    final PyFunctionBuilder builder = new PyFunctionBuilder(methodName);
    addDecorators(builder, flags);
    addFakeParameters(builder, variableData);
    builder.statement("return " + expression.getText());
    return builder.buildFunction(project, LanguageLevel.getDefault());
  }

  private static PyFunction generateMethodFromElements(final Project project,
                                                       final String methodName,
                                                       final AbstractVariableData[] variableData,
                                                       final List<PsiElement> elementsRange,
                                                       @Nullable final PyUtil.MethodFlags flags) {
    assert !elementsRange.isEmpty() : "Empty statements list was selected!";

    final PyFunctionBuilder builder = new PyFunctionBuilder(methodName);
    addDecorators(builder, flags);
    addFakeParameters(builder, variableData);
    final PyFunction method = builder.buildFunction(project, LanguageLevel.getDefault());
    final PyStatementList statementList = method.getStatementList();

    for (PsiElement element : elementsRange) {
      if (element instanceof PsiWhiteSpace){
        continue;
      }
      statementList.add(element);
    }
    // remove last instruction
    statementList.getFirstChild().delete();
    return method;
  }

  private static void addDecorators(PyFunctionBuilder builder, PyUtil.MethodFlags flags) {
    if (flags != null) {
      if (flags.isClassMethod()) {
        builder.decorate(PyNames.CLASSMETHOD);
      }
      else if (flags.isStaticMethod()) {
        builder.decorate(PyNames.STATICMETHOD);
      }
    }
  }

  private static void addFakeParameters(PyFunctionBuilder builder, AbstractVariableData[] variableData) {
    for (AbstractVariableData data : variableData) {
      builder.parameter(data.getOriginalName());
    }
  }

  private static Pair<String, AbstractVariableData[]> getNameAndVariableData(final Project project,
                                                                             final CodeFragment fragment,
                                                                             final PsiElement element,
                                                                             final boolean isClassMethod,
                                                                             final boolean isStaticMethod) {
      final ExtractMethodValidator validator = new PyExtractMethodValidator(element, project);
    if (ApplicationManager.getApplication().isUnitTestMode()){
      String name = System.getProperty(NAME);
      if (name == null){
        name = "foo";
      }
      final String error = validator.check(name);
      if (error != null){
        if (ApplicationManager.getApplication().isUnitTestMode()){
          throw new CommonRefactoringUtil.RefactoringErrorHintException(error);
        }
        final StringBuilder builder = new StringBuilder();
        builder.append(error).append(". ").append(RefactoringBundle.message("do.you.wish.to.continue"));
        if (Messages.showOkCancelDialog(builder.toString(), RefactoringBundle.message("warning.title"), Messages.getWarningIcon()) != 0){
          throw new CommonRefactoringUtil.RefactoringErrorHintException(error);
        }
      }
      final List<AbstractVariableData> data = new ArrayList<AbstractVariableData>();
      for (String in : fragment.getInputVariables()) {
        final AbstractVariableData d = new AbstractVariableData();
        d.name = in+"_new";
        d.originalName = in;
        d.passAsParameter = true;
        data.add(d);
      }
      return Pair.create(name, data.toArray(new AbstractVariableData[data.size()]));
    }

    final boolean isMethod = PyPsiUtils.isMethodContext(element);
    final ExtractMethodDecorator decorator = new ExtractMethodDecorator() {
      public String createMethodPreview(final String methodName, final AbstractVariableData[] variableDatas) {
        final StringBuilder builder = new StringBuilder();
        if (isClassMethod) {
          builder.append("cls");
        }
        else if (isMethod && !isStaticMethod) {
          builder.append("self");
        }
        for (AbstractVariableData variableData : variableDatas) {
          if (variableData.passAsParameter) {
            if (builder.length() != 0) {
              builder.append(", ");
            }
            builder.append(variableData.name);
          }
        }
        builder.insert(0, "(");
        builder.insert(0, methodName);
        builder.insert(0, "def ");
        builder.append(")");
        return builder.toString();
      }
    };

    final AbstractExtractMethodDialog dialog = new AbstractExtractMethodDialog(project, "method_name", fragment, validator, decorator);
    dialog.show();

    //return if don`t want to extract method
    if (!dialog.isOK()) {
      return Pair.create(null, null);
    }

    return Pair.create(dialog.getMethodName(), dialog.getVariableData());
  }

  private static class PyExtractMethodValidator implements ExtractMethodValidator {
    private final PsiElement myElement;
    private final Project myProject;
    private final Function<String, Boolean> myFunction;

    public PyExtractMethodValidator(final PsiElement element, final Project project) {
      myElement = element;
      myProject = project;
      final PsiNamedElement parent = PsiTreeUtil.getParentOfType(myElement, PyFile.class, PyClass.class);
      if (parent instanceof PyFile){
        final List<PyFunction> functions = ((PyFile)parent).getTopLevelFunctions();
        myFunction = new Function<String, Boolean>() {
          public Boolean fun(@NotNull final String s) {
            for (PyFunction function : functions) {
              if (s.equals(function.getName())){
                return false;
              }
            }
            return true;
          }
        };
      } else
      if (parent instanceof PyClass){
        myFunction = new Function<String, Boolean>() {
          public Boolean fun(@NotNull final String s) {
            return ((PyClass) parent).findMethodByName(s, true) == null;
          }
        };
      } else {
        myFunction = null;
      }
    }

    public String check(final String name) {
      if (myFunction != null && !myFunction.fun(name)){
        return PyBundle.message("refactoring.extract.method.error.name.clash");
      }
      return null;
    }

    public boolean isValidName(final String name) {
      return LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance()).isIdentifier(name, myProject);
    }
  }
}

