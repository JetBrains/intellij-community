package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 * Intention to add documentation string for function
 * (with checked format)
 */
public class PyGenerateDocstringIntention extends BaseIntentionAction {
  private String myText = PyBundle.message("INTN.doc.string.stub");

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.doc.string.stub");
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (elementAt == null) {
      return false;
    }
    PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    if (function == null) {
      return false;
    }
    return isAvailableForFunction(project, elementAt, function);
  }

  private boolean isAvailableForFunction(Project project, PsiElement elementAt, PyFunction function) {
    PySignature signature = PySignatureCacheManager.getInstance(project).findSignature(function);
    if (signature != null) {
      if (function.getDocStringValue() != null) {
        PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(function);
        addFunctionArguments(function, signature, docstringGenerator);


        if (docstringGenerator.haveParametersToAdd()) {
          myText = PyBundle.message("INTN.add.types.to.docstring");
          return true;
        }
      }
      else {
        myText = PyBundle.message("INTN.generate.docstring.with.types");
        return true;
      }
    }

    PyStatementList list = PsiTreeUtil.getParentOfType(elementAt, PyStatementList.class,
                                                       false, PyFunction.class);
    if (list == null) {
      if (function.getDocStringExpression() != null) {
        return false;
      }
      final PyStatementList statementList = function.getStatementList();
      if (statementList != null && statementList.getStatements().length != 0) {
        return true;
      }
    }
    return false;
  }

  private static void addFunctionArguments(PyFunction function, PySignature signature, PyDocstringGenerator docstringGenerator) {
    for (PySignature.NamedParameter param : signature.getArgs()) {
      PyParameter functionParameter = function.getParameterList().findParameterByName(param.getName());
      if (functionParameter != null && !functionParameter.isSelf()) {
        docstringGenerator.withParamTypedByQualifiedName("type", param.getName(), param.getTypeQualifiedName(), function);
      }
    }
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(file)) {
      return;
    }

    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);

    if (function == null) {
      return;
    }

    generateDocstringForFunction(project, editor, function);
  }

  public static void generateDocstringForFunction(Project project, Editor editor, PyFunction function) {
    PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(function);

    PySignature signature = PySignatureCacheManager.getInstance(project).findSignature(function);

    if (signature != null && function.getParameterList().getParameters().length > 0) {

      addFunctionArguments(function, signature, docstringGenerator);

      docstringGenerator.build();
    }
    else {
      PythonDocumentationProvider.insertDocStub(function, project, editor);
    }
  }
}
