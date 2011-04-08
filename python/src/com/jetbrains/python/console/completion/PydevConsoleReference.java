package com.jetbrains.python.console.completion;

import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.IToken;
import com.jetbrains.python.console.pydev.PyCodeCompletionImages;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class PydevConsoleReference extends PsiPolyVariantReferenceBase<PyReferenceExpression> {
  private final ConsoleCommunication myCommunication;
  private final String myPrefix;

  public PydevConsoleReference(final PyReferenceExpression expression,
                               final ConsoleCommunication communication,
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
        final String name = completion.getName();
        final int type = completion.getType();
        LookupElementBuilder builder = LookupElementBuilder
          .create(new PydevConsoleElement(manager, name, completion.getDescription()))
          .setIcon(PyCodeCompletionImages.getImageForType(type));
        final String args = completion.getArgs();
        if (!StringUtil.isEmptyOrSpaces(args)) {
          builder = builder.setTailText(args);
        }
        // Set function insert handler
        if (type == IToken.TYPE_FUNCTION || args.endsWith(")")){
          builder = builder.setInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS);
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
