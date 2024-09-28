// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import org.jetbrains.kotlin.codegen.sanitizeNameIfNeeded
import org.jetbrains.kotlin.idea.base.platforms.StableModuleNameProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.psi.*

object CallableNameCalculator {
    sealed class CallableName {
        class Exact(val name: String) : CallableName() {
            override fun matches(name: String) = name == this.name
        }

        class Mangled(val prefix: String, private val postfix: String?) : CallableName() {
            override fun matches(name: String) = name.startsWith(prefix) && (postfix == null || name.endsWith(postfix))
        }

        abstract fun matches(name: String): Boolean
    }

    fun getFunctionName(function: KtNamedFunction): CallableName? {
        val jvmName = KotlinPsiHeuristics.findJvmName(function)
        if (jvmName != null) {
            return CallableName.Exact(jvmName)
        }

        val name = function.name ?: return null
        val prefix = sanitizeNameIfNeeded(name, function.languageVersionSettings)

        if (function.hasModifier(KtTokens.INTERNAL_KEYWORD) && !function.isTopLevel && !isPublishedApi(function)) {
            return CallableName.Mangled(prefix, getInternalPostfix(function))
        } else {
            return CallableName.Mangled(prefix, null)
        }
    }

    fun getAccessorName(property: KtProperty, isSetter: Boolean): CallableName? {
        val jvmName = if (isSetter) KotlinPsiHeuristics.findJvmSetterName(property) else KotlinPsiHeuristics.findJvmGetterName(property)
        if (jvmName != null) {
            return CallableName.Exact(jvmName)
        }

        val propertyName = property.name ?: return null
        val prefix = if (isSetter) JvmAbi.setterName(propertyName) else JvmAbi.getterName(propertyName)

        if (property.hasModifier(KtTokens.INTERNAL_KEYWORD) && !property.isTopLevel && !isPublishedApi(property)) {
            return CallableName.Mangled(prefix, getInternalPostfix(property))
        } else {
            return CallableName.Mangled(prefix, null)
        }
    }

    fun getParameterName(parameter: KtParameter): String? {
        val name = parameter.name ?: return null

        // See org.jetbrains.kotlin.codegen.FunctionCodegen.computeParameterName()
        if (name == "_") {
            val index = when (val parent = parameter.parent) {
                is KtParameterList -> parent.parameters.indexOf(parameter)
                else -> 0
            }
            return "\$noName_$index"
        }

        return name
    }

    private fun getInternalPostfix(declaration: KtDeclaration): String? {
        val module = declaration.module ?: return null
        val moduleName = StableModuleNameProvider.getInstance(declaration.project).getStableModuleName(module)
        return "$" + NameUtils.sanitizeAsJavaIdentifier(moduleName)
    }

    private fun isPublishedApi(declaration: KtAnnotated): Boolean {
        return KotlinPsiHeuristics.hasPublishedApiAnnotation(declaration)
    }
}
