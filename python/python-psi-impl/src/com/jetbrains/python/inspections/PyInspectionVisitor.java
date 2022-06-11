/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dcheryasov
 */
public abstract class PyInspectionVisitor extends PyElementVisitor {
  @Nullable private final ProblemsHolder myHolder;
  protected final TypeEvalContext myTypeEvalContext;

  public static final Key<TypeEvalContext> INSPECTION_TYPE_EVAL_CONTEXT = Key.create("PyInspectionTypeEvalContext");


  /**
   * @deprecated use {@link PyInspectionVisitor#PyInspectionVisitor(ProblemsHolder, TypeEvalContext)} instead
   */
  @Deprecated
  public PyInspectionVisitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
    myHolder = holder;
    myTypeEvalContext = PyInspectionVisitor.getContext(session);
    PluginException.reportDeprecatedUsage("this constructor", "");
  }

  public PyInspectionVisitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
    myHolder = holder;
    myTypeEvalContext = context;
  }

  @NotNull
  public static TypeEvalContext getContext(@NotNull LocalInspectionToolSession session) {
    TypeEvalContext context;
    synchronized (INSPECTION_TYPE_EVAL_CONTEXT) {
      context = session.getUserData(INSPECTION_TYPE_EVAL_CONTEXT);
      if (context == null) {
        final PsiFile sessionFile = session.getFile();
        final PsiFile contextFile = FileContextUtil.getContextFile(sessionFile);
        final PsiFile file = ObjectUtils.chooseNotNull(contextFile, sessionFile);

        context = TypeEvalContext.codeAnalysis(file.getProject(), file);
        session.putUserData(INSPECTION_TYPE_EVAL_CONTEXT, context);
      }
    }
    return context;
  }

  protected PyResolveContext getResolveContext() {
    return PyResolveContext.defaultContext(myTypeEvalContext);
  }

  @Nullable
  protected ProblemsHolder getHolder() {
    return myHolder;
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull @InspectionMessage String message) {
    if (element == null || element.getTextLength() == 0) {
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(element, message);
    }
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull @InspectionMessage String message,
                                       LocalQuickFix @NotNull ... quickFixes) {
    if (element == null || element.getTextLength() == 0) {
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(element, message, quickFixes);
    }
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull @InspectionMessage String message,
                                       @NotNull ProblemHighlightType type) {
    if (element == null || element.getTextLength() == 0) {
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(element, message, (LocalQuickFix)null, type, myHolder.isOnTheFly()));
    }
  }

  /**
   * The most full-blown version.
   *
   * @see ProblemDescriptor
   */
  protected final void registerProblem(
    @NotNull PsiElement psiElement,
    @NotNull @InspectionMessage String descriptionTemplate,
    @NotNull ProblemHighlightType highlightType,
    @Nullable HintAction hintAction,
    LocalQuickFix @NotNull... fixes) {
    registerProblem(psiElement, descriptionTemplate, highlightType, hintAction, null, fixes);
  }

  /**
   * The most full-blown version.
   *
   * @see ProblemDescriptor
   */
  protected final void registerProblem(
    @NotNull PsiElement psiElement,
    @NotNull @InspectionMessage String descriptionTemplate,
    @NotNull ProblemHighlightType highlightType,
    @Nullable HintAction hintAction,
    @Nullable TextRange rangeInElement,
    LocalQuickFix @NotNull... fixes) {
    if (myHolder != null && !(psiElement instanceof PsiErrorElement)) {
      myHolder.registerProblem(new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false,
                                                         rangeInElement, hintAction, myHolder.isOnTheFly()));
    }
  }
}
