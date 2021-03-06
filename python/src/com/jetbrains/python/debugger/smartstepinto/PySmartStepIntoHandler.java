// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.smartstepinto;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PySmartStepIntoHandler extends XSmartStepIntoHandler<PySmartStepIntoVariant> {
  @NotNull private final XDebugSession mySession;
  @NotNull private final PyDebugProcess myProcess;

  public PySmartStepIntoHandler(@NotNull final PyDebugProcess process) {
    mySession = process.getSession();
    myProcess = process;
  }

  @Override
  public void startStepInto(@NotNull PySmartStepIntoVariant smartStepIntoVariant) {
    myProcess.startSmartStepInto(smartStepIntoVariant);
  }

  @Override
  public String getPopupTitle(@NotNull XSourcePosition position) {
    return PyBundle.message("debug.popup.title.step.into.function");
  }

  @Override
  @NotNull
  public List<PySmartStepIntoVariant> computeSmartStepVariants(@NotNull XSourcePosition position) {
    PyStackFrame currentFrame = (PyStackFrame)mySession.getCurrentStackFrame();
    if (currentFrame == null || currentFrame.isComprehension()) return Collections.emptyList();

    PySmartStepIntoContext context = createSmartStepIntoContext(currentFrame);
    if (context == null) return Collections.emptyList();

    final Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
    if (document == null) return Collections.emptyList();

    Future<List<Pair<String, Boolean>>> future = ApplicationManager.getApplication().executeOnPooledThread(
      () -> myProcess.getSmartStepIntoVariants(context.getStartLine(), context.getEndLine()));

    List<Pair<String, Boolean>> variantsFromPython;
    try {
      variantsFromPython = future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      return Collections.emptyList();
    }

    if (variantsFromPython.size() == 0) return Collections.emptyList();

    return removePossiblyUnreachableVariants(document, position.getLine(), variantsFromPython, context);
  }

  private @NotNull List<PySmartStepIntoVariant> removePossiblyUnreachableVariants(@NotNull Document document, int line,
                                                                                  @NotNull List<Pair<String, Boolean>> variantsFromPython,
                                                                                  @NotNull PySmartStepIntoContext context) {
    PsiElement expression = findSmartStepIntoBaseExpression(document, line);
    if (expression == null) return Collections.emptyList();

    List<PySmartStepIntoVariant> result = new ArrayList<>();

    // We are going to filter the variants that PyCharm cannot resolve to be sure we don't suggest stepping into a native function.
    expression.accept(new PySmartStepIntoVariantVisitor(result, variantsFromPython, context));

    return result;
  }

  /**
   * Find an expression within which we are going to search for smart step into variants.
   *
   * Note, that expressions can span multiple lines, e.g. for parenthesized expressions or argument lists.
   */
  @Nullable
  private PsiElement findSmartStepIntoBaseExpression(@NotNull Document document, int line) {
    PsiFile file = PsiDocumentManager.getInstance(mySession.getProject()).getPsiFile(document);
    if (file == null) return null;

    PsiElement element = file.findElementAt(DocumentUtil.getFirstNonSpaceCharOffset(document, line));
    if (element == null) return null;

    // Allow multiline smart step into for argument list case.
    PsiElement argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
    if (argumentList != null) return argumentList.getParent();

    // Allow multiline smart step into for parenthesized multiline expressions.
    PsiElement parenthesizedExpression = PsiTreeUtil.getParentOfType(element, PyParenthesizedExpression.class);
    if (parenthesizedExpression != null) return parenthesizedExpression;

    // If it's not an argument list or parenthesized expression, simply find and return top expression at the line.
    PsiElement parent = element.getParent();
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line);
    while (parent != null &&  lineStartOffset <= parent.getTextOffset() && parent.getTextOffset() < lineEndOffset) {
      element = parent;
      parent = element.getParent();
    }

    if (element instanceof PyExpression) return element;

    Ref<PsiElement> psiElementRef = new Ref<>();

    element.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
        storeElement(node);
      }

      @Override
      public void visitPyCallExpression(@NotNull PyCallExpression node) {
        storeElement(node);
      }

      @Override
      public void visitPyComprehensionElement(@NotNull PyComprehensionElement node) {
        storeElement(node);
      }

      @Override
      public void visitPyElement(@NotNull PyElement node) {
        if (node instanceof PyDecorator) {
          storeElement(node);
        }
        super.visitPyElement(node);
      }

      @Override
      public void visitPyDecoratorList(@NotNull PyDecoratorList node) {
        storeElement(node);
        super.visitPyDecoratorList(node);
      }

      private void storeElement(PsiElement node) {
        if (psiElementRef.isNull()) {
          psiElementRef.set(node);
        }
      }
    });

    return psiElementRef.get();
  }

  @NotNull
  @Override
  public Promise<List<PySmartStepIntoVariant>> computeStepIntoVariants(@NotNull XSourcePosition position) {
    if (PyDebuggerSettings.getInstance().isAlwaysDoSmartStepInto()) {
      return computeSmartStepVariantsAsync(position);
    }
    return Promises.rejectedPromise();
  }

  /**
   * Creates and stores a smart step into context for the given frame.
   */
  @Nullable
  public PySmartStepIntoContext createSmartStepIntoContext(@NotNull PyStackFrame frame) {
    XSourcePosition position = frame.getSourcePosition();
    if (position == null) return null;

    Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
    if (document == null) return null;

    PySmartStepIntoHandler handler = (PySmartStepIntoHandler)myProcess.getSmartStepIntoHandler();
    PsiElement expression = handler.findSmartStepIntoBaseExpression(document, position.getLine());

    if (expression != null) {
      TextRange range = expression.getTextRange();
      int startLine = document.getLineNumber(range.getStartOffset());
      int endLine = document.getLineNumber(range.getEndOffset());
      return new PySmartStepIntoContext(startLine + 1, endLine + 1, frame);
    }

    return null;
  }
}
