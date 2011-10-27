package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.PyTypedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class TypeEvalContext {
  private final boolean myAllowDataFlow;
  private final boolean myAllowStubToAST;
  private List<String> myTrace;
  private String myTraceIndent = "";
  private final PsiFile myOrigin;

  private final Map<PyTypedElement, PyType> myEvaluated = new HashMap<PyTypedElement, PyType>();

  private TypeEvalContext(boolean allowDataFlow, boolean allowStubToAST, PsiFile origin) {
    myAllowDataFlow = allowDataFlow;
    myAllowStubToAST = allowStubToAST;
    myOrigin = origin;
  }

  public boolean allowDataFlow(PsiElement element) {
    return myAllowDataFlow || element.getContainingFile() == myOrigin;
  }

  public boolean allowReturnTypes(PsiElement element) {
    return myAllowDataFlow || element.getContainingFile() == myOrigin;
  }

  public boolean allowStubToAST() {
    return myAllowStubToAST;
  }

  public static TypeEvalContext slow() {
    return new TypeEvalContext(true, true, null);
  }

  public static TypeEvalContext fast() {
    return new TypeEvalContext(false, true, null);
  }
  
  /**
   * Creates a TypeEvalContext for performing analysis operations on the specified file which is currently open in the editor.
   * For such a file, additional slow operations are allowed.
   * 
   * @param origin the file open in the editor
   * @return the type eval context for the file.
   */
  public static TypeEvalContext fast(@Nullable PsiFile origin) {
    return new TypeEvalContext(false, true, origin);
  }

  public static TypeEvalContext fastStubOnly() {
    return new TypeEvalContext(false, false, null);
  }

  /**
   * Creates a TypeEvalContext for performing analysis operations on the specified file which is currently open in the editor,
   * without accessing stubs. For such a file, additional slow operations are allowed.
   *
   * @param origin the file open in the editor
   * @return the type eval context for the file.
   */
  public static TypeEvalContext fastStubOnly(@Nullable PsiFile origin) {
    return new TypeEvalContext(false, false, origin);
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
        return myEvaluated.get(element);
      }
    }
    PyType result = element.getType(this);
    synchronized (myEvaluated) {
      myEvaluated.put(element, result);
    }
    return result;
  }

  public boolean maySwitchToAST(StubBasedPsiElement element) {
    return myAllowStubToAST || element.getStub() == null;
  }
}
