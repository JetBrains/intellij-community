package com.jetbrains.python.console.completion;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 * Light element in completion to provide Quick documentation functionality
 */
public class PydevConsoleElement extends LightElement implements PsiNamedElement {
  private final String myName;
  private final String myDescription;

  public PydevConsoleElement(final PsiManager manager, final String name, final String description) {
    super(manager, PythonLanguage.getInstance());
    myName = name;
    myDescription = description;
  }

  public String getText() {
    return myName;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public PsiElement copy() {
    return null;
  }

  @Override
  public String toString() {
    return "PydevConsoleElement " + myDescription;
  }

  public String getName() {
    return myName;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("PydevConsoleElement#setName() is not supported");
  }

  public static String generateDoc(final PydevConsoleElement pydevConsoleElement) {
    final String description = pydevConsoleElement.myDescription;
    // Description contract:
    // (Signatures\n\n) ? Description
    final int index = description.indexOf("\n\n");
    if (index != -1){
      final StringBuilder builder = new StringBuilder();
      builder.append("<b>").append(description.subSequence(0, index)).append("</b>").append("<hr/>").append(description.substring(index+2));
      return StringUtil.replace(builder.toString(), "\n", "<br/>");
    }
    return StringUtil.replace(description, "\n", "<br/>");
  }
}
