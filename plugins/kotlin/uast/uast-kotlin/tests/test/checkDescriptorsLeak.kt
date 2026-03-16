// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.kotlin

import com.intellij.util.PairProcessor
import com.intellij.util.ref.DebugReflectionUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.cli.jvm.compiler.CliTraceHolder
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.uast.UElement

private val descriptorsClasses = listOf(AnnotationDescriptor::class, DeclarationDescriptor::class)

fun checkDescriptorsLeak(node: UElement) {
    DebugReflectionUtil.walkObjects(
        10,
        mapOf(node to node.javaClass.name),
        Any::class.java,
        { it !is CliTraceHolder },
        PairProcessor { value, backLink ->
            descriptorsClasses.find { it.isInstance(value) }?.let {
                TestCase.fail("""Leaked descriptor ${it.qualifiedName} in ${node.javaClass.name}\n$backLink""")
                false
            } ?: true
        })
}