package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Weighs down items starting with two underscores.
 * <br/>
 * User: dcheryasov
 * Date: 11/11/10 4:24 PM
 */
public class PythonCompletionWeigher extends CompletionWeigher {
  @NonNls private static final String DOUBLE_UNDER = "__";

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    final String name = element.getLookupString();
    if (name.startsWith(DOUBLE_UNDER)) {
      if (name.endsWith(DOUBLE_UNDER)) return -10; // __foo__ is lowest
      else return -5; // __foo is lower than normal
    }
    return 0; // default
  }
}
