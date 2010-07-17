package com.jetbrains.python.console;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.console.pydev.PyCodeCompletionImages;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
    List<LookupElement> variants = new ArrayList<LookupElement>();
    try {
      final List<PydevCompletionVariant> completions = myCommunication.getCompletions(myPrefix);
      for (PydevCompletionVariant completion : completions) {
        final PsiManager manager = myElement.getManager();
        LookupElementBuilder builder = LookupElementBuilder
          .create(new PydevConsoleElement(manager, completion.getName(), completion.getDescription()))
          .setIcon(PyCodeCompletionImages.getImageForType(completion.getType()));
        if (!StringUtil.isEmptyOrSpaces(completion.getArgs())) {
          builder = builder.setTailText(completion.getArgs());
        }
        variants.add(builder);
      }

    }
    catch (Exception e) {
      //LOG.error(e);
    }
    return variants.toArray();
  }
}
