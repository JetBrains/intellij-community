// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.util.Range
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.util.OperatorNameConventions
import javax.swing.Icon

class KotlinLambdaSmartStepTarget(
    label: String,
    highlightElement: KtFunction,
    lines: Range<Int>,
    val isInline: Boolean,
    val isSuspend: Boolean
) : SmartStepTarget(label, highlightElement, true, lines) {
    override fun getIcon(): Icon = KotlinIcons.LAMBDA

    fun getLambda() = highlightElement as KtFunction

    companion object {
        fun calcLabel(descriptor: DeclarationDescriptor, paramName: Name): String {
            return "${descriptor.name.asString()}: ${paramName.asString()}.${OperatorNameConventions.INVOKE.asString()}()"
        }
    }
}