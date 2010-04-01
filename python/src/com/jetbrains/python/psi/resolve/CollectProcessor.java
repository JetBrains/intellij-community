package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CollectProcessor implements PyClassScopeProcessor {
  private final Condition<PsiElement> myCondition;
  private final List<PsiElement> myResult;

  public CollectProcessor(Condition<PsiElement> condition) {
    myCondition = condition;
    myResult = new ArrayList<PsiElement>();
  }

  public boolean execute(final PsiElement element, final ResolveState state) {
    if (myCondition.value(element)) {
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
  public Condition<PsiElement> getTargetCondition() {
    return myCondition;
  }
}
