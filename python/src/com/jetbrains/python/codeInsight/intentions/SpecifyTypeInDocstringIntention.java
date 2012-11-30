package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 * <p/>
 * Helps to specify type
 */
public class SpecifyTypeInDocstringIntention extends TypeIntention {
  private String myText = PyBundle.message("INTN.specify.type");

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type");
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyExpression problemElement = getProblemElement(elementAt);
    PsiReference reference = problemElement == null? null : problemElement.getReference();

    final PsiElement resolved = reference != null? reference.resolve() : null;
    PyParameter parameter = getParameter(problemElement, resolved);
    String kind = parameter != null? "type" : "rtype";

    final Callable callable = getCallable(elementAt);
    if (callable instanceof PyFunction)
      generateDocstring(kind, (PyFunction)callable, problemElement);
  }

  private static void generateDocstring(String kind,
                                        PyFunction pyFunction,
                                        PyExpression problemElement) {
    String name = "rtype".equals(kind)? "" : StringUtil.notNullize(problemElement.getName());

    PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(pyFunction);

    PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
    if (signature != null) {
      docstringGenerator.withParamTypedByQualifiedName(kind, name, signature.getArgTypeQualifiedName(name), pyFunction);
    }
    else {
      docstringGenerator.withParam(kind, name);
    }

    docstringGenerator.build();
    docstringGenerator.startTemplate();
  }

  @Override
  protected void updateText(boolean isReturn) {
    myText = isReturn? PyBundle.message("INTN.specify.return.type") : PyBundle.message("INTN.specify.type");
  }
}