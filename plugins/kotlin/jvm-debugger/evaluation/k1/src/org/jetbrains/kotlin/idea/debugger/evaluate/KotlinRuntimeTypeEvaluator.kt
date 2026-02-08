// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.CommonClassNames
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.ClassType
import com.sun.jdi.Value
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.org.objectweb.asm.Type as AsmType

@K1Deprecation
abstract class KotlinRuntimeTypeEvaluator(
    editor: Editor?,
    expression: KtExpression,
    context: DebuggerContextImpl,
    indicator: ProgressIndicator
) : KotlinRuntimeTypeEvaluatorBase<KotlinType>(editor, expression, context, indicator) {

    override fun getCastableRuntimeType(scope: GlobalSearchScope, value: Value): KotlinType? {

        val myValue = value.asValue()
        var psiClass = myValue.asmType.getClassDescriptor(scope)
        if (psiClass != null) {
            return psiClass.defaultType
        }

        val type = value.type()
        if (type is ClassType) {
            val superclass = type.superclass()
            if (superclass != null && CommonClassNames.JAVA_LANG_OBJECT != superclass.name()) {
                psiClass = AsmType.getType(superclass.signature()).getClassDescriptor(scope)
                if (psiClass != null) {
                    return psiClass.defaultType
                }
            }

            for (interfaceType in type.interfaces()) {
                psiClass = AsmType.getType(interfaceType.signature()).getClassDescriptor(scope)
                if (psiClass != null) {
                    return psiClass.defaultType
                }
            }
        }
        return null
    }
}

