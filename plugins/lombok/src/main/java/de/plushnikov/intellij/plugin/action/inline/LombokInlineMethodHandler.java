/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package de.plushnikov.intellij.plugin.action.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inline.InlineMethodDialog;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.inline.JavaInlineActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;

/**
 * Customized copy of com.intellij.refactoring.inline.InlineMethodHandler class to support lombok generated methods
 */
public class LombokInlineMethodHandler extends JavaInlineActionHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");

  private LombokInlineMethodHandler() {
  }

  public boolean canInlineElement(PsiElement element) {
    return element instanceof LombokLightMethodBuilder && element.getLanguage() == JavaLanguage.INSTANCE;
  }

  public void inlineElement(final Project project, Editor editor, PsiElement element) {
    final PsiMethod method = (PsiMethod) element;

    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) {
      String message;
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.to.abstract.methods", REFACTORING_NAME);
      } else {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.no.sources.attached", REFACTORING_NAME);
      }
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null) {
      final PsiElement refElement = reference.getElement();
      if (refElement != null && !isEnabledForLanguage(refElement.getLanguage())) {
        String message = RefactoringBundle
          .message("refactoring.is.not.supported.for.language", "Inline of Java method", refElement.getLanguage().getDisplayName());
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method)) {
      if (reference == null || InlineUtil.getTailCallType(reference) == InlineUtil.TailCallType.None) {
        String message = RefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (reference == null && checkRecursive(method)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.for.recursive.methods", REFACTORING_NAME);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    if (reference != null) {
      final String errorMessage = InlineMethodProcessor.checkCalledInSuperOrThisExpr(methodBody, reference.getElement());
      if (errorMessage != null) {
        CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (method.isConstructor()) {
      if (method.isVarArgs()) {
        String message = RefactoringBundle.message("refactoring.cannot.be.applied.to.vararg.constructors", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_CONSTRUCTOR);
        return;
      }
      final boolean chainingConstructor = isChainingConstructor(method);
      if (!chainingConstructor) {
        if (!isThisReference(reference)) {
          String message = RefactoringBundle.message("refactoring.cannot.be.applied.to.inline.non.chaining.constructors", REFACTORING_NAME);
          CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_CONSTRUCTOR);
          return;
        }
      }
      if (reference != null) {
        final PsiElement refElement = reference.getElement();
        PsiCall constructorCall = refElement instanceof PsiJavaCodeReferenceElement ? RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement) refElement) : null;
        if (constructorCall == null || !method.equals(constructorCall.resolveMethod())) {
          reference = null;
        }
      }
    } else {
      if (reference != null && !method.getManager().areElementsEquivalent(method, reference.resolve())) {
        reference = null;
      }
    }

    if (reference != null && PsiTreeUtil.getParentOfType(reference.getElement(), PsiImportStaticStatement.class) != null) {
      reference = null;
    }

    final boolean invokedOnReference = reference != null;
    if (!invokedOnReference) {
      final VirtualFile vFile = method.getContainingFile().getVirtualFile();
      ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(vFile);
    }

    PsiJavaCodeReferenceElement refElement = null;
    if (reference != null) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiJavaCodeReferenceElement) {
        refElement = (PsiJavaCodeReferenceElement) referenceElement;
      }
    }

    //for lombok methods we are allowing only 'this inlines'
    InlineMethodDialog dialog = new InlineMethodDialog(project, method, refElement, editor, true);
    dialog.show();
  }

  private boolean isChainingConstructor(PsiMethod constructor) {
    PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement) statements[0]).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpr = ((PsiMethodCallExpression) expression).getMethodExpression();
          if ("this".equals(methodExpr.getReferenceName())) {
            PsiElement resolved = methodExpr.resolve();
            return resolved instanceof PsiMethod && ((PsiMethod) resolved).isConstructor(); //delegated via "this" call
          }
        }
      }
    }
    return false;
  }

  private boolean checkRecursive(PsiMethod method) {
    return checkCalls(method.getBody(), method);
  }

  private static boolean checkCalls(PsiElement scope, PsiMethod method) {
    if (scope instanceof PsiMethodCallExpression) {
      PsiMethod refMethod = (PsiMethod) ((PsiMethodCallExpression) scope).getMethodExpression().resolve();
      if (method.equals(refMethod)) {
        return true;
      }
    }

    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (checkCalls(child, method)) {
        return true;
      }
    }

    return false;
  }

  private boolean isThisReference(PsiReference reference) {
    if (reference != null) {
      final PsiElement referenceElement = reference.getElement();
      return referenceElement instanceof PsiJavaCodeReferenceElement &&
        referenceElement.getParent() instanceof PsiMethodCallExpression &&
        "this".equals(((PsiJavaCodeReferenceElement) referenceElement).getReferenceName());
    }
    return false;
  }
}
