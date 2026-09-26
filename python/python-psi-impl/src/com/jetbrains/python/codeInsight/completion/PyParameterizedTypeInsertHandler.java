package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * Inserts square brackets after names of parameterized types inside type hints.
 * It includes special constructs from typings, such as {@code typing.Optional} or {@code typing.Union},
 * and regular generic classes, such as {@code list} or {@code ContextManager}.
 */
public class PyParameterizedTypeInsertHandler extends ParenthesesInsertHandler<LookupElement> {
  public static boolean isCompletingParameterizedType(@NotNull PsiElement definition,
                                                      @NotNull PsiElement completionPosition,
                                                      @NotNull TypeEvalContext context) {
    return PyTypingTypeProvider.isInsideTypeHint(completionPosition, context) && isParameterizableType(definition, context);
  }

  public static final PyParameterizedTypeInsertHandler INSTANCE = new PyParameterizedTypeInsertHandler();

  private PyParameterizedTypeInsertHandler() {
    super(false, false, true, false, '[', ']');
  }

  @Override
  protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
    return true;
  }

  private static boolean isParameterizableType(@NotNull PsiElement element, @NotNull TypeEvalContext typeEvalContext) {
    return element instanceof PyQualifiedNameOwner qnOwner &&
           PyTypingTypeProvider.GENERIC_CLASSES.contains(qnOwner.getQualifiedName()) ||
           element instanceof PyClass pyClass && new PyTypingTypeProvider().getGenericType(pyClass, typeEvalContext) != null;
  }
}
