package org.jetbrains.postfixCompletion.lookupItems;

import com.intellij.codeInsight.completion.JavaChainLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class PostfixChainLookupElement extends JavaChainLookupElement {
  public PostfixChainLookupElement(@NotNull LookupElement qualifier, @NotNull LookupElement element) {
    super(qualifier, element);
  }

  @Override
  public PsiType getType() {
    return null;
  }

  @Override
  public Set<String> getAllLookupStrings() {
    String qualifierString = getQualifier().getLookupString() + ".";
    Set<String> prefixedStrings = new LinkedHashSet<String>();

    for (String s : getDelegate().getAllLookupStrings()) {
      prefixedStrings.add(qualifierString + s);
    }

    return prefixedStrings;
  }
}
