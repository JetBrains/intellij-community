// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.ClassType
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
import org.jetbrains.org.objectweb.asm.Type as AsmType

@ApiStatus.Internal
@OptIn(KaExperimentalApi::class)
abstract class KotlinK2RuntimeTypeEvaluator(
    editor: Editor?,
    private val expression: KtExpression,
    context: DebuggerContextImpl,
    indicator: ProgressIndicator
) : KotlinRuntimeTypeEvaluatorBase<KaTypePointer<KaType>?>(editor, expression, context, indicator) {

    override fun getCastableRuntimeType(scope: GlobalSearchScope, value: Value): KaTypePointer<KaType>? {
        val project = scope.project ?: return null
        val asmType = value.asValue().asmType
        findTypeByAsmType(asmType, project, scope)?.let { return it }

        // Fallback for anonymous classes: combine the superclass and all interfaces into a single intersection type.
        val type = value.type() as? ClassType ?: return null
        val superAsmTypes = buildList {
            type.superclass()
                ?.takeIf { it.name() != CommonClassNames.JAVA_LANG_OBJECT }
                ?.let { add(AsmType.getType(it.signature())) }
            type.interfaces().forEach { add(AsmType.getType(it.signature())) }
        }
        if (superAsmTypes.isEmpty()) return null
        return buildIntersectionTypePointer(superAsmTypes, project, scope)
    }

    private fun findTypeByAsmType(asmType: AsmType, project: Project, scope: GlobalSearchScope): KaTypePointer<KaType>? {
        val jvmName = JvmClassName.byInternalName(asmType.internalName).fqNameForClassNameWithoutDollars
        val psiClass = runReadAction {
            DebuggerUtils.findClass(jvmName.asString(), project, scope, true)
        } ?: return null
        return runDumbAnalyze(expression, fallback = null) {
            val runtimeType = (psiClass.asKtClassOrObject()?.namedClassSymbol ?: psiClass.namedClassSymbol)?.defaultType
            runtimeType?.createPointer()
        }
    }

    private fun buildIntersectionTypePointer(
        asmTypes: List<AsmType>,
        project: Project,
        scope: GlobalSearchScope,
    ): KaTypePointer<KaType>? {
        val psiClasses = runReadAction {
            asmTypes.mapNotNull { asm ->
                val jvmName = JvmClassName.byInternalName(asm.internalName).fqNameForClassNameWithoutDollars
                DebuggerUtils.findClass(jvmName.asString(), project, scope, true)
            }
        }
        if (psiClasses.isEmpty()) return null
        return runDumbAnalyze(expression, fallback = null) {
            val kaConjuncts = psiClasses.mapNotNull {
                (it.asKtClassOrObject()?.namedClassSymbol ?: it.namedClassSymbol)?.defaultType
            }
            when (kaConjuncts.size) {
                0 -> null
                1 -> kaConjuncts.single().createPointer()
                else -> typeCreator.intersectionType { conjuncts(kaConjuncts) }.createPointer()
            }
        }
    }
}
