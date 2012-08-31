package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyReturnTypeReference;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 *
 * Helps to specify type
 */
public class SpecifyTypeInDocstringIntention implements IntentionAction {
  private String myText = PyBundle.message("INTN.specify.type");
  public SpecifyTypeInDocstringIntention() {
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset() - 1);
    if (elementAt != null && !(elementAt.getNode().getElementType() == PyTokenTypes.IDENTIFIER))
      elementAt = file.findElementAt(editor.getCaretModel().getOffset());

    PyFunction parentFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    if (parentFunction != null) {
      final ASTNode nameNode = parentFunction.getNameNode();
      if (nameNode != null && nameNode.getPsi() == elementAt) {
        myText = PyBundle.message("INTN.specify.return.type");
        return true;
      }
    }

    PyExpression problemElement = PyUtil.findProblemElement(editor, file, PyNamedParameter.class, PyQualifiedExpression.class);

    if (problemElement == null) return false;
    if (problemElement instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
        problemElement = qualifier;
      }
    }
    if (problemElement.getParent() instanceof PyCallExpression
        || PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null) {
      return false;
    }
    final PyType type = problemElement.getType(TypeEvalContext.slow());
    if (type == null || type instanceof PyReturnTypeReference) {
      PyFunction pyFunction = PsiTreeUtil.getParentOfType(problemElement, PyFunction.class);
      PsiReference reference = problemElement.getReference();
      if (problemElement instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
        if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) reference = qualifier.getReference();
      }
      if (pyFunction != null && (problemElement instanceof PyParameter || reference != null && reference.resolve() instanceof PyParameter)) {
        final String docstring = pyFunction.getDocStringValue();
        if (docstring != null) {
          String name = problemElement.getName();
          if (problemElement instanceof PyQualifiedExpression) {
            final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
            if (qualifier != null)
              name = qualifier.getText();
          }
          if (docstring.contains("type " + name + ":")) return false;
        }
        return true;
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset() - 1);
    if (elementAt != null && !(elementAt.getNode().getElementType() == PyTokenTypes.IDENTIFIER))
      elementAt = file.findElementAt(editor.getCaretModel().getOffset());

    String type = "type";
    String name = "";
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    if (pyFunction != null) {
      final ASTNode nameNode = pyFunction.getNameNode();
      if (nameNode != null && nameNode.getPsi() == elementAt) {
        type = "rtype";
      }
    }

    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(project);
    String prefix = ":";
    if (documentationSettings.isEpydocFormat(file)) {
      prefix = "@";
    }

    PsiReference reference = null;
    PyExpression problemElement = PyUtil.findProblemElement(editor, file, PyNamedParameter.class, PyQualifiedExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (problemElement != null) {
      name = problemElement.getName();

      reference = problemElement.getReference();
      if (problemElement instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
        if (qualifier != null) {
          reference = qualifier.getReference();
          name = qualifier.getText();
        }
      }
      pyFunction = PsiTreeUtil.getParentOfType(problemElement, PyFunction.class);
    }

    final ASTNode nameNode = pyFunction.getNameNode();
    if ((pyFunction != null && (problemElement instanceof PyParameter || reference != null && reference.resolve() instanceof PyParameter)) ||
      elementAt == nameNode.getPsi()) {
      PyStringLiteralExpression docStringExpression = pyFunction.getDocStringExpression();
      int startOffset;
      int endOffset;
      final Pair<String, Integer> replacementToOffset =
          PythonDocumentationProvider.addParamToDocstring(pyFunction, type, name, prefix);

      if (docStringExpression != null) {
        final String typePattern = type + " " + name + ":";
        final int index = docStringExpression.getText().indexOf(typePattern);
        if (index == -1) {
          PyExpression str = elementGenerator.createDocstring(replacementToOffset.getFirst()).getExpression();
          docStringExpression.replace(str);
          startOffset = replacementToOffset.getSecond();
          endOffset = startOffset;
        }
        else {
          startOffset = index + typePattern.length() + 1;
          endOffset = docStringExpression.getText().indexOf("\n", startOffset);
          if (endOffset == -1) endOffset = startOffset;
        }
        pyFunction = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(pyFunction);
        docStringExpression = pyFunction.getDocStringExpression();
      }
      else {
        final PyStatementList list = pyFunction.getStatementList();
        final Document document = editor.getDocument();
        startOffset = replacementToOffset.getSecond();

        if (list != null && list.getStatements().length != 0) {
          if (document.getLineNumber(list.getTextOffset()) == document.getLineNumber(pyFunction.getTextOffset())) {
            PyFunction func = elementGenerator.createFromText(LanguageLevel.forElement(pyFunction),
                                      PyFunction.class, "def " + pyFunction.getName() + pyFunction.getParameterList().getText()
                                      +":\n\t"+replacementToOffset.getFirst() + "\n\t" + list.getText());

            pyFunction = (PyFunction)pyFunction.replace(func);
            startOffset = replacementToOffset.getSecond() + 2;
          }
          else {
            PyExpressionStatement str = elementGenerator.createDocstring(replacementToOffset.getFirst());
            list.addBefore(str, list.getStatements()[0]);
          }
        }

        pyFunction = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(pyFunction);
        docStringExpression = pyFunction.getDocStringExpression();

        endOffset = startOffset;
      }
      assert docStringExpression != null;
      int textOffSet = docStringExpression.getTextOffset();
      editor.getCaretModel().moveToOffset(textOffSet);

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(docStringExpression);
      builder.replaceRange(TextRange.create(startOffset, endOffset), PyNames.OBJECT);
      Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}