// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.Value
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

@ApiStatus.Internal
@OptIn(KaExperimentalApi::class, KaContextParameterApi::class)
abstract class KotlinK2RuntimeTypeEvaluator(
    editor: Editor?,
    private val expression: KtExpression,
    context: DebuggerContextImpl,
    indicator: ProgressIndicator
) : KotlinRuntimeTypeEvaluatorBase<KaTypePointer<KaType>?>(editor, expression, context, indicator) {

    override fun getCastableRuntimeType(scope: GlobalSearchScope, value: Value): KaTypePointer<KaType>? {
        val project = scope.project ?: return null
        val asmType = value.asValue().asmType
        val jvmName = JvmClassName.byInternalName(asmType.internalName).fqNameForClassNameWithoutDollars
        val psiClass = runReadAction {
            DebuggerUtils.findClass(jvmName.asString(), project, scope, true)
        } ?: return null
        return runDumbAnalyze(expression, fallback = null) {
            val runtimeType = (psiClass.asKtClassOrObject()?.namedClassSymbol ?: psiClass.namedClassSymbol)?.defaultType
            runtimeType?.createPointer()
        }
    }
}
