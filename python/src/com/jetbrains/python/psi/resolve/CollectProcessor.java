package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CollectProcessor implements PyClassScopeProcessor {

  Class<? extends PsiElement>[] my_collectables;
  List<PsiElement> my_result;

  public CollectProcessor(Class<? extends PsiElement>... collectables) {
    my_collectables = collectables;
    my_result = new ArrayList<PsiElement>();
  }

  public boolean execute(final PsiElement element, final ResolveState state) {
    for (Class cls : my_collectables) {
      if (cls.isInstance(element)) {
        my_result.add(element);
      }
    }
    return true; // collect till we drop
  }

  public <T> T getHint(final Key<T> hintKey) {
    return null;
  }

  public void handleEvent(final Event event, final Object associated) {
  }

  public List<PsiElement> getResult() {
    return my_result;
  }

  @NotNull
  public Class[] getPossibleTargets() {
    return my_collectables;
  }
}
