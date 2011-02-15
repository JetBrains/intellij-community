package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CollectProcessor implements PyClassScopeProcessor {
  private final List<PsiElement> myResult;
  private final TokenSet myTargetTokenSet;

  public CollectProcessor(TokenSet targetTokenSet) {
    myTargetTokenSet = targetTokenSet;
    myResult = new ArrayList<PsiElement>();
  }

  public boolean execute(final PsiElement element, final ResolveState state) {
    if (myTargetTokenSet.contains(element.getNode().getElementType())) {
      myResult.add(element);
    }
    return true; // collect till we drop
  }

  public <T> T getHint(final Key<T> hintKey) {
    return null;
  }

  public void handleEvent(final Event event, final Object associated) {
  }

  public List<PsiElement> getResult() {
    return myResult;
  }

  @NotNull
  @Override
  public TokenSet getTargetTokenSet() {
    return myTargetTokenSet;
  }
}
