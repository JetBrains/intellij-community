// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.smartstepinto;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PySmartStepIntoHandler extends XSmartStepIntoHandler<PySmartStepIntoVariant> {
  private final @NotNull XDebugSession mySession;
  private final @NotNull PyDebugProcess myProcess;

  public PySmartStepIntoHandler(final @NotNull PyDebugProcess process) {
    mySession = process.getSession();
    myProcess = process;
  }

  @Override
  public void startStepInto(@NotNull PySmartStepIntoVariant smartStepIntoVariant) {
    myProcess.startSmartStepInto(smartStepIntoVariant);
  }

  @Override
  public String getPopupTitle() {
    return PyBundle.message("debug.popup.title.step.into.function");
  }

  @Override
  public @NotNull Promise<List<PySmartStepIntoVariant>> computeSmartStepVariantsAsync(@NotNull XSourcePosition position) {
    var promise = new AsyncPromise<List<PySmartStepIntoVariant>>();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      var computedVariants = findVariants(position);
      promise.setResult(computedVariants);
    });
    return promise;
  }

  private @NotNull List<PySmartStepIntoVariant> findVariants(@NotNull XSourcePosition position) {
    PyStackFrame currentFrame = (PyStackFrame)mySession.getCurrentStackFrame();
    if (currentFrame == null || currentFrame.isComprehension()) return Collections.emptyList();

    PySmartStepIntoContext context = ReadAction.compute(() -> createSmartStepIntoContext(currentFrame));
    if (context == null) return Collections.emptyList();

    var document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(position.getFile()));
    if (document == null) return Collections.emptyList();

    var variantsFromPython = myProcess.getSmartStepIntoVariants(context.getStartLine(), context.getEndLine());

    if (variantsFromPython.isEmpty()) return Collections.emptyList();

    return ReadAction.compute(() -> removePossiblyUnreachableVariants(document, position.getLine(), variantsFromPython, context));
  }

  @Override
  public @NotNull List<PySmartStepIntoVariant> computeSmartStepVariants(@NotNull XSourcePosition position) {
    return findVariants(position);
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
   * <p>
   * Note, that expressions can span multiple lines, e.g. for parenthesized expressions or argument lists.
   */
  private @Nullable PsiElement findSmartStepIntoBaseExpression(@NotNull Document document, int line) {
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

  @Override
  public @NotNull Promise<List<PySmartStepIntoVariant>> computeStepIntoVariants(@NotNull XSourcePosition position) {
    if (PyDebuggerSettings.getInstance().isAlwaysDoSmartStepInto()) {
      return computeSmartStepVariantsAsync(position);
    }
    return Promises.rejectedPromise();
  }

  /**
   * Creates and stores a smart step into context for the given frame.
   */
  public @Nullable PySmartStepIntoContext createSmartStepIntoContext(@NotNull PyStackFrame frame) {
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
