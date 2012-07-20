package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class DocstringQuickFix implements LocalQuickFix {

  PyParameter myMissing;
  String myMissingText = "";
  String myUnexpected;
  String myPrefix;

  public DocstringQuickFix(PyParameter missing, String unexpected) {
    myMissing = missing;
    if (myMissing != null) {
      if (myMissing.getText().startsWith("*")) {
        myMissingText = myMissing.getText();
      }
      else {
        myMissingText = myMissing.getName();
      }
    }
    myUnexpected = unexpected;
  }

  @NotNull
  public String getName() {
    if (myMissing != null) {
      return PyBundle.message("QFIX.docstring.add.$0", myMissingText);
    }
    else if (myUnexpected != null){
      return PyBundle.message("QFIX.docstring.remove.$0", myUnexpected);
    }
    else  {
      return PyBundle.message("QFIX.docstring.insert.stub");
    }
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nullable
  private static Editor getEditor(Project project, PsiFile file) {
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      final EditorFactory instance = EditorFactory.getInstance();
      if (instance == null) return null;
      Editor[] editors = instance.getEditors(document);
      if (editors.length > 0)
        return editors[0];
    }
    return null;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyDocStringOwner.class);
    if (docStringOwner == null) return;
    PyStringLiteralExpression docStringExpression = docStringOwner.getDocStringExpression();
    if (docStringExpression == null && myMissing == null && myUnexpected == null) {
      if (docStringOwner instanceof PyFunction) {
        PythonDocumentationProvider.inserDocStub((PyFunction)docStringOwner, project, getEditor(project, docStringOwner.getContainingFile()));
      }
      if (docStringOwner instanceof PyClass) {
        PyFunction init = ((PyClass)docStringOwner).findInitOrNew(false);
        if (init == null) return;
        PythonDocumentationProvider.inserDocStub(init, ((PyClass)docStringOwner).getStatementList(),
                                                 project, getEditor(project, docStringOwner.getContainingFile()));
      }
      return;
    }
    if (docStringExpression == null) return;
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(project);
    if (documentationSettings.isEpydocFormat(docStringExpression.getContainingFile())) {
      myPrefix = "@";
    }
    else {
      myPrefix = ":";
    }

    String replacement = docStringExpression.getText();
    if (myMissing != null) {
      replacement = createMissingReplacement(docStringOwner);
    }
    if (myUnexpected != null) {
      replacement = createUnexpectedReplacement(replacement);
    }
    if (!replacement.equals(docStringExpression.getText())) {
      PyExpression str = elementGenerator.createDocstring(replacement).getExpression();
      docStringExpression.replace(str);
    }
  }

  private String createUnexpectedReplacement(String text) {
    StringBuilder newText = new StringBuilder();
    String[] lines = LineTokenizer.tokenize(text, true);
    boolean skipNext = false;
    for (String line : lines) {
      if (line.contains(myPrefix)) {
        String[] subLines = line.split(" ");
        boolean lookNext = false;
        boolean add = true;
        for (String s : subLines) {
          if (s.trim().equals(myPrefix + "param")) {
            lookNext = true;
          }
          if (lookNext && s.trim().endsWith(":")) {
            String tmp = s.trim().substring(0, s.trim().length() - 1);
            if (myUnexpected.equals(tmp)) {
              lookNext = false;
              skipNext = true;
              add = false;
            }
          }
        }
        if (add) {
          newText.append(line);
          skipNext = false;
        }
      }
      else if (!skipNext || line.contains("\"\"\"") || line.contains("'''")) {
        newText.append(line);
      }
    }
    return newText.toString();
  }

  private String createMissingReplacement(PyDocStringOwner docstring) {
    return PythonDocumentationProvider.addParamToDocstring(docstring, "param", myMissingText, myPrefix).getFirst();
  }
}
