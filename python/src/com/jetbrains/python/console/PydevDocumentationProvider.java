package com.jetbrains.python.console;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PydevDocumentationProvider extends QuickDocumentationProvider {
  public String getQuickNavigateInfo(final PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    if (object instanceof PydevConsoleElement){
      return (PydevConsoleElement) object;
    }
    return super.getDocumentationElementForLookupItem(psiManager, object, element);
  }

  @Override
  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    // Process PydevConsoleElement case
    if (element instanceof PydevConsoleElement){
      return PydevConsoleElement.generateDoc((PydevConsoleElement)element);
    }
    return null;
  }

  @Nullable
  public static String createDoc(final PsiElement element, final PsiElement originalElement) {
    final PyExpression expression = PsiTreeUtil.getParentOfType(originalElement, PyExpression.class);
    // Indicates that we are inside console, not a lookup element!
    if (expression == null){
      return null;
    }
    final ConsoleCommunication communication = PydevConsoleRunner.getConsoleCommunication(originalElement);
    if (communication == null){
      return null;
    }
    try {
      final String description = communication.getDescription(expression.getText());
      return StringUtil.isEmptyOrSpaces(description) ? null : description;
    }
    catch (Exception e) {
      return null;
    }
  }
}
