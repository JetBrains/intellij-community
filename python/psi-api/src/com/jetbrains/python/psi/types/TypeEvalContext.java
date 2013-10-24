/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyTypedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class TypeEvalContext {
  public static class Key {
    private static final Key INSTANCE = new Key();

    private Key() {}
  }

  private final boolean myAllowDataFlow;
  private final boolean myAllowStubToAST;
  private List<String> myTrace;
  private String myTraceIndent = "";
  @Nullable private final PsiFile myOrigin;

  private final Map<PyTypedElement, PyType> myEvaluated = new HashMap<PyTypedElement, PyType>();
  private final ThreadLocal<Set<PyTypedElement>> myEvaluating = new ThreadLocal<Set<PyTypedElement>>() {
    @Override
    protected Set<PyTypedElement> initialValue() {
      return new HashSet<PyTypedElement>();
    }
  };

  private TypeEvalContext(boolean allowDataFlow, boolean allowStubToAST, @Nullable PsiFile origin) {
    myAllowDataFlow = allowDataFlow;
    myAllowStubToAST = allowStubToAST;
    myOrigin = origin;
  }

  @Override
  public String toString() {
    return String.format("TypeEvalContext(%b, %b, %s)", myAllowDataFlow, myAllowStubToAST, myOrigin);
  }

  public boolean allowDataFlow(PsiElement element) {
    return myAllowDataFlow || element.getContainingFile() == myOrigin;
  }

  public boolean allowReturnTypes(PsiElement element) {
    return myAllowDataFlow || element.getContainingFile() == myOrigin;
  }

  public boolean allowLocalUsages(@NotNull PsiElement element) {
    return myAllowStubToAST && myAllowDataFlow && element.getContainingFile() == myOrigin;
  }

  /**
   * Create the most detailed type evaluation context for user-initiated actions.
   *
   * Should be used for code completion, go to definition, find usages, refactorings, documentation.
   */
  public static TypeEvalContext userInitiated(@Nullable PsiFile origin) {
    return new TypeEvalContext(true, true, origin);
  }

  /**
   * Create a type evaluation context for performing analysis operations on the specified file which is currently open in the editor,
   * without accessing stubs. For such a file, additional slow operations are allowed.
   *
   * Inspections should not create a new type evaluation context. They should re-use the context of the inspection session.
   */
  public static TypeEvalContext codeAnalysis(@Nullable PsiFile origin) {
    return new TypeEvalContext(false, false, origin);
  }

  /**
   * Create the most shallow type evaluation context for code insight purposes when other more detailed contexts are not available.
   *
   * It's use should be minimized.
   */
  public static TypeEvalContext codeInsightFallback() {
    return new TypeEvalContext(false, false, null);
  }

  /**
   * Create a type evaluation context for deeper and slower code insight.
   *
   * Should be used only when normal code insight context is not enough for getting good results.
   */
  public static TypeEvalContext deepCodeInsight() {
    return new TypeEvalContext(false, true, null);
  }

  public TypeEvalContext withTracing() {
    if (myTrace == null) {
      myTrace = new ArrayList<String>();
    }
    return this;
  }
  
  public void trace(String message, Object... args) {
    if (myTrace != null) {
      myTrace.add(myTraceIndent + String.format(message, args));
    }
  }
  
  public void traceIndent() {
    if (myTrace != null) {
      myTraceIndent += "  ";
    }
  }
  
  public void traceUnindent() {
    if (myTrace != null && myTraceIndent.length() >= 2) {
      myTraceIndent = myTraceIndent.substring(0, myTraceIndent.length()-2);
    }
  }
  
  public String printTrace() {
    return StringUtil.join(myTrace, "\n");
  }
  
  public boolean tracing() {
    return myTrace != null;
  }

  @Nullable
  public PyType getType(@NotNull PyTypedElement element) {
    synchronized (myEvaluated) {
      if (myEvaluated.containsKey(element)) {
        final PyType pyType = myEvaluated.get(element);
        if (pyType != null) {
          pyType.assertValid(element.toString());
        }
        return pyType;
      }
    }
    final Set<PyTypedElement> evaluating = myEvaluating.get();
    if (evaluating.contains(element)) {
      return null;
    }
    evaluating.add(element);
    try {
      PyType result = element.getType(this, Key.INSTANCE);
      if (result != null) {
        result.assertValid(element.toString());
      }
      synchronized (myEvaluated) {
        myEvaluated.put(element, result);
      }
      return result;
    }
    finally {
      evaluating.remove(element);
    }
  }

  public boolean maySwitchToAST(@NotNull PsiElement element) {
    return myAllowStubToAST || myOrigin == element.getContainingFile();
  }

  @Nullable
  public PsiFile getOrigin() {
    return myOrigin;
  }
}
