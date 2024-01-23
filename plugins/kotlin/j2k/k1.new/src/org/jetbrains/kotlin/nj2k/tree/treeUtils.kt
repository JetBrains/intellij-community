// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.tree

import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KProperty0

internal inline fun <reified T : JKElement> JKElement.parentOfType(): T? {
    return generateSequence(parent) { it.parent }.filterIsInstance<T>().firstOrNull()
}

internal fun JKElement.parents(): Sequence<JKElement> {
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

internal fun <T : JKElement> KProperty0<T>.detached(): T =
    get().also { detach(it) }

internal fun <T : JKElement> KProperty0<List<T>>.detached(): List<T> =
    get().also { list -> list.forEach { detach(it) } }

internal fun <T : JKElement> T.detached(from: JKElement): T =
    also { it.detach(from) }

internal fun <R : JKTreeElement, T> applyRecursive(
    element: R,
    data: T,
    onElementChanged: (JKTreeElement, JKTreeElement) -> Unit,
    func: (JKTreeElement, T) -> JKTreeElement
): R {
    fun <T> applyRecursiveToList(
        element: JKTreeElement,
        child: List<JKTreeElement>,
        iter: MutableListIterator<Any>,
        data: T,
        func: (JKTreeElement, T) -> JKTreeElement
    ): List<JKTreeElement> {

        val newChild = child.map {
            func(it, data)
        }

        child.forEach { it.detach(element) }
        iter.set(child)
        newChild.forEach { it.attach(element) }
        newChild.zip(child).forEach { (old, new) ->
            if (old !== new) {
                onElementChanged(new, old)
            }
        }
        return newChild
    }

    val iter = element.children.listIterator()
    while (iter.hasNext()) {
        val child = iter.next()

        if (child is List<*>) {
            @Suppress("UNCHECKED_CAST")
            iter.set(applyRecursiveToList(element, child as List<JKTreeElement>, iter, data, func))
        } else if (child is JKTreeElement) {
            val newChild = func(child, data)
            if (child !== newChild) {
                child.detach(element)
                iter.set(newChild)
                newChild.attach(element)
                onElementChanged(newChild, child)
            }
        } else {
            error("unsupported child type: ${child::class}")
        }
    }
    return element
}

internal fun <R : JKTreeElement> applyRecursive(
    element: R,
    func: (JKTreeElement) -> JKTreeElement
): R = applyRecursive(element, null, { _, _ -> }) { it, _ -> func(it) }

internal inline fun <reified T : JKTreeElement> T.copyTree(): T =
    copy().withFormattingFrom(this) as T

internal inline fun <reified T : JKTreeElement> T.copyTreeAndDetach(): T =
    copyTree().also {
        if (it.parent != null) it.detach(it.parent!!)
    }