// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.compile

import org.jetbrains.kotlin.diagnostics.rendering.Renderers
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.isSingleUnderscore

class KtScratchSourceFileProcessor {
    companion object {
        const val GENERATED_OUTPUT_PREFIX = "##scratch##generated##"
        const val LINES_INFO_MARKER = "end##"
        const val END_OUTPUT_MARKER = "end##!@#%^&*"

        const val OBJECT_NAME = "ScratchFileRunnerGenerated"
        const val INSTANCE_NAME = "instanceScratchFileRunner"
        const val PACKAGE_NAME = "org.jetbrains.kotlin.idea.scratch.generated"
        const val GET_RES_FUN_NAME_PREFIX = "generated_get_instance_res"
    }

    fun process(expressions: List<ScratchExpression>): Result {
        val sourceProcessor = KtSourceProcessor()
        expressions.forEach {
            sourceProcessor.process(it)
        }

        val codeResult =
            """
                package $PACKAGE_NAME

                ${sourceProcessor.imports.joinToString("\n") { it.text }}

                object $OBJECT_NAME {
                    class $OBJECT_NAME {
                        ${sourceProcessor.classBuilder}
                    }

                    @JvmStatic fun main(args: Array<String>) {
                        val $INSTANCE_NAME = $OBJECT_NAME()
                        ${sourceProcessor.objectBuilder}
                        println("$END_OUTPUT_MARKER")
                    }
                }
            """
        return Result.OK("$PACKAGE_NAME.$OBJECT_NAME", codeResult)
    }

    class KtSourceProcessor {
        val classBuilder = StringBuilder()
        val objectBuilder = StringBuilder()
        val imports = arrayListOf<KtImportDirective>()

        private var resCount = 0

        fun process(expression: ScratchExpression) {
            when (val psiElement = expression.element) {
                is KtDestructuringDeclaration -> processDestructuringDeclaration(expression, psiElement)
                is KtVariableDeclaration -> processDeclaration(expression, psiElement)
                is KtFunction -> processDeclaration(expression, psiElement)
                is KtClassOrObject -> processDeclaration(expression, psiElement)
                is KtImportDirective -> imports.add(psiElement)
                is KtExpression -> processExpression(expression, psiElement)
            }
        }

        private fun processDeclaration(e: ScratchExpression, c: KtDeclaration) {
            classBuilder.append(c.text).newLine()

            val descriptor = c.resolveToDescriptorIfAny() ?: return

            val context = RenderingContext.of(descriptor)
            objectBuilder.println(Renderers.COMPACT.render(descriptor, context))
            objectBuilder.appendLineInfo(e)
        }

        private fun processDestructuringDeclaration(e: ScratchExpression, c: KtDestructuringDeclaration) {
            val entries = c.entries.mapNotNull { if (it.isSingleUnderscore) null else it.resolveToDescriptorIfAny() }
            entries.forEach {
                val context = RenderingContext.of(it)
                val rendered = Renderers.COMPACT.render(it, context)
                classBuilder.append(rendered).newLine()
                objectBuilder.println(rendered)
            }
            objectBuilder.appendLineInfo(e)
            classBuilder.append("init {").newLine()
            classBuilder.append(c.text).newLine()
            entries.forEach {
                classBuilder.append("this.${it.name} = ${it.name}").newLine()
            }
            classBuilder.append("}").newLine()
        }

        private fun processExpression(e: ScratchExpression, expr: KtExpression) {
            val resName = "$GET_RES_FUN_NAME_PREFIX$resCount"

            classBuilder.append("fun $resName() = run { ${expr.text} }").newLine()

            objectBuilder.printlnObj("$INSTANCE_NAME.$resName()")
            objectBuilder.appendLineInfo(e)

            resCount += 1
        }

        private fun StringBuilder.appendLineInfo(e: ScratchExpression) {
            println("$LINES_INFO_MARKER${e.lineStart}|${e.lineEnd}")
        }

        private fun StringBuilder.println(str: String) = append("println(\"$GENERATED_OUTPUT_PREFIX$str\")").newLine()
        private fun StringBuilder.printlnObj(str: String) = append("println(\"$GENERATED_OUTPUT_PREFIX\${$str}\")").newLine()
        private fun StringBuilder.newLine() = append("\n")
    }

    sealed class Result {
        class Error(val message: String) : Result()
        class OK(val mainClassName: String, val code: String) : Result()
    }
}