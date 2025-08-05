package com.jetbrains.python.psi.types

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.intellij.util.SmartList
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import org.jetbrains.annotations.ApiStatus
import java.util.Collections

/**
 * The unsafe union type is different from the regular "safe" `PyUnionType` in that it's considered a subtype of another type if
 * *any* of its member types is compatible with it.
 *
 * In mathematical terms, `UnsafeUnion[T1, ..., Tn] <: T iff ∃ Ti: Ti ∈ {T1, ..., Tn} & Ti <: T`.
 * At the same time, `T <: UnsafeUnion[T1, ..., Tn] iff ∃ Ti: Ti ∈ {T1, ..., Tn} & T <: Ti` (similarly to regular "safe" unions).
 *
 * One can also tell that an unsafe union behaves like a regular union when it's considered a supertype of another type,
 * and as an intersection type when it's considered a subtype of another type.
 *
 * In various discussion this type was also named `AnyOf`. Indeed, it behaves like a local `Any`, i.e.
 * a local top and bottom type on a restricted set of types.
 *
 * In practice, it means not only `int` is considered compatible with `int |str` (expected for unions),
 * but also `int | str` is considered compatible with `int` making things like the following acceptable:
 *
 * ```
 * x: int | str
 * y: int = x  # no type checker error
 * x.upper()  # no type checker error
 * ```
 *
 * Such an inherently unsound entity in the type system is needed to support cases where in the absence of explicit type hints,
 * we have to guess the right type to provide code completion and other code assistance, but don't want to trigger any false positives.
 *
 * One useful concept following from such semantics is the "weak type" that is an unsafe union of a type with `Any`.
 * A weak type provides code completion and attribute name resolution just as the type it wraps, but it doesn't trigger any warnings
 * as `Any`.
 *
 * Previously, the regular `PyUnionType` used to have this semantics. Now it has normal "safe" semantics, and unsafe unions are
 * mostly kept for several heuristics for untyped Python code that we don't want to break.
 *
 * See the relevant discussions about unsafe unions
 *  - https://github.com/python/typing/issues/566
 *  - https://github.com/python/mypy/issues/1693#issuecomment-226643848
 *  - https://mail.python.org/archives/list/typing-sig@python.org/message/TTPVTIKZ6BFVWZBUYR2FN2SPGB63Z7PH/
 *
 * @see PyUnionType.createWeakType
 */
@ApiStatus.Experimental
class PyUnsafeUnionType private constructor(members: Collection<PyType?>) : PyType {
  val members: Set<PyType?> = LinkedHashSet(members)
    get() = Collections.unmodifiableSet<PyType?>(field)

  override fun resolveMember(name: String, location: PyExpression?, direction: AccessDirection, resolveContext: PyResolveContext): List<RatedResolveResult?>? {
    val ret = SmartList<RatedResolveResult?>()
    var allNulls = true
    for (member in members) {
      if (member != null) {
        val result: MutableList<out RatedResolveResult?>? = member.resolveMember(name, location, direction, resolveContext)
        if (result != null) {
          allNulls = false
          ret.addAll(result)
        }
      }
    }
    return if (allNulls) null else ret
  }

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement?, context: ProcessingContext?): Array<out Any> {
    return members.flatMap { it?.getCompletionVariants(completionPrefix, location, context)?.asList() ?: emptyList() }
      .distinct()
      .toTypedArray()
  }

  override fun getName(): @NlsSafe String? {
    return members.joinToString(separator = " | ") { it?.name ?: "Any" }
  }

  override fun isBuiltin(): Boolean {
    return members.all { it != null && it.isBuiltin }
  }

  override fun assertValid(message: String?) {
    for (member in members) {
      member?.assertValid(message)
    }
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T {
    if (visitor is PyTypeVisitorExt<T>) {
      return visitor.visitPyUnsafeUnionType(this)
    }
    return visitor.visitPyType(this)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PyUnsafeUnionType

    return members == other.members
  }

  override fun hashCode(): Int {
    return members.hashCode()
  }

  override fun toString(): String {
    return "PyUnsafeUnionType: $name"
  }

  companion object {
    @JvmStatic
    fun unsafeUnion(vararg types: PyType?): PyType? {
      if (!PyUnionType.isStrictSemanticsEnabled()) {
        return PyUnionType.union(types.toList())
      }
      return unsafeUnion(types.toList())
    }

    @JvmStatic
    fun unsafeUnion(types: Collection<PyType?>): PyType? {
      if (!PyUnionType.isStrictSemanticsEnabled()) {
        return PyUnionType.union(types)
      }
      val newMembers = buildSet {
        for (member in types) {
          if (member is PyUnsafeUnionType) {
            addAll(member.members)
          }
          else {
            add(member)
          }
        }
      }
      return if (newMembers.size > 1) PyUnsafeUnionType(types) else newMembers.firstOrNull()
    }
  }
}