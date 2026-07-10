// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.ClassType
import com.sun.jdi.Value
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.debugger.core.ClassNameProvider
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
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
        val psiClass = findPsiClassByType(asmType, project, scope) ?: return null
        val useSiteModule = getKaModule(psiClass, project)
        return runDumbAnalyze(useSiteModule, fallback = null) {
            getKaType(psiClass)?.createPointer()
        }
    }

    private fun buildIntersectionTypePointer(
        asmTypes: List<AsmType>,
        project: Project,
        scope: GlobalSearchScope,
    ): KaTypePointer<KaType>? {
        val psiClasses = asmTypes.mapNotNull { findPsiClassByType(it, project, scope) }
        if (psiClasses.isEmpty()) return null
        val useSiteModule = psiClasses.firstNotNullOfOrNull { getKaModule(it, project) }
        if (useSiteModule == null) return null
        return runDumbAnalyze(useSiteModule, fallback = null) {
            val kaConjuncts = psiClasses.mapNotNull {
                getKaType(it)
            }
            when (kaConjuncts.size) {
                0 -> null
                1 -> kaConjuncts.single().createPointer()
                else -> typeCreator.intersectionType { conjuncts(kaConjuncts) }.createPointer()
            }
        }
    }

    context(session: KaSession)
    private fun getKaType(psiClass: PsiElement): KaType? {
        with(session) {
            val classSymbol = psiClass.asKtClassOrObject()?.namedClassSymbol ?: (psiClass as? PsiClass)?.namedClassSymbol
            return classSymbol?.defaultType
        }
    }

    private fun getKaModule(psiElement: PsiElement, project: Project): KaModule =
        ReadAction.nonBlocking<KaModule> {
            psiElement.getKaModule(project, useSiteModule = null)
        }.executeSynchronously()

    /**
     * Resolve a local class by matching its compiled class name against
     * the class declarations in the evaluated expression's file.
     */
    private fun findLocalClassByName(className: String): KtClassOrObject? {
        val contextFile = (expression.containingFile as? KtCodeFragment)?.context?.containingFile as? KtFile ?: return null
        val classNameProvider = ClassNameProvider(ClassNameProvider.Configuration.DEFAULT)
        return contextFile.collectDescendantsOfType<KtClassOrObject> { it.name != null }
            .firstOrNull { className in classNameProvider.getCandidatesForElement(it) }
    }

    private fun findPsiClassByType(asmType: AsmType, project: Project, scope: GlobalSearchScope): PsiElement? =
        ReadAction.nonBlocking<PsiElement?> {
            val className = asmType.internalName.replace('/', '.')
            DebuggerUtils.findClass(className, project, scope, true)
                ?: findLocalClassByName(className)
                ?: return@nonBlocking null
        }.executeSynchronously()
}
