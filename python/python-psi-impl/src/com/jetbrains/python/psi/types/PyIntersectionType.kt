package com.jetbrains.python.psi.types

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.intellij.util.SmartList
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import org.jetbrains.annotations.ApiStatus
import java.util.Collections

@ApiStatus.Experimental
class PyIntersectionType private constructor(members: Collection<PyType?>) : PyCompositeTypeBase() {
  override val memberSet: Set<PyType?> = Collections.unmodifiableSet(LinkedHashSet(members))

  override val members: Set<PyType?>
    get() = memberSet

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult>? {
    val ret = SmartList<RatedResolveResult>()
    var allNulls = true
    for (member in members) {
      if (member != null) {
        val result = member.resolveMember(name, location, direction, resolveContext)
        if (result != null) {
          allNulls = false
          ret.addAll(result)
        }
      }
    }
    return if (allNulls) null else ret
  }

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<Any> {
    return members.flatMap { it?.getCompletionVariants(completionPrefix, location, context)?.asList() ?: emptyList() }
      .distinct()
      .toTypedArray()
  }

  override val name: @NlsSafe String = members.joinToString(separator = " & ") { it?.name ?: "Any" }

  override val isBuiltin: Boolean = members.all { it != null && it.isBuiltin }

  override fun assertValid(message: String?) {
    for (member in members) {
      member?.assertValid(message)
    }
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    if (visitor is PyTypeVisitorExt<T>) {
      return visitor.visitPyIntersectionType(this)
    }
    return visitor.visitPyType(this)
  }

  override fun toString(): String {
    return "PyIntersectionType: $name"
  }

  companion object {
    @JvmStatic
    fun intersection(vararg types: PyType?): PyType? {
      return intersection(types.toList())
    }

    /**
     * Constructs an intersection of the given types.
     *
     * If the resulting intersection would be empty, returns [PyTopType], which is the natural colapse of an intersection.
     */
    @JvmStatic
    fun intersection(types: Collection<PyType?>): PyType? {
      return intersectionOrDefault(types, PyTopType)
    }

    /**
     * Constructs an intersection of the given types, falling back to Unknown instead of [PyTopType].
     *
     * An intersection of no types is the type that constrains nothing, i.e. the top type.
     */
    @JvmStatic
    fun intersectionOrUnknown(types: Collection<PyType?>): PyType? {
      return intersectionOrDefault(types, PyAnyType.unknown)
    }

    private fun intersectionOrDefault(types: Collection<PyType?>, defaultResult: PyType?): PyType? {
      val newMembers = buildSet {
        for (member in types) {
          if (member is PyNeverType) return member
          if (member is PyIntersectionType) {
            addAll(member.members)
          }
          else {
            add(member)
          }
        }
      }
      return when {
        newMembers.size > 1 -> PyIntersectionType(newMembers)
        newMembers.isEmpty() -> defaultResult
        else -> newMembers.single()
      }
    }
  }
}
