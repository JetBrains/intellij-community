// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.jdi.LocalVariableProxyImpl

internal sealed class ExistingVariable {
    interface This
    object UnlabeledThis : ExistingVariable(), This
    data class LabeledThis(val label: String) : ExistingVariable(), This

    data class Ordinary(val name: String) : ExistingVariable()
}

internal class ExistingVariables(thisVariables: List<LocalVariableProxyImpl>, ordinaryVariables: List<LocalVariableProxyImpl>) {
    private val set = HashSet<ExistingVariable>()

    var hasThisVariables: Boolean
        private set

    init {
        thisVariables.forEach {
            val thisVariable = it as? ThisLocalVariable ?: return@forEach
            val label = thisVariable.label
            val newExistingVariable: ExistingVariable = when {
                label != null -> ExistingVariable.LabeledThis(label)
                else -> ExistingVariable.UnlabeledThis
            }
            set.add(newExistingVariable)
        }

        hasThisVariables = thisVariables.isNotEmpty()

        ordinaryVariables.forEach { set += ExistingVariable.Ordinary(it.name()) }
    }

    fun add(variable: ExistingVariable): Boolean {
        val result = set.add(variable)
        if (result && !hasThisVariables && variable is ExistingVariable.This) {
            hasThisVariables = true
        }
        return result
    }
}