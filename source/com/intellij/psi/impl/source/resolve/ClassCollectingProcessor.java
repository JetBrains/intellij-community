/*
 * @author max
 */
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.util.containers.MostlySingularMultiMap;

public class ClassCollectingProcessor extends BaseScopeProcessor implements ElementClassHint {
  private final MostlySingularMultiMap<String, ResultWithContext> myResult = new MostlySingularMultiMap<String, ResultWithContext>();
  private PsiElement myCurrentFileContext = null;

  @Override
  public <T> T getHint(Key<T> hintKey) {
    if (hintKey == KEY) return (T)this;
    return null;
  }


  @Override
  public void handleEvent(Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (element instanceof PsiNamedElement) {
      PsiNamedElement named = ((PsiNamedElement)element);
      String name = named.getName();
      if (name != null) {
        myResult.add(name, new ResultWithContext(named, myCurrentFileContext));
      }
    }
    return true;
  }

  public boolean shouldProcess(DeclaractionKind kind) {
    return kind == DeclaractionKind.CLASS || kind == DeclaractionKind.PACKAGE || kind == DeclaractionKind.METHOD || kind == DeclaractionKind.FIELD;
  }

  public MostlySingularMultiMap<String, ResultWithContext> getResults() {
    return myResult;
  }

  public static class ResultWithContext {
    private final PsiNamedElement myElement;
    private final PsiElement myFileContext;

    public ResultWithContext(PsiNamedElement element, PsiElement fileContext) {
      myElement = element;
      myFileContext = fileContext;
    }

    public PsiNamedElement getElement() {
      return myElement;
    }

    public PsiElement getFileContext() {
      return myFileContext;
    }
  }
}
