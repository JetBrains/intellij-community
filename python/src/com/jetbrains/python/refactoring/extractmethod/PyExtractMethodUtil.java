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
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.extractmethod.ExtractMethodDecorator;
import com.intellij.refactoring.extractmethod.ExtractMethodDialog;
import com.intellij.refactoring.extractmethod.ExtractMethodValidator;
import com.intellij.refactoring.extractmethod.VariableData;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import java.util.List;
import java.util.Map;

/**
 * @author oleg
 */
public class PyExtractMethodUtil {

  public static void extractFromStatements(final Project project,
                                           final Editor editor,
                                           final CodeFragment fragment,
                                           final PsiElement statement1,
                                           final PsiElement statement2) {
    if (!fragment.getOutputVariables().isEmpty() && fragment.isReturnInstructonInside()) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          "Cannot extract method with non empty output variables and return instructions inside",
                                          RefactoringBundle.message("error.title"), "refactoring.extractMethod");
      return;
    }

    final Pair<String, VariableData[]> data = getNameAndVariableData(project, fragment, statement1);
    if (data.first == null || data.second == null) {
      return;
    }

    // collect statements
    final List<PsiElement> elementsRange = PyPsiUtils.collectElements(statement1, statement2);
    final String methodName = data.first;
    final VariableData[] variableData = data.second;

    if (fragment.getOutputVariables().isEmpty()) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              // Generate method
              PyFunction generatedMethod = generateMethodFromElements(project, methodName, variableData, elementsRange);
              generatedMethod = insertGeneratedMethod(statement1, generatedMethod);

              // Process parameters
              processParameters(project, generatedMethod, variableData);

              // Generating call element
              final StringBuilder builder = new StringBuilder();
              if (fragment.isReturnInstructonInside()) {
                builder.append("return ");
              }
              builder.append(methodName);
              builder.append("(").append(createCallArgsString(variableData)).append(")");
              PsiElement callElement = PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyCallExpression.class, builder.toString());

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
              // Generate method
              PyFunction generatedMethod = generateMethodFromElements(project, methodName, variableData, elementsRange);

              // Append return modified variables statements
              final StringBuilder builder = new StringBuilder();
              for (String s : fragment.getOutputVariables()) {
                if (builder.length() != 0) {
                  builder.append(", ");
                }
                builder.append(s);
              }
              final PsiElement returnStatement = PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyElement.class, "return " + builder.toString());
              generatedMethod.getStatementList().add(returnStatement);

              generatedMethod = insertGeneratedMethod(statement1, generatedMethod);

              // Process parameters
              processParameters(project, generatedMethod, variableData);

              // Generating call element
              builder.append(" = ").append(methodName).append("(");
              builder.append(createCallArgsString(variableData)).append(")");
              PsiElement callElement = PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyElement.class, builder.toString());

              // replace statements with call
              callElement = replaceElements(elementsRange, callElement);

              // # Set editor
              setSelectionAndCaret(editor, callElement);
            }
          });
        }
      }, "Extract method", null);
    }
  }

  public static void extractFromExpression(final Project project,
                                           final Editor editor,
                                           final CodeFragment fragment,
                                           final PsiElement expression) {
    if (!fragment.getOutputVariables().isEmpty() && fragment.isReturnInstructonInside()){
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          "Cannot extract method with non empty output variables and return instructions inside",
                                          RefactoringBundle.message("error.title"), "refactoring.extractMethod");
      return;
    }

    final Pair<String, VariableData[]> data = getNameAndVariableData(project, fragment, expression);
    if (data.first == null || data.second == null) {
      return;
    }

    final String methodName = data.first;
    final VariableData[] variableData = data.second;

    if (fragment.getOutputVariables().isEmpty()) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              // Generate method
              PyFunction generatedMethod = generateMethodFromExpression(project, methodName, variableData, expression);
              generatedMethod = insertGeneratedMethod(expression, generatedMethod);

              // Process parameters
              processParameters(project, generatedMethod, variableData);

              // Generating call element
              final StringBuilder builder = new StringBuilder();
              if (fragment.isReturnInstructonInside()) {
                builder.append("return ");
              }
              builder.append(methodName);
              builder.append("(").append(createCallArgsString(variableData)).append(")");
              PsiElement callElement = PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyElement.class, builder.toString());

              //# replace statements with call
              callElement = PyPsiUtils.replaceExpression(project, expression, callElement);

              // # Set editor
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
  private static String createCallArgsString(VariableData[] variableDatas) {
    final StringBuilder builder = new StringBuilder();
    for (VariableData data : variableDatas) {
      if (data.isPassAsParameter()) {
        if (builder.length() != 0) {
          builder.append(", ");
        }
        builder.append(data.getOriginalName());
      }
    }
    return builder.toString();
  }

  private static void processParameters(final Project project, final PyFunction generatedMethod, final VariableData[] variableData) {
    final Map<String, String> map = new HashMap<String, String>();
    for (VariableData  data : variableData) {
      map.put(data.getOriginalName(), data.getName());
    }
    // Rename parameters
    for (PyParameter parameter : generatedMethod.getParameterList().getParameters()) {
      final String name = parameter.getName();
      final String newName = map.get(name);
      if (name != null && newName != null && !name.equals(newName)){
        RefactoringFactory.getInstance(project).createRename(parameter, newName).run();        
      }
    }
  }

  private static PyFunction insertGeneratedMethod(PsiElement anchor, final PyFunction generatedMethod) {
    final Pair<PsiElement, TextRange> data = anchor.getUserData(PyPsiUtils.SELECTION_BREAKS_AST_NODE);
    if (data != null){
      anchor = data.first;
    }
    final PsiElement compoundStatement = PyPsiUtils.getCompoundStatement(anchor);
    if (compoundStatement.getParent() instanceof PyFunction){
      compoundStatement.getParent().addBefore(generatedMethod, compoundStatement);
      return generatedMethod;
    }
    final PsiElement statement = PyPsiUtils.getStatement(compoundStatement, anchor);
    compoundStatement.addBefore(generatedMethod, statement);
    return generatedMethod;
  }

  //  Creates string for method parameters

  private static String createMethodParamsString(final VariableData[] variableDatas, final boolean fakeSignature) {
    final StringBuilder builder = new StringBuilder();
    for (VariableData data : variableDatas) {
      if (fakeSignature || data.isPassAsParameter()) {
        if (builder.length() != 0) {
          builder.append(", ");
        }
        builder.append(fakeSignature ? data.getOriginalName() : data.getName());
      }
    }
    return builder.toString();
  }

  private static String generateSignature(final String methodName, final VariableData[] variableData, final PsiElement expression) {
    final StringBuilder builder = new StringBuilder();
    builder.append("def ").append(methodName).append("(");
    builder.append(createMethodParamsString(variableData, true));
    builder.append("):\n");
    return builder.toString();
  }

  private static PyFunction generateMethodFromExpression(final Project project,
                                                         final String methodName,
                                                         final VariableData[] variableData,
                                                         final PsiElement expression) {
    final StringBuilder builder = new StringBuilder();
    builder.append(generateSignature(methodName, variableData, expression));
    builder.append(expression.getText());
    return PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyFunction.class, builder.toString());
  }

  private static PyFunction generateMethodFromElements(final Project project,
                                                       final String methodName,
                                                       final VariableData[] variableData,
                                                       final List<PsiElement> elementsRange) {
    final StringBuilder builder = new StringBuilder();
    builder.append(generateSignature(methodName, variableData, elementsRange.get(0)));
    for (PsiElement element : elementsRange) {
      builder.append("  ").append(element.getText());
    }
    return PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyFunction.class, builder.toString());
  }

  private static Pair<String, VariableData[]> getNameAndVariableData(final Project project,
                                                                     final CodeFragment fragment,
                                                                     final PsiElement element) {
    final ExtractMethodValidator validator = new ExtractMethodValidator() {
      public String check(final String name) {
        // TODO[oleg] implement context for name clashes
        return null;
      }

      public boolean isValidName(final String name) {
        return LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance()).isIdentifier(name, project);
      }
    };

    final ExtractMethodDecorator decorator = new ExtractMethodDecorator() {
      public String createMethodPreview(final String methodName, final VariableData[] variableDatas) {
        final StringBuilder builder = new StringBuilder();
        builder.append("def ").append(methodName);
        builder.append("(");
        boolean first = true;
        for (VariableData variableData : variableDatas) {
          if (variableData.passAsParameter) {
            if (first) {
              first = false;
            }
            else {
              builder.append(", ");
            }
            builder.append(variableData.name);
          }
        }
        builder.append(")");
        return builder.toString();
      }
    };

    final ExtractMethodDialog dialog = new ExtractMethodDialog(project, "method_name", fragment, validator, decorator);
    dialog.show();

    //return if don`t want to extract method
    if (!dialog.isOK()) {
      return Pair.create(null, null);
    }

    return Pair.create(dialog.getMethodName(), dialog.getVariableData());
  }

}

