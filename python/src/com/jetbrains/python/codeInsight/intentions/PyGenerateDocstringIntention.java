package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * Intention to add documentation string for function
 * (with checked format)
 */
public class PyGenerateDocstringIntention extends BaseIntentionAction {
  private String myText;

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
    if (function.getDocStringValue() != null) {
      PySignature signature = PySignatureCacheManager.getInstance(project).findSignature(function);

      PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(function);
      addFunctionArguments(function, signature, docstringGenerator);


      if (docstringGenerator.haveParametersToAdd()) {
        myText = PyBundle.message("INTN.add.parameters.to.docstring");
        return true;
      }
      else {
        return false;
      }
    }
    else {
      myText = PyBundle.message("INTN.doc.string.stub");
      return true;
    }
  }

  private static void addFunctionArguments(@NotNull PyFunction function,
                                           @Nullable PySignature signature,
                                           @NotNull PyDocstringGenerator docstringGenerator) {
    for (PyParameter functionParam : function.getParameterList().getParameters()) {
      if (!functionParam.isSelf()) {
        String paramName = functionParam.getName();
        if (!StringUtil.isEmpty(paramName)) {
          assert paramName != null;

          String type;
          if (signature != null) {
            type = signature.getArgTypeQualifiedName(paramName);
          }
          else {
            type = null;
          }
          if (type != null) {
            docstringGenerator.withParamTypedByQualifiedName("type", paramName, type, function);
          }
          else {
            docstringGenerator.withParam("param", paramName);
          }
        }
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

    addFunctionArguments(function, signature, docstringGenerator);

    docstringGenerator.build();
  }
}
