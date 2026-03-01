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
class PyIntersectionType private constructor(members: Collection<PyType?>) : PyCompositeType {
  override val members: Set<PyType?> = Collections.unmodifiableSet<PyType?>(LinkedHashSet(members))

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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PyIntersectionType

    return members == other.members
  }

  override fun hashCode(): Int {
    return members.hashCode()
  }

  override fun toString(): String {
    return "PyIntersectionType: $name"
  }

  companion object {
    @JvmStatic
    fun intersection(vararg types: PyType?): PyType? {
      return intersection(types.toList())
    }

    @JvmStatic
    fun intersection(types: Collection<PyType?>): PyType? {
      val newMembers = buildSet {
        for (member in types) {
          if (member is PyIntersectionType) {
            addAll(member.members)
          }
          else {
            add(member)
          }
        }
      }
      return if (newMembers.size > 1) PyIntersectionType(newMembers) else newMembers.firstOrNull()
    }
  }
}
