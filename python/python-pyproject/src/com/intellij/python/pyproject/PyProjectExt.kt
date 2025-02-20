package com.intellij.python.pyproject

import com.intellij.psi.PsiElement
import com.jetbrains.python.Result
import com.jetbrains.python.Result.Companion.failure
import com.jetbrains.python.Result.Companion.success
import com.jetbrains.python.mapResult
import org.apache.tuweni.toml.TomlArray
import org.apache.tuweni.toml.TomlTable
import org.toml.lang.psi.TomlKeyValue as PsiTomlKeyValue
import org.toml.lang.psi.TomlTable as PsiTomlTable
import org.toml.lang.psi.TomlLiteral as PsiTomlLiteral
import kotlin.reflect.KClass

sealed class TomlTableSafeGetError {
  data class UnexpectedType(val path: String, val expected: KClass<*>, val actual: KClass<*>) : TomlTableSafeGetError()
  data class RequiredValueMissing(val path: String) : TomlTableSafeGetError()
}

inline fun <reified T> TomlTable.safeGet(key: String): Result<T?, TomlTableSafeGetError.UnexpectedType> {
  val value = get("\"$key\"")

  if (value == null) {
    return success(null)
  }

  if (value !is T) {
    return failure(
      TomlTableSafeGetError.UnexpectedType(
        key,
        T::class,
        value::class,
      )
    )
  }

  return success(value)
}

inline fun <reified T> TomlTable.safeGetRequired(key: String): Result<T, TomlTableSafeGetError> =
  safeGet<T>(key).mapResult {
    if (it == null) {
      failure(TomlTableSafeGetError.RequiredValueMissing(key))
    }
    else {
      success(it)
    }
  }

inline fun <reified T> TomlTable.safeGetArr(key: String): Result<List<T>?, TomlTableSafeGetError.UnexpectedType> {
  val array = safeGet<TomlArray>(key).getOr { return it }

  if (array == null) {
    return success(null)
  }

  return success(array.toList().mapIndexed { index, value ->
    if (value !is T) {
      return failure(TomlTableSafeGetError.UnexpectedType(
        "$key[$index]",
        T::class,
        value::class,
      ))
    }
    value
  })
}

fun <T, E, I> Result<T, E>.getOrIssue(issues: MutableList<I>, wrapper: (E) -> I, onNull: (() -> Unit)? = null): T? =
  when (this) {
    is Result.Success -> {
      if (result == null && onNull != null) {
        onNull()
      }
      result
    }
    is Result.Failure -> {
      issues += wrapper(error)
      null
    }
  }

fun PsiElement.findTomlHeader(name: String): PsiElement? =
  children.find { element ->
    (element as? PsiTomlTable)?.header?.key?.text == name
  }

fun PsiElement.findTomlValueByKey(name: String): PsiElement? =
  (children.find { element ->
    (element as? PsiTomlKeyValue)?.key?.text == name
  } as? PsiTomlKeyValue)?.value

fun PsiElement.findTomlLiteralsContaining(text: String): List<PsiElement> =
  children.filter { element ->
    (element as? PsiTomlLiteral)?.text?.contains(text) ?: false
  }