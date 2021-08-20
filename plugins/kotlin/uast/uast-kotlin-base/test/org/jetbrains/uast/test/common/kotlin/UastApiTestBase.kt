/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt uFile.
 */
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiClass
import junit.framework.TestCase
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.test.env.findElementByText

interface UastApiTestBase : UastPluginSelection {
    fun checkCallbackForSAM(uFilePath: String, uFile: UFile) {
        TestCase.assertNull(uFile.findElementByText<ULambdaExpression>("{ /* Not SAM */ }").functionalInterfaceType)
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{/* Variable */}").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{/* Assignment */}").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{/* Type Cast */}").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{/* Argument */}").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{/* Return */}").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{ /* SAM */ }").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{ println(\"hello1\") }").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{ println(\"hello2\") }").functionalInterfaceType?.canonicalText
        )
        val call = uFile.findElementByText<UCallExpression>("Runnable { println(\"hello2\") }")
        TestCase.assertEquals(
            "java.lang.Runnable",
            (call.classReference?.resolve() as? PsiClass)?.qualifiedName
        )
        TestCase.assertEquals(
            "java.util.function.Supplier<T>",
            uFile.findElementByText<ULambdaExpression>("{ \"Supplier\" }").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.util.concurrent.Callable<V>",
            uFile.findElementByText<ULambdaExpression>("{ \"Callable\" }").functionalInterfaceType?.canonicalText
        )
    }
}
