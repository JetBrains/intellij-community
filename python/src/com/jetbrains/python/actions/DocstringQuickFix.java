package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User : catherine
 */
public class DocstringQuickFix implements LocalQuickFix {

  List<String> myMissing;
  List<String> myUnexpected;
  String myPrefix;

  public DocstringQuickFix(List<String> missing, List<String> unexpected) {
    myMissing = missing;
    myUnexpected = unexpected;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.docstring");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(element.getProject());
    if (documentationSettings.isEpydocFormat(element.getContainingFile()))
      myPrefix = "@";
    else
      myPrefix = ":";

    String replacement = element.getText();
    if (!myMissing.isEmpty()) {
      replacement = createMissingReplacement(element);
    }
    if (!myUnexpected.isEmpty()) {
      replacement = createUnexpectedReplacement(replacement);
    }
    if (!replacement.equals(element.getText())) {
      PyStringLiteralExpression str = (PyStringLiteralExpression)elementGenerator.createFromText(LanguageLevel.forElement(element),
                                                                        PyExpressionStatement.class, replacement.toString()).getExpression();
      element.replace(str);
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
          if (s.trim().equals(myPrefix+"param")) {
            lookNext = true;
          }
          if (lookNext && s.trim().endsWith(":")) {
            String tmp = s.trim().substring(0, s.trim().length()-1);
            if (myUnexpected.contains(tmp)) {
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
      else if (!skipNext || line.contains("\"\"\"") || line.contains("'''"))
        newText.append(line);
    }
    return newText.toString();
  }

  private String createMissingReplacement(PsiElement element) {
    String text = element.getText();
    String[] lines = LineTokenizer.tokenize(text, true);
    StringBuilder newText = new StringBuilder();
    int ind = lines.length - 1;
    for (int i = 0; i != lines.length-1; ++i) {
      String line = lines[i];
      if (line.contains(myPrefix)) {
        ind = i;
        break;
      }
      newText.append(line);
    }
    PyFunction fun = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    PsiWhiteSpace whitespace = PsiTreeUtil.getPrevSiblingOfType(fun.getStatementList(), PsiWhiteSpace.class);
    String ws = "\n";
    if (whitespace != null) {
      String[] spaces = whitespace.getText().split("\n");
      if (spaces.length > 1)
        ws = ws + whitespace.getText().split("\n")[1];
    }
    newText.deleteCharAt(newText.length()-1);
    newText.append(ws);

    for (int i = 0; i != myMissing.size(); ++i) {
      String s = myMissing.get(i);
      newText.append(myPrefix).append("param ").append(s).append(": ");
      if (i != myMissing.size()-1)
        newText.append(ws);
    }
    newText.append("\n");
    for (int i = ind; i != lines.length; ++i) {
      String line = lines[i];
      newText.append(line);
    }
    return newText.toString();
  }
}
