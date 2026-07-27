// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.openapi.util.Comparing
import com.intellij.util.containers.ContainerUtil
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.function.Predicate

/**
 * Reflection helper used by Python code style settings to exclude bookkeeping fields (such as the
 * active code style profile id) from `equals` comparisons.
 * 
 * 
 * Mirrors the Kotlin plugin helper `org.jetbrains.kotlin.idea.util.ReflectionUtil`; the
 * platform [com.intellij.util.ReflectionUtil.comparePublicNonFinalFields] has no skip variant.
 */
object PyCodeStyleReflectionUtil {
    @JvmStatic
    fun comparePublicNonFinalFieldsWithSkip(first: Any, second: Any): Boolean {
        return comparePublicNonFinalFields(first, second, { field ->
            field.getAnnotation(
                SkipInEquals::class.java
            ) == null
        })
    }

    private fun comparePublicNonFinalFields(
        first: Any,
        second: Any,
        acceptPredicate: Predicate<Field>
    ): Boolean {
        val firstFields: MutableSet<Field?> = ContainerUtil.newHashSet(*first.javaClass.fields)

        for (field in second.javaClass.fields) {
            if (firstFields.contains(field)) {
                if (isPublic(field) && !isFinal(field) && (acceptPredicate.test(field))) {
                    try {
                        if (!Comparing.equal<Any?>(field.get(first), field.get(second))) {
                            return false
                        }
                    } catch (e: IllegalAccessException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }

        return true
    }

    private fun isPublic(field: Field): Boolean {
        return (field.modifiers and Modifier.PUBLIC) != 0
    }

    private fun isFinal(field: Field): Boolean {
        return (field.modifiers and Modifier.FINAL) != 0
    }

    @Retention(AnnotationRetention.RUNTIME)
    annotation class SkipInEquals
}
