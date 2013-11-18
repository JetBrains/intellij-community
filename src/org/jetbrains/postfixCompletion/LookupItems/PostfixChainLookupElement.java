package org.jetbrains.postfixCompletion.LookupItems;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.util.*;

public final class PostfixChainLookupElement extends JavaChainLookupElement {
  public PostfixChainLookupElement(
      @NotNull LookupElement qualifier, @NotNull LookupElement element) {
    super(qualifier, element);
  }

  @Override public PsiType getType() {
    return null;
  }

  @Override public Set<String> getAllLookupStrings() {
    String qualifierString = getQualifier().getLookupString() + ".";
    Set<String> prefixedStrings = new LinkedHashSet<String>();

    for (String s : getDelegate().getAllLookupStrings()) {
      prefixedStrings.add(qualifierString + s);
    }

    return prefixedStrings;
  }
}
