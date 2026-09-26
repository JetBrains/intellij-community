// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<PyAnyType>()

/** The maximum number of characters of user code that one detail line can hold. */
private const val MAX_SNIPPET_LENGTH = 200

/** The number of trailing characters that a shortened detail line keeps. */
private const val SNIPPET_SUFFIX_LENGTH = 40

/**
 * represents `typing.Any` and `Unknown`/untyped
 */
@ApiStatus.Experimental
sealed class PyAnyType private constructor(override val name: String) : PyType {

  object Any : PyAnyType(PyNames.ANY_TYPE)
  object Unknown : PyAnyType(PyNames.UNKNOWN_TYPE)

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> {
    return emptyList()
  }

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<Any> {
    return emptyArray()
  }

  override val isBuiltin: Boolean = false

  override fun assertValid(message: String?) {
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? = when(this) {
    is Any -> visitor.visitAnyType()
    is Unknown -> visitor.visitUnknownType()
  }

  override fun toString(): String = name

  companion object {
    /** The single gate for `Any`/`Unknown` support. Read it through [isEnabled]. */
    @ApiStatus.Internal
    const val REGISTRY_KEY: String = "python.type.any"

    @JvmStatic
    val isEnabled: Boolean get() = Registry.`is`(REGISTRY_KEY)

    /**
     * Guards the `Any`/`Unknown` invariant: with the support enabled a [PyType] must never be raw `null`, and
     * with the support disabled a [PyAnyType] must never leak out.
     *
     * A violation fails the test in a unit test. In a running IDE it goes to the log as an error, which an EAP
     * build then reports to the exception analyzer. The user gets no notification, because the user can do
     * nothing about the problem. The check runs only in an EAP or an internal build.
     */
    @JvmStatic
    @JvmOverloads
    fun validate(it: PyType?, context: PsiElement? = null) {
      val application = ApplicationManager.getApplication() ?: return
      val isTest = application.isUnitTestMode
      if (!isTest && !application.isEAP && !application.isInternal) return

      val problem = when {
        isEnabled && it == null -> "A Python type was `null` while `Any`/`Unknown` support was enabled."
        !isEnabled && it is PyAnyType -> "A Python type was `PyAnyType` while `Any`/`Unknown` support was disabled."
        else -> return
      }

      // Build the context only for a real violation. It reads PSI text, which the hot path must not pay for.
      val details = describeContext(context)
      if (isTest) {
        throw AssertionError(details.joinToString("\n", prefix = "$problem\n"))
      }

      // `problem` stays the message, so that the exception analyzer groups the reports by the broken
      // invariant. The stack trace shows which inference path broke it, and the details show on what code.
      LOG.error(problem, Throwable(problem), *details)
    }

    @JvmStatic
    val any: Any? get() = if (isEnabled) Any else null
    @JvmStatic
    val unknown: Unknown? get() = if (isEnabled) Unknown else null
  }
}

/**
 * Describes the element whose inference broke the invariant, in enough detail to reproduce the problem.
 * A single element text is too little, because the element is often one reference.
 */
private fun describeContext(context: PsiElement?): Array<String> {
  if (context == null) return arrayOf("context: not available")
  if (!context.isValid) return arrayOf("context: an invalid ${context.javaClass.name}")

  val file = context.containingFile
  val statement = PsiTreeUtil.getParentOfType(context, PyStatement::class.java, false)
  val owner = PsiTreeUtil.getParentOfType(context, PyFunction::class.java, PyClass::class.java)
  return arrayOf(
    "element      : ${context.javaClass.name} at ${file?.name}:${context.textOffset}",
    "psi path     : ${psiPath(context)}",
    "owner        : ${(owner as? PyQualifiedNameOwner)?.qualifiedName ?: "<module level>"}",
    "element text : ${abbreviate(context.text)}",
    "statement    : ${abbreviate(statement?.text)}",
  )
}

private fun psiPath(element: PsiElement): String =
  generateSequence(element) { it.parent }
    .takeWhile { it !is PsiFile }
    .take(12)
    .joinToString(" < ") { it.javaClass.simpleName }

private fun abbreviate(text: String?): String =
  if (text == null) "<none>" else StringUtil.shortenTextWithEllipsis(text, MAX_SNIPPET_LENGTH, SNIPPET_SUFFIX_LENGTH, true)
