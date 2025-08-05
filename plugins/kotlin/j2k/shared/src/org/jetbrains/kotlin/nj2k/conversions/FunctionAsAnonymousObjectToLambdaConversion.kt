// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions


import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class FunctionAsAnonymousObjectToLambdaConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKNewExpression) return recurse(element)
        if (element.isAnonymousClass
            && element.classSymbol.isKtFunction()
        ) {
            val invokeFunction = element.classBody.declarations.singleOrNull()
                ?.safeAs<JKMethod>()
                ?.takeIf { it.name.value == "invoke" }
                ?: return recurse(element)
            return recurse(
                JKLambdaExpression(
                    JKBlockStatement(invokeFunction::block.detached()),
                    invokeFunction::parameters.detached()
                )
            )
        }
        return recurse(element)
    }

    private fun JKClassSymbol.isKtFunction() =
        fqName.matches("""kotlin\.Function(\d+)""".toRegex())
                || fqName.matches("""kotlin\.jvm\.functions\.Function(\d+)""".toRegex())
}