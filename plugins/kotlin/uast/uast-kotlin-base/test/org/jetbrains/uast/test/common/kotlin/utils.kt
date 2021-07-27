// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import com.intellij.util.PairProcessor
import com.intellij.util.ref.DebugReflectionUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.cli.jvm.compiler.CliTraceHolder
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile

internal fun UFile.findFacade(): UClass? {
    return classes.find { it.psi is KtLightClassForFacade }
}

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