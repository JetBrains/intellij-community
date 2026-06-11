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
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.diagnostic.PluginException;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyInspectionVisitor extends PyElementVisitor {
  public static final String POPULATE_TYPE_EVAL_CONTEXT_ON_CREATION_PROPERTY = "python.populate.type.eval.context.on.creation";

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
        if (Boolean.getBoolean(POPULATE_TYPE_EVAL_CONTEXT_ON_CREATION_PROPERTY)) {
          populateContextCache(context, file);
        }
        session.putUserData(INSPECTION_TYPE_EVAL_CONTEXT, context);
      }
    }
    return context;
  }

  private static void populateContextCache(@NotNull TypeEvalContext context, @NotNull PsiFile file) {
    PsiTreeUtil.processElements(file, element -> {
      if (element instanceof PyTypedElement typedElement) {
        context.getType(typedElement);
      }
      return true;
    });
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
    return myTypeEvalContext.getUsesExternalTypeEngine() ? ProblemHighlightType.INFORMATION : type;
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

  /**
   * Registers a {@link PyInspectionMessages.ProblemMessage}: the {@link PyInspectionMessages.ProblemMessage#description}
   * is the plain-text description shown in the Problems view, and the
   * {@link PyInspectionMessages.ProblemMessage#tooltip} is the HTML tooltip shown on editor hover.
   * Keeping the description plain-text means batch results and golden tests still see the original
   * message; the tooltip just adds {@code <code>} blocks around names.
   */
  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull PyInspectionMessages.ProblemMessage message) {
    registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull PyInspectionMessages.ProblemMessage message,
                                       @NotNull ProblemHighlightType type) {
    if (myHolder == null || element == null || !canRegisterProblem(element)) {
      return;
    }
    ProblemHighlightType effectiveType = downgradeHighlightForTypeEngine ? ProblemHighlightType.INFORMATION : type;
    myHolder.problem(element, message.description()).highlight(effectiveType).tooltip(message.tooltip()).register();
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull PyInspectionMessages.ProblemMessage message,
                                       @NotNull ProblemHighlightType type,
                                       @NotNull LocalQuickFix fix) {
    if (myHolder == null || element == null || !canRegisterProblem(element)) {
      return;
    }
    ProblemHighlightType effectiveType = downgradeHighlightForTypeEngine ? ProblemHighlightType.INFORMATION : type;
    myHolder.problem(element, message.description()).highlight(effectiveType).tooltip(message.tooltip()).fix(fix).register();
  }

  protected final void registerProblem(@Nullable PsiElement element,
                                       @NotNull PyInspectionMessages.ProblemMessage message,
                                       @NotNull ProblemHighlightType type,
                                       @NotNull ModCommandAction fix) {
    if (myHolder == null || element == null || !canRegisterProblem(element)) {
      return;
    }
    ProblemHighlightType effectiveType = downgradeHighlightForTypeEngine ? ProblemHighlightType.INFORMATION : type;
    myHolder.problem(element, message.description()).highlight(effectiveType).tooltip(message.tooltip()).fix(fix).register();
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
