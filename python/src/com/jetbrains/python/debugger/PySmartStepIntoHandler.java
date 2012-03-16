package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PySmartStepIntoHandler extends XSmartStepIntoHandler<PySmartStepIntoHandler.PySmartStepIntoVariant> {
  private final XDebugSession mySession;
  private PyDebugProcess myProcess;

  public PySmartStepIntoHandler(final PyDebugProcess process) {
    mySession = process.getSession();
    myProcess = process;
  }

  @NotNull
  public List<PySmartStepIntoVariant> computeSmartStepVariants(@NotNull XSourcePosition position) {
    final Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
    final List<PySmartStepIntoVariant> variants = Lists.newArrayList();
    final Set<PyCallExpression> visitedCalls = Sets.newHashSet();
    final Set<PyFunction> addedFunctions = Sets.newHashSet();

    XDebuggerUtil.getInstance().iterateLine(mySession.getProject(), document, position.getLine(), new Processor<PsiElement>() {
      public boolean process(PsiElement psiElement) {
        addVariants(psiElement, variants, visitedCalls, addedFunctions);
        return true;
      }
    });
    return variants;
  }

  @Override
  public void startStepInto(PySmartStepIntoVariant smartStepIntoVariant) {
    myProcess.startSmartStepInto(smartStepIntoVariant.getFunctionName());
  }

  public String getPopupTitle(@NotNull XSourcePosition position) {
    return PyBundle.message("debug.popup.title.step.into.function");
  }

  private static void addVariants(@Nullable PsiElement element,
                                  List<PySmartStepIntoVariant> variants,
                                  Set<PyCallExpression> visited,
                                  Set<PyFunction> addedFunctions) {
    if (element == null) return;

    final PyCallExpression expression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (expression != null && visited.add(expression)) {
      addVariants(expression.getParent(), variants, visited, addedFunctions);
      PyExpression ref = expression.getCallee();

      variants.add(new PySmartStepIntoVariant(ref));
    }
  }

  public static class PySmartStepIntoVariant extends XSmartStepIntoVariant {
    //private final String myFunctionName;

    private final PyElement myElement;

    public PySmartStepIntoVariant(PyElement element) {
      myElement = element;
    }

    @Override
    public String getText() {
      return myElement.getText() + "()";
    }

    public String getFunctionName() {
      String name = myElement.getName();
      return name != null ? name : getText();
    }
  }
}
