package com.jetbrains.python.console;

import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PydevConsoleReference extends PsiPolyVariantReferenceBase<PyReferenceExpression> {
  private final PydevConsoleCommunication myCommunication;
  private final String myPrefix;

  public PydevConsoleReference(final PyReferenceExpression expression,
                               final PydevConsoleCommunication communication,
                               final String prefix) {
    super(expression, true);
    myCommunication = communication;
    myPrefix = prefix;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return new ResolveResult[0];
  }

  @NotNull
  public Object[] getVariants() {
    Object[] completions = new Object[]{};
    try {
      completions = myCommunication.getCompletions(myPrefix);
    }
    catch (Exception e) {
      //LOG.error(e);
    }
    return completions;
  }
}
