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

import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
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
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyInspectionVisitor extends PyElementVisitor {
  private final @Nullable ProblemsHolder myHolder;
  protected final TypeEvalContext myTypeEvalContext;
  /**
   * When set to {@code true}, all problems registered by this visitor will use
   * {@link ProblemHighlightType#INFORMATION} instead of their original highlight type.
   * This is used when an external type engine (e.g., Pyrefly) handles error highlighting,
   * but we still want to keep quick fixes available.
   */
  @ApiStatus.Internal
  protected boolean downgradeHighlightForTypeEngine = false;

  public static final Key<TypeEvalContext> INSPECTION_TYPE_EVAL_CONTEXT = Key.create("PyInspectionTypeEvalContext");


  /**
   * @deprecated use {@link PyInspectionVisitor#PyInspectionVisitor(ProblemsHolder, TypeEvalContext)} instead
   */
  @Deprecated
  public PyInspectionVisitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
    myHolder = holder;
    myTypeEvalContext = getContext(session);
    PluginException.reportDeprecatedUsage("this constructor", "");
  }

  public PyInspectionVisitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
    myHolder = holder;
    myTypeEvalContext = context;
  }

  public static @NotNull TypeEvalContext getContext(@NotNull LocalInspectionToolSession session) {
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

  protected @Nullable ProblemsHolder getHolder() {
    return myHolder;
  }

  /**
   * Returns {@link ProblemHighlightType#INFORMATION} when an external type engine handles highlighting,
   * otherwise returns the original type. Use for specific checks that the type engine covers,
   * in inspections where only some checks should be downgraded.
   */
  @ApiStatus.Internal
  protected @NotNull ProblemHighlightType effectiveHighlightType(@NotNull ProblemHighlightType type) {
    return myTypeEvalContext.getTypeEngine() != null ? ProblemHighlightType.INFORMATION : type;
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull @InspectionMessage String message) {
    if (!canRegisterProblem(element)) {
      return;
    }
    if (myHolder != null) {
      if (downgradeHighlightForTypeEngine) {
        myHolder.registerProblem(
          myHolder.getManager().createProblemDescriptor(element, message, (LocalQuickFix)null,
                                                        ProblemHighlightType.INFORMATION, myHolder.isOnTheFly()));
      }
      else {
        myHolder.registerProblem(element, message);
      }
    }
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull @InspectionMessage String message,
                                       LocalQuickFix @NotNull ... quickFixes) {
    if (!canRegisterProblem(element)) {
      return;
    }
    if (myHolder != null) {
      if (downgradeHighlightForTypeEngine) {
        registerProblem(element, message, ProblemHighlightType.INFORMATION, null, quickFixes);
      }
      else {
        myHolder.registerProblem(element, message, quickFixes);
      }
    }
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull @InspectionMessage String message,
                                       @NotNull ProblemHighlightType type) {
    if (!canRegisterProblem(element)) {
      return;
    }
    if (myHolder != null) {
      ProblemHighlightType effectiveType = downgradeHighlightForTypeEngine ? ProblemHighlightType.INFORMATION : type;
      myHolder.registerProblem(
        myHolder.getManager().createProblemDescriptor(element, message, (LocalQuickFix)null, effectiveType, myHolder.isOnTheFly()));
    }
  }

  private static boolean canRegisterProblem(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }

    if (element.getTextLength() > 0) {
      return true;
    }

    return element instanceof PyFile;
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
    @NotNull LocalQuickFix @NotNull ... fixes) {
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
    @NotNull LocalQuickFix @NotNull ... fixes) {
    if (myHolder != null && !(psiElement instanceof PsiErrorElement)) {
      ProblemHighlightType effectiveType = downgradeHighlightForTypeEngine ? ProblemHighlightType.INFORMATION : highlightType;
      myHolder.registerProblem(new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, effectiveType, false,
                                                         rangeInElement, hintAction, myHolder.isOnTheFly()));
    }
  }
}
