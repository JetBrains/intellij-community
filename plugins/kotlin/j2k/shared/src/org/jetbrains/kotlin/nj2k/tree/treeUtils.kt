// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree

import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KProperty0

inline fun <reified T : JKElement> JKElement.parentOfType(): T? {
    return generateSequence(parent) { it.parent }.filterIsInstance<T>().firstOrNull()
}

fun JKElement.parents(): Sequence<JKElement> {
    return generateSequence(parent) { it.parent }
}

private fun <T : JKElement> KProperty0<Any>.detach(element: T) {
    if (element.parent == null) return
    // TODO: Fix when KT-16818 is implemented
    val boundReceiver = (this as CallableReference).boundReceiver
    require(boundReceiver != CallableReference.NO_RECEIVER)
    require(boundReceiver is JKElement)
    element.detach(boundReceiver)
}

fun <T : JKElement> KProperty0<T>.detached(): T =
    get().also { detach(it) }

fun <T : JKElement> KProperty0<List<T>>.detached(): List<T> =
    get().also { list -> list.forEach { detach(it) } }

fun <T : JKElement> T.detached(from: JKElement): T =
    also { it.detach(from) }

fun <E : JKTreeElement, D> applyRecursive(element: E, data: D, func: (JKTreeElement, D) -> JKTreeElement): E {
    val iterator = element.children.listIterator()

    fun applyRecursiveToList(child: List<JKTreeElement>): List<JKTreeElement> {
        val newChild = child.map { func(it, data) }
        child.forEach { it.detach(element) }
        iterator.set(child)
        newChild.forEach { it.attach(element) }
        return newChild
    }

    while (iterator.hasNext()) {
        when (val child = iterator.next()) {
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                val newChild = applyRecursiveToList(child as List<JKTreeElement>)
                iterator.set(newChild)
            }

            is JKTreeElement -> {
                val newChild = func(child, data)
                if (child !== newChild) {
                    child.detach(element)
                    iterator.set(newChild)
                    newChild.attach(element)
                }
            }

            else -> error("unsupported child type: ${child::class}")
        }
    }

    return element
}

fun <E : JKTreeElement> applyRecursive(element: E, func: (JKTreeElement) -> JKTreeElement): E =
    applyRecursive(element, data = null) { it, _ -> func(it) }

inline fun <reified T : JKTreeElement> T.copyTree(): T =
    copy().withFormattingFrom(this) as T

inline fun <reified T : JKTreeElement> T.copyTreeAndDetach(): T =
    copyTree().also {
        if (it.parent != null) it.detach(it.parent!!)
    }