package com.jetbrains.python.console.completion;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.impl.LightNamedElement;

/**
 * @author oleg
 * Light element in completion to provide Quick documentation functionality
 */
public class PydevConsoleElement extends LightNamedElement {
  private final String myDescription;

  public PydevConsoleElement(final PsiManager manager, final String name, final String description) {
    super(manager, PythonLanguage.getInstance(), name);
    myDescription = description;
  }

  @Override
  public String toString() {
    return "PydevConsoleElement " + myDescription;
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
