package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
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
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class GenerateDocstringWIthTypesIntention implements IntentionAction {
  private String myText = PyBundle.message("INTN.generate.docstring.with.types");

  public GenerateDocstringWIthTypesIntention() {
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.generate.docstring.with.types");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myText = PyBundle.message("INTN.generate.docstring.with.types");
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (elementAt == null) return false;
    PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    if (function == null || function.getDocStringValue() != null) {
      return false;
    }
    PySignature signature = PySignatureCacheManager.getInstance(project).findSignature(function);
    if (signature == null) {
      return false;
    }

    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);

    if (function == null) {
      return;
    }

    PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(function);

    PySignature signature = PySignatureCacheManager.getInstance(project).findSignature(function);

    if (signature == null) {
      return;
    }

    for (PySignature.NamedParameter param : signature.getArgs()) {
      docstringGenerator.withParam("type", param.getName(), getShortestImportableName(function, param.getType()));
    }

    docstringGenerator.build();
  }

  private static String getShortestImportableName(PsiElement anchor, String type) {
    final PyType pyType = PyTypeParser.getTypeByName(anchor, type);
    if (pyType instanceof PyClassType) {
      PyClass c = ((PyClassType)pyType).getPyClass();
      return c.getQualifiedName();
    }

    if (pyType != null) {
      return pyType.getName();
    }
    else {
      return type;
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}