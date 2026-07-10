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
package com.jetbrains.python.inspections

import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.diagnostic.PluginException
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ObjectUtils
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.TypeEvalContext.Companion.codeAnalysis
import org.jetbrains.annotations.ApiStatus
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

abstract class PyInspectionVisitor(
  protected open val holder: ProblemsHolder?,
  @JvmField
  protected val myTypeEvalContext: TypeEvalContext,
) : PyElementVisitor() {

  /**
   * When set to `true`, all problems registered by this visitor will use
   * [ProblemHighlightType.INFORMATION] instead of their original highlight type.
   * This is used when an external type engine (e.g., Pyrefly) handles error highlighting,
   * but we still want to keep quick fixes available.
   */
  @JvmField
  @ApiStatus.Internal
  var downgradeHighlightForTypeEngine: Boolean = false

  @Deprecated("use {@link PyInspectionVisitor#PyInspectionVisitor(ProblemsHolder, TypeEvalContext)} instead")
  constructor(holder: ProblemsHolder?, session: LocalInspectionToolSession) : this(holder, getContext(session)) {
    PluginException.reportDeprecatedUsage("this constructor", "")
  }

  protected val resolveContext: PyResolveContext
    get() = PyResolveContext.defaultContext(myTypeEvalContext)

  /**
   * Returns [ProblemHighlightType.INFORMATION] when an external type engine handles highlighting,
   * otherwise returns the original type. Use for specific checks that the type engine covers,
   * in inspections where only some checks should be downgraded.
   */
  @ApiStatus.Internal
  protected fun effectiveHighlightType(type: ProblemHighlightType): ProblemHighlightType {
    return if (myTypeEvalContext.usesExternalTypeEngine) ProblemHighlightType.INFORMATION else type
  }

  @get:ApiStatus.Internal
  val isOnTheFly: Boolean
    get() = holder?.isOnTheFly == true

  protected fun registerProblem(
    element: PsiElement?,
    @InspectionMessage message: @InspectionMessage String,
  ) {
    if (!element.canRegisterProblem) {
      return
    }
    holder?.let { holder ->
      if (downgradeHighlightForTypeEngine) {
        holder.registerProblem(
          holder.manager.createProblemDescriptor(element, message, null as LocalQuickFix?,
                                                 ProblemHighlightType.INFORMATION, holder.isOnTheFly))
      }
      else {
        holder.registerProblem(element, message)
      }
    }
  }

  protected fun registerProblem(
    element: PsiElement?,
    @InspectionMessage message: @InspectionMessage String,
    vararg quickFixes: LocalQuickFix,
  ) {
    if (!element.canRegisterProblem) {
      return
    }
    holder?.let { holder ->
      if (downgradeHighlightForTypeEngine) {
        registerProblem(element, message, ProblemHighlightType.INFORMATION, null as HintAction?, *quickFixes)
      }
      else {
        holder.registerProblem(element, message, *quickFixes)
      }
    }
  }

  @ApiStatus.Internal
  fun registerProblem(
    element: PsiElement?,
    @InspectionMessage message: @InspectionMessage String,
    type: ProblemHighlightType,
  ) {
    if (!element.canRegisterProblem) {
      return
    }
    holder?.let { holder ->
      val effectiveType = if (downgradeHighlightForTypeEngine) ProblemHighlightType.INFORMATION else type
      holder.registerProblem(
        holder.manager.createProblemDescriptor(element, message, null as LocalQuickFix?, effectiveType, holder.isOnTheFly))
    }
  }

  /**
   * Registers a [ProblemMessage]: the [ProblemMessage.description]
   * is the plain-text description shown in the Problems view, and the
   * [ProblemMessage.tooltip] is the HTML tooltip shown on editor hover.
   * Keeping the description plain-text means batch results and golden tests still see the original
   * message; the tooltip just adds `<code>` blocks around names.
   */
  protected fun registerProblem(
    element: PsiElement?,
    message: ProblemMessage,
  ) {
    registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
  }

  @ApiStatus.Internal
  fun registerProblem(
    element: PsiElement?,
    message: ProblemMessage,
    type: ProblemHighlightType,
  ) {
    registerProblemMessage(element, message, type, null)
  }

  protected fun registerProblem(
    element: PsiElement?,
    message: ProblemMessage,
    type: ProblemHighlightType,
    fix: LocalQuickFix,
  ) {
    registerProblemMessage(element, message, type, null, fix)
  }

  protected fun registerProblem(
    element: PsiElement?,
    message: ProblemMessage,
    type: ProblemHighlightType,
    fix: ModCommandAction,
  ) {
    if (holder == null || element == null || !element.canRegisterProblem) {
      return
    }
    val effectiveType = if (downgradeHighlightForTypeEngine) ProblemHighlightType.INFORMATION else type
    holder!!.problem(element, message.description).highlight(effectiveType).tooltip(message.tooltip).fix(fix).register()
  }

  protected fun registerProblem(
    element: PsiElement?,
    message: ProblemMessage,
    type: ProblemHighlightType,
    vararg fixes: LocalQuickFix,
  ) {
    registerProblemMessage(element, message, type, null, *fixes)
  }

  protected fun registerProblem(
    element: PsiElement?,
    message: ProblemMessage,
    type: ProblemHighlightType,
    vararg fixes: LocalQuickFix?,
    rangeInElement: TextRange?,
  ) {
    if (element is PsiErrorElement) {
      return
    }
    registerProblemMessage(element, message, type, rangeInElement, *fixes)
  }

  /**
   * The most full-blown version.
   *
   * @see ProblemDescriptor
   */
  protected fun registerProblem(
    psiElement: PsiElement,
    @InspectionMessage descriptionTemplate: @InspectionMessage String,
    highlightType: ProblemHighlightType,
    hintAction: HintAction?,
    vararg fixes: LocalQuickFix,
  ) {
    registerProblem(psiElement, descriptionTemplate, highlightType, hintAction, null, *fixes)
  }

  /**
   * The most full-blown version.
   *
   * @see ProblemDescriptor
   */
  protected fun registerProblem(
    psiElement: PsiElement,
    @InspectionMessage descriptionTemplate: @InspectionMessage String,
    highlightType: ProblemHighlightType,
    hintAction: HintAction?,
    rangeInElement: TextRange?,
    vararg fixes: LocalQuickFix,
  ) {
    if (psiElement is PsiErrorElement) {
      return
    }
    holder?.let { holder ->
      val effectiveType = if (downgradeHighlightForTypeEngine) ProblemHighlightType.INFORMATION else highlightType
      holder.registerProblem(
        ProblemDescriptorImpl(
          psiElement, psiElement, descriptionTemplate, fixes, effectiveType, false,
          rangeInElement, hintAction, holder.isOnTheFly
        )
      )
    }
  }

  private fun registerProblemMessage(
    element: PsiElement?,
    message: ProblemMessage,
    type: ProblemHighlightType,
    rangeInElement: TextRange?,
    vararg fixes: LocalQuickFix?,
  ) {
    if (holder == null || element == null || !element.canRegisterProblem) {
      return
    }
    val effectiveType = if (downgradeHighlightForTypeEngine) ProblemHighlightType.INFORMATION else type
    val builder = holder!!.problem(element, message.description).highlight(effectiveType).tooltip(message.tooltip)
    if (rangeInElement != null) {
      builder.range(rangeInElement)
    }
    for (fix in fixes) {
      if (fix != null) builder.fix(fix)
    }
    builder.register()
  }

  /**
   * Registers a problem whose one-line [message] is the plain-text description, but whose editor hover shows a
   * richer [tooltip] (HTML). Keeping the description and tooltip separate means batch results and golden tests
   * still see only the plain message. Honors the type-engine downgrade like the other registration helpers; any
   * quick [fixes] are attached (nulls are ignored).
   */
  @ApiStatus.Internal
  protected fun registerProblemWithTooltip(
    element: PsiElement?,
    @InspectionMessage message: String,
    type: ProblemHighlightType,
    @NlsContexts.Tooltip tooltip: String,
    vararg fixes: LocalQuickFix?,
  ) {
    if (holder == null || element == null || !element.canRegisterProblem) {
      return
    }
    val effectiveType = if (downgradeHighlightForTypeEngine) ProblemHighlightType.INFORMATION else type
    val builder = holder!!.problem(element, message).highlight(effectiveType).tooltip(tooltip)
    for (fix in fixes) {
      if (fix != null) builder.fix(fix)
    }
    builder.register()
  }

  /**
   * Registers [message] as the flat description and, **only** in on-the-fly mode, attaches the hover tooltip
   * produced by [tooltip]. The supplier is invoked lazily and exclusively for on-the-fly highlighting, so any
   * expensive work behind it (e.g. re-running a type match to build a breakdown) is skipped in batch mode; when
   * it yields `null` — or in batch mode — only the plain [message] is registered. This folds the
   * `if (isOnTheFly) registerProblemWithTooltip(...) else registerProblem(...)` branch into a single call.
   */
  @ApiStatus.Internal
  fun registerProblemWithTooltip(
    element: PsiElement?,
    @InspectionMessage message: String,
    type: ProblemHighlightType,
    vararg fixes: LocalQuickFix,
    tooltip: () -> @NlsContexts.Tooltip String?,
  ) {
    if (!element.canRegisterProblem) {
      return
    }
    val onTheFlyTooltip = if (holder?.isOnTheFly == true) tooltip() else null
    if (onTheFlyTooltip != null) {
      registerProblemWithTooltip(element, message, type, onTheFlyTooltip, *fixes)
    }
    else if (fixes.isEmpty()) {
      registerProblem(element, message, type)
    }
    else {
      registerProblem(element, message, type, null as HintAction?, *fixes)
    }
  }

  /**
   * [ProblemMessage] variant of the supplier-based [registerProblemWithTooltip]: on-the-fly the [tooltip] supplier
   * (when it yields non-null) replaces the message's own tooltip; otherwise the message is registered with its
   * plain [ProblemMessage.description] and [ProblemMessage.tooltip].
   */
  @ApiStatus.Internal
  fun registerProblemWithTooltip(
    element: PsiElement?,
    message: ProblemMessage,
    type: ProblemHighlightType,
    vararg fixes: LocalQuickFix,
    tooltip: () -> @NlsContexts.Tooltip String?,
  ) {
    if (!element.canRegisterProblem) {
      return
    }
    val onTheFlyTooltip = if (holder?.isOnTheFly == true) tooltip() else null
    if (onTheFlyTooltip != null) {
      registerProblemWithTooltip(element, message.description, type, onTheFlyTooltip, *fixes)
    }
    else {
      registerProblem(element, message, type, *fixes)
    }
  }

  companion object {
    const val POPULATE_TYPE_EVAL_CONTEXT_ON_CREATION_PROPERTY: String = "python.populate.type.eval.context.on.creation"

    val INSPECTION_TYPE_EVAL_CONTEXT: Key<TypeEvalContext?> = Key("PyInspectionTypeEvalContext")


    @JvmStatic
    fun getContext(session: LocalInspectionToolSession): TypeEvalContext {
      var context: TypeEvalContext?
      synchronized(INSPECTION_TYPE_EVAL_CONTEXT) {
        context = session.getUserData<TypeEvalContext?>(INSPECTION_TYPE_EVAL_CONTEXT)
        if (context == null) {
          val sessionFile = session.file
          val contextFile = FileContextUtil.getContextFile(sessionFile)
          val file = ObjectUtils.chooseNotNull<PsiFile>(contextFile, sessionFile)
          context = codeAnalysis(file.getProject(), file)
          if (java.lang.Boolean.getBoolean(POPULATE_TYPE_EVAL_CONTEXT_ON_CREATION_PROPERTY)) {
            populateContextCache(context, file)
          }
          session.putUserData<TypeEvalContext?>(INSPECTION_TYPE_EVAL_CONTEXT, context)
        }
      }
      return context!!
    }

    private fun populateContextCache(context: TypeEvalContext, file: PsiFile) {
      PsiTreeUtil.processElements(file) { element ->
        if (element is PyTypedElement) {
          context.getType(element)
        }
        true
      }
    }

    @OptIn(ExperimentalContracts::class)
    private val PsiElement?.canRegisterProblem: Boolean
      get() {
        contract {
          returns(true) implies (this@canRegisterProblem != null)
        }
        if (this == null) {
          return false
        }

        if (this.getTextLength() > 0) {
          return true
        }

        return this is PyFile
      }
  }
}
