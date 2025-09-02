// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

import com.intellij.psi.PsiElement
import com.jetbrains.python.Result
import com.jetbrains.python.Result.Companion.failure
import com.jetbrains.python.Result.Companion.success
import com.jetbrains.python.mapResult
import org.apache.tuweni.toml.TomlArray
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.reflect.KClass
import org.toml.lang.psi.TomlKeyValue as PsiTomlKeyValue
import org.toml.lang.psi.TomlLiteral as PsiTomlLiteral
import org.toml.lang.psi.TomlTable as PsiTomlTable

/**
 * The error union used by [TomlTable.safeGet], [TomlTable.safeGetRequired] and [TomlTable.safeGetArr].
 */
@Internal
sealed class TomlTableSafeGetError {
  /**
   * Signifies an incorrect type at [path]. Expected value is signified by [expected], actual value is signified by [actual].
   */
  data class UnexpectedType(val path: String, val expected: KClass<*>, val actual: KClass<*>) : TomlTableSafeGetError()

  /**
   * Signifies a missing value at [path].
   */
  data class RequiredValueMissing(val path: String) : TomlTableSafeGetError()
}

/**
 * Attempts to extract a value of type [T] from an instance of [TomlTable] by key.
 * On an invalid type, returns an instance of [Result.Failure] with an error of [TomlTableSafeGetError.UnexpectedType].
 * On a missing value, returns an instance of [Result.Success] with a value of null.
 * On a present value with the valid type, returns an instance of [Result.Success] with that value.
 *
 * Example:
 *
 * ```kotlin
 * val foo = tomlTable.safeGet<String>("foo")
 * when (foo) {
 *   is Result.Success -> println("foo is ${foo.result}")
 *   is Result.Failure -> println("foo is of the incorrect type")
 * }
 * ```
 */
@Internal
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

/**
 * Attempts to extract a value of type [T] from an instance of [TomlTable] by key, asserting its presence.
 * On an invalid type, returns an instance of [Result.Failure] with an error of [TomlTableSafeGetError.UnexpectedType].
 * On a missing value, returns an instance of [Result.Failure] with an error of [TomlTableSafeGetError.RequiredValueMissing].
 * On a present value with the valid type, returns an instance of [Result.Success] with that value.
 *
 * Example:
 *
 * ```kotlin
 * tomlTable.safeGetRequired<String>("foo").getOr { error ->
 *   when (error) {
 *     is TomlTableSafeGetError.UnexpectedType -> print("incorrect type")
 *     is TomlTableSafeGetError.RequiredValueMissing -> print("value is missing")
 *   }
 *   return
 * }
 * ```
 */
@Internal
inline fun <reified T> TomlTable.safeGetRequired(key: String): Result<T, TomlTableSafeGetError> =
  safeGet<T>(key).mapResult {
    if (it == null) {
      failure(TomlTableSafeGetError.RequiredValueMissing(key))
    }
    else {
      success(it)
    }
  }

/**
 * Attempts to extract an array of values of type [T] from an instance of [TomlTable] by key.
 * On an invalid type, returns an instance of [Result.Failure] with an error of [TomlTableSafeGetError.UnexpectedType].
 * On a missing value, returns an instance of [Result.Success] with a value of null.
 * On a present value with the valid type, returns an instance of [Result.Success] with a [List] of type [T].
 *
 * Example:
 *
 * ```kotlin
 * val foo = tomlTable.safeGetArr<String>("foo")
 * when (foo) {
 *   is Result.Success -> foo.result?.forEachIndexed { index, value ->
 *     print("foo[$index] is $value")
 *   } ?: {
 *     print("foo is null")
 *   }
 *   is Result.Failure -> println("foo is of the incorrect type")
 * }
 * ```
 */
@Internal
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

/**
 * Attempts to unwrap a [Result].
 * On success, returns the unwrapped value and calls the [onNull] callback (if provided) when the value is null.
 * On failure, wraps the error by calling the [wrapper] callback, adds the result to the provided [issues] list, then return null.
 * This function is useful for cases requiring to collect a list of parsing issues.
 *
 * Example:
 *
 * ```kotlin
 * val issues = mutableListOf<>
 * ```
 */
@Internal
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

/**
 * Attempts to find a TOML header in [PsiElement] by the name of [name].
 * Returns null if the file wasn't TOML or the header wasn't found.
 *
 * Example:
 *
 * ```kotlin
 * val dependencies = psiFile.findTomlHeader("project").findTomlValueByKey("dependencies")
 * ```
 */
@Internal
fun PsiElement.findTomlHeader(name: String): PsiElement? =
  children.find { element ->
    (element as? PsiTomlTable)?.header?.key?.text == name
  }

/**
 * Attempts to find a value of a [PsiTomlKeyValue] by the key of [key] in an instance of [PsiElement].
 * Returns null if the file wasn't TOML or the key wasn't found.
 *
 * Example:
 *
 * ```kotlin
 * val dependencies = psiFile.findTomlHeader("project").findTomlValueByKey("dependencies")
 * ```
 */
@Internal
fun PsiElement.findTomlValueByKey(key: String): PsiElement? =
  (children.find { element ->
    (element as? PsiTomlKeyValue)?.key?.text == key
  } as? PsiTomlKeyValue)?.value

/**
 * Attempts to find all [PsiTomlLiteral]s found within the children of [PsiElement] that have the text containing [text].
 *
 * Example:
 *
 * ```kotlin
 * val dependencies = psiFile.findTomlHeader("project").findTomlValueByKey("dependencies")
 * val requests = dependencies.findTomlLiteralsContaining("requests")
 * ```
 */
@Internal
fun PsiElement.findTomlLiteralsContaining(text: String): List<PsiElement> =
  children.filter { element ->
    (element as? PsiTomlLiteral)?.text?.contains(text) ?: false
  }