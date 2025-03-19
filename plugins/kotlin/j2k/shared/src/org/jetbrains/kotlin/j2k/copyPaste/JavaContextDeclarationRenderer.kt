// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.nj2k.KotlinNJ2KBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.blockExpressionsOrSingle
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

data class JavaContextDeclarationStubs(
    val localDeclarations: String,
    val memberDeclarations: String
)

/**
 * Converts member/local declarations of the target Kotlin element, into which the Java code is pasted,
 * to Java-friendly stubs ([JavaContextDeclarationStubs]).
 *
 * This is basically a crude Kotlin to Java converter for enhancing the context of plain text copy-paste J2K conversion.
 * In plain text conversion, we don't have an original Java `PsiFile` from which to draw context.
 * So, we do the next best thing: take an approximation of the context from the target Kotlin file.
 */
object JavaContextDeclarationRenderer {
    fun render(contextElement: KtElement): JavaContextDeclarationStubs {
        val task: () -> JavaContextDeclarationStubs = {
            runReadAction {
                analyze(contextElement) {
                    val localDeclarations = getLocalDeclarations(contextElement)
                    val memberDeclarations = getMemberDeclarations(contextElement)
                    val localDeclarationsJavaStubs = render(localDeclarations)
                    val memberDeclarationsJavaStubs = render(memberDeclarations)
                    JavaContextDeclarationStubs(localDeclarationsJavaStubs, memberDeclarationsJavaStubs)
                }
            }
        }

        return ProgressManager.getInstance().runProcessWithProgressSynchronously<JavaContextDeclarationStubs, Exception>(
            task, KotlinNJ2KBundle.message("copy.text.rendering.declaration.stubs"), /* canBeCanceled = */ true, contextElement.project
        )
    }

    private fun KaSession.getLocalDeclarations(contextElement: KtElement): List<KaDeclarationSymbol> {
        val containerFunction = contextElement.getParentOfType<KtFunction>(strict = false) ?: return emptyList()
        val localDeclarations = containerFunction.bodyExpression
            ?.blockExpressionsOrSingle()
            ?.filterIsInstance<KtDeclaration>()
            .orEmpty()
        return localDeclarations.map { it.symbol }.toList()
    }

    private fun KaSession.getMemberDeclarations(contextElement: KtElement): List<KaDeclarationSymbol> {
        val allMembers = contextElement.parentsWithSelf.flatMap { declaration ->
            when (declaration) {
                is KtClass -> declaration.classSymbol?.declaredMemberScope?.declarations
                is KtDeclarationContainer -> declaration.declarations.map { it.symbol }.asSequence()
                else -> null
            } ?: emptySequence()
        }
        val filteredMembers = allMembers.filter { member ->
            val name = member.name ?: return@filter false
            !name.isSpecial && name.asString() != "dummy"
        }

        return filteredMembers.toList()
    }

    private fun KaSession.render(declarations: List<KaDeclarationSymbol>): String {
        val renderer = Renderer()
        return buildString {
            for (declaration in declarations) {
                val renderedDeclaration = renderer.render(declaration, session = this@render)
                append(renderedDeclaration)
                appendLine()
            }
        }
    }
}

private class Renderer {
    private val builder = StringBuilder()

    fun render(declaration: KaDeclarationSymbol, session: KaSession): String {
        with(session) { renderDeclaration(declaration) }
        val result = builder.toString()
        builder.clear()
        return result
    }

    private fun KaSession.renderDeclaration(declaration: KaDeclarationSymbol) {
        when (declaration) {
            is KaVariableSymbol -> {
                renderType(declaration.returnType)
                append(" ")
                append(declaration.name.asString())
                append(" = null;")
            }

            is KaFunctionSymbol -> {
                val name = declaration.name ?: return
                renderType(declaration.returnType)
                append(" ")
                append(name.asString())
                append("(")
                for ((i, parameter) in declaration.valueParameters.withIndex()) {
                    renderType(parameter.returnType)
                    append(" ")
                    append(parameter.name.asString())
                    if (i != declaration.valueParameters.lastIndex) {
                        append(", ")
                    }
                }
                append(") {}")
            }

            else -> {} // Do nothing
        }
    }

    private fun KaSession.renderType(type: KaType?) {
        val fqName = type?.symbol?.getFqNameIfPackageOrNonLocal()
        if (fqName != null) {
            renderFqName(fqName.toUnsafe())
        } else {
            append("Object")
        }

        if (type is KaClassType && type.typeArguments.isNotEmpty()) {
            append("<")
            for (typeArgument in type.typeArguments) {
                if (typeArgument is KaStarTypeProjection) {
                    append("?")
                } else {
                    renderType(typeArgument.type)
                }
            }
            append(">")
        }
    }

    private fun renderFqName(fqName: FqNameUnsafe) {
        val stringFqName = when (fqName) {
            StandardNames.FqNames.unit -> "void"
            else -> JavaToKotlinClassMap.mapKotlinToJava(fqName)?.asFqNameString() ?: fqName.asString()
        }
        append(stringFqName)
    }

    private fun append(s: String) {
        builder.append(s)
    }
}
