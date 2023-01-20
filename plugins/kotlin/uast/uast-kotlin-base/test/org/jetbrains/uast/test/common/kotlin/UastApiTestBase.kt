/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt uFile.
 */
package org.jetbrains.uast.test.common.kotlin

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.*
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.assertedCast
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.test.env.findElementByText
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert
import java.lang.IllegalStateException
import kotlin.test.fail as kfail

interface UastApiTestBase : UastPluginSelection {
    fun checkCallbackForAnnotationParameters(uFilePath: String, uFile: UFile) {
        val annotation = uFile.findElementByText<UAnnotation>("@IntRange(from = 10, to = 0)")
        TestCase.assertEquals(10L, annotation.findAttributeValue("from")?.evaluate())
        val toAttribute = annotation.findAttributeValue("to")!!
        TestCase.assertEquals(0L, toAttribute.evaluate())
        KtUsefulTestCase.assertInstanceOf(annotation.psi.toUElement(), UAnnotation::class.java)
        KtUsefulTestCase.assertInstanceOf(
            annotation.psi.cast<KtAnnotationEntry>().toLightAnnotation().toUElement(),
            UAnnotation::class.java
        )
        KtUsefulTestCase.assertInstanceOf(toAttribute.uastParent, UNamedExpression::class.java)
        KtUsefulTestCase.assertInstanceOf(toAttribute.psi.toUElement()?.uastParent, UNamedExpression::class.java)

        checkFindAttributeDefaultValue(uFile)
        checkLiteralArraysTypes(uFile)
    }

    private fun checkFindAttributeDefaultValue(uFile: UFile) {
        val witDefaultValue = uFile.findElementByText<UAnnotation>("@WithDefaultValue")
        TestCase.assertEquals(42, witDefaultValue.findAttributeValue("value")!!.evaluate())
        TestCase.assertEquals(42, witDefaultValue.findAttributeValue(null)!!.evaluate())
    }

    private fun checkLiteralArraysTypes(uFile: UFile) {
        uFile.findElementByTextFromPsi<UCallExpression>("intArrayOf(1, 2, 3)").let { field ->
            Assert.assertEquals("int[]", field.returnType?.canonicalText)
        }
        uFile.findElementByTextFromPsi<UCallExpression>("[1, 2, 3]").let { field ->
            Assert.assertEquals("int[]", field.returnType?.canonicalText)
            Assert.assertEquals("int", field.typeArguments.single().canonicalText)
        }
        uFile.findElementByTextFromPsi<UCallExpression>("[\"a\", \"b\", \"c\"]").let { field ->
            Assert.assertEquals("java.lang.String[]", field.returnType?.canonicalText)
            Assert.assertEquals("java.lang.String", field.typeArguments.single().canonicalText)
        }
    }

    fun checkCallbackForStringTemplateInClass(uFilePath: String, uFile: UFile) {
        val literalExpression = uFile.findElementByText<ULiteralExpression>("lorem")
        val psi = literalExpression.psi!!
        TestCase.assertTrue(psi is KtLiteralStringTemplateEntry)
        val literalExpressionAgain = psi.toUElement()
        TestCase.assertTrue(literalExpressionAgain is ULiteralExpression)
    }

    fun checkCallbackForStringTemplateWithVar(uFilePath: String, uFile: UFile) {
        val index = uFile.psi.text.indexOf("foo")
        val stringTemplate = uFile.psi.findElementAt(index)!!.getParentOfType<KtStringTemplateExpression>(false)
        val uLiteral = stringTemplate.toUElementOfType<ULiteralExpression>()
        TestCase.assertNull(uLiteral)
    }

    fun checkCallbackForNameContainingFile(uFilePath: String, uFile: UFile) {
        val foo = uFile.findElementByText<UClass>("class Foo")
        TestCase.assertEquals(uFile.psi, foo.nameIdentifier!!.containingFile)

        val bar = uFile.findElementByText<UMethod>("fun bar() {}")
        TestCase.assertEquals(uFile.psi, bar.nameIdentifier!!.containingFile)

        val xyzzy = uFile.findElementByText<UVariable>("val xyzzy: Int = 0")
        TestCase.assertEquals(uFile.psi, xyzzy.nameIdentifier!!.containingFile)
    }

    fun checkCallbackForDefaultImpls(uFilePath: String, uFile: UFile) {
        val bar = uFile.findElementByText<UMethod>("fun bar() = \"Hello!\"")
        TestCase.assertFalse(bar.containingFile.text!!, bar.psi.modifierList.hasExplicitModifier(PsiModifier.DEFAULT))
        TestCase.assertTrue(bar.containingFile.text!!, bar.psi.modifierList.hasModifierProperty(PsiModifier.DEFAULT))
    }

    fun checkCallbackForParameterPropertyWithAnnotation(uFilePath: String, uFile: UFile) {
        val test1 = uFile.classes.find { it.name == "Test1" }!!

        val constructor1 = test1.methods.find { it.name == "Test1" }!!
        TestCase.assertTrue(constructor1.uastParameters.first().annotations.any { it.qualifiedName == "MyAnnotation" })

        val getter1 = test1.methods.find { it.name == "getBar" }!!
        TestCase.assertFalse(getter1.annotations.any { it.qualifiedName == "MyAnnotation" })

        val setter1 = test1.methods.find { it.name == "setBar" }!!
        TestCase.assertFalse(setter1.annotations.any { it.qualifiedName == "MyAnnotation" })
        TestCase.assertFalse(setter1.uastParameters.first().annotations.any { it.qualifiedName == "MyAnnotation" })

        val test2 = uFile.classes.find { it.name == "Test2" }!!
        val constructor2 = test2.methods.find { it.name == "Test2" }!!
        TestCase.assertFalse(constructor2.uastParameters.first().annotations.any { it.qualifiedName?.startsWith("MyAnnotation") ?: false })

        val getter2 = test2.methods.find { it.name == "getBar" }!!
        getter2.annotations.single { it.qualifiedName == "MyAnnotation" }

        val setter2 = test2.methods.find { it.name == "setBar" }!!
        setter2.annotations.single { it.qualifiedName == "MyAnnotation2" }
        setter2.uastParameters.first().annotations.single { it.qualifiedName == "MyAnnotation3" }

        test2.fields.find { it.name == "bar" }!!.annotations.single { it.qualifiedName == "MyAnnotation5" }
    }

    fun checkCallbackForTypeInAnnotation(uFilePath: String, uFile: UFile) {
        val index = uFile.psi.text.indexOf("Test")
        val element = uFile.psi.findElementAt(index)!!.getParentOfType<KtUserType>(false)!!
        TestCase.assertNotNull(element.getUastParentOfType(UAnnotation::class.java))
    }

    fun checkCallbackForElvisType(uFilePath: String, uFile: UFile) {
        val elvisExpression = uFile.findElementByText<UExpression>("text ?: return")
        TestCase.assertEquals("String", elvisExpression.getExpressionType()!!.presentableText)
    }

    fun checkCallbackForIfStatement(uFilePath: String, uFile: UFile) {
        val psiFile = uFile.psi
        val element = psiFile.findElementAt(psiFile.text.indexOf("\"abc\""))!!
        val binaryExpression = element.getParentOfType<KtBinaryExpression>(false)!!
        val uBinaryExpression = uFile.languagePlugin.convertElementWithParent(binaryExpression, null)!!
        UsefulTestCase.assertInstanceOf(uBinaryExpression.uastParent, UIfExpression::class.java)
    }

    fun checkCallbackForWhenStringLiteral(uFilePath: String, uFile: UFile) {
        uFile.findElementByTextFromPsi<UInjectionHost>("\"abc\"").let { literalExpression ->
            val psi = literalExpression.psi!!
            Assert.assertTrue(psi is KtStringTemplateExpression)
            UsefulTestCase.assertInstanceOf(literalExpression.uastParent, USwitchClauseExpressionWithBody::class.java)
        }

        uFile.findElementByTextFromPsi<UInjectionHost>("\"def\"").let { literalExpression ->
            val psi = literalExpression.psi!!
            Assert.assertTrue(psi is KtStringTemplateExpression)
            UsefulTestCase.assertInstanceOf(literalExpression.uastParent, USwitchClauseExpressionWithBody::class.java)
        }

        uFile.findElementByTextFromPsi<UInjectionHost>("\"def1\"").let { literalExpression ->
            val psi = literalExpression.psi!!
            Assert.assertTrue(psi is KtStringTemplateExpression)
            UsefulTestCase.assertInstanceOf(literalExpression.uastParent, UBlockExpression::class.java)
        }
    }

    fun checkCallbackForWhenAndDestructing(uFilePath: String, uFile: UFile) {
        uFile.findElementByTextFromPsi<UExpression>("val (bindingContext, statementFilter) = arr").let { e ->
            val uBlockExpression = e.getParentOfType<UBlockExpression>()
            Assert.assertNotNull(uBlockExpression)
            val uMethod = uBlockExpression!!.getParentOfType<UMethod>()
            Assert.assertNotNull(uMethod)
        }
    }

    fun checkCallbackForBrokenMethod(uFilePath: String, uFile: UFile) {
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                node.returnType
                return false
            }
        })
    }

    fun checkCallbackForEnumValuesConstructors(uFilePath: String, uFile: UFile) {
        val enumEntry = uFile.findElementByTextFromPsi<UElement>("(\"system\")")
        enumEntry.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodIdentifier = node.methodIdentifier
                TestCase.assertEquals("SYSTEM", methodIdentifier?.name)
                return super.visitCallExpression(node)
            }
        })
    }

    fun checkCallbackForEnumValueMembers(uFilePath: String, uFile: UFile) {
        val enumEntry = uFile.findElementByTextFromPsi<UElement>("(\"foo\")")
        enumEntry.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodIdentifier = node.methodIdentifier
                TestCase.assertEquals("SHEET", methodIdentifier?.name)
                return super.visitCallExpression(node)
            }
        })
    }

    private fun <T, R> Iterable<T>.assertedFind(value: R, transform: (T) -> R): T =
        find { transform(it) == value }
            ?: throw AssertionError("'$value' not found, only ${this.joinToString { transform(it).toString() }}")

    fun checkCallbackForSimpleAnnotated(uFilePath: String, uFile: UFile) {
        uFile.findElementByTextFromPsi<UField>("@kotlin.SinceKotlin(\"1.0\")\n    val property: String = \"Mary\"").let { field ->
            val annotation = field.uAnnotations.assertedFind("kotlin.SinceKotlin") { it.qualifiedName }
            Assert.assertEquals("1.0", annotation.findDeclaredAttributeValue("version")?.evaluateString())
            Assert.assertEquals("SinceKotlin", annotation.cast<UAnchorOwner>().uastAnchor?.sourcePsi?.text)
        }
    }

    private fun UFile.checkUastSuperTypes(refText: String, superTypes: List<String>) {
        findElementByTextFromPsi<UClass>(refText, false).let {
            TestCase.assertEquals("base classes", superTypes, it.uastSuperTypes.map { it.getQualifiedName() })
        }
    }

    fun checkCallbackForSuperCalls(uFilePath: String, uFile: UFile) {
        uFile.checkUastSuperTypes("class B", listOf("A"))
        uFile.checkUastSuperTypes("object O", listOf("A"))
        uFile.checkUastSuperTypes("InnerClass", listOf("A"))
        uFile.checkUastSuperTypes("object : A(\"textForAnon\")", listOf("A"))
    }

    fun checkCallbackForAnonymous(uFilePath: String, uFile: UFile) {
        uFile.checkUastSuperTypes("object : Runnable { override fun run() {} }", listOf("java.lang.Runnable"))
        uFile.checkUastSuperTypes(
            "object : Runnable, Closeable { override fun close() {} override fun run() {} }",
            listOf("java.lang.Runnable", "java.io.Closeable")
        )
        uFile.checkUastSuperTypes(
            "object : InputStream(), Runnable { override fun read(): Int = 0; override fun run() {} }",
            listOf("java.io.InputStream", "java.lang.Runnable")
        )
    }

    fun checkCallbackForTypeAliases(uFilePath: String, uFile: UFile) {
        val g = (uFile.psi as KtFile).declarations.single { it.name == "G" } as KtTypeAlias
        val originalType = g.getTypeReference()!!.typeElement as KtFunctionType
        val originalTypeParameters = originalType.parameterList.toUElement() as UDeclarationsExpression
        Assert.assertTrue((originalTypeParameters.declarations.single() as UParameter).type.isValid)
    }

    fun checkCallbackForAnnotationComplex(uFilePath: String, uFile: UFile) {
        checkNestedAnnotation(uFile)
        checkNestedAnnotationParameters(uFile)
    }

    private fun checkNestedAnnotation(uFile: UFile) {
        uFile.findElementByTextFromPsi<UElement>("@AnnotationArray(value = Annotation(\"sv1\", \"sv2\"))", strict = true)
            .findElementByTextFromPsi<UElement>("Annotation(\"sv1\", \"sv2\")", strict = true)
            .sourcePsiElement
            .let { referenceExpression ->
                val convertedUAnnotation = referenceExpression
                    .cast<KtReferenceExpression>()
                    .toUElementOfType<UAnnotation>()
                    ?: throw AssertionError("haven't got annotation from $referenceExpression(${referenceExpression?.javaClass})")

                // NB: descriptor is FE 1.0 thing, not FIR.
                if (!isFirUastPlugin) {
                    checkDescriptorsLeak(convertedUAnnotation)
                }
                TestCase.assertEquals("Annotation", convertedUAnnotation.qualifiedName)
                val lightAnnotation = convertedUAnnotation.getAsJavaPsiElement(PsiAnnotation::class.java)
                    ?: throw AssertionError("can't get lightAnnotation from $convertedUAnnotation")
                TestCase.assertEquals("Annotation", lightAnnotation.qualifiedName)
                TestCase.assertEquals("Annotation", (convertedUAnnotation as UAnchorOwner).uastAnchor?.sourcePsi?.text)
            }
    }

    private fun checkNestedAnnotationParameters(uFile: UFile) {
        fun UFile.annotationAndParam(refText: String, check: (PsiAnnotation, String?) -> Unit) {
            findElementByTextFromPsi<UElement>(refText)
                .let { expression ->
                    val (annotation: PsiAnnotation, paramname: String?) =
                        getContainingAnnotationEntry(expression) ?: kfail("annotation not found for '$refText' ($expression)")
                    check(annotation, paramname)
                }
        }

        uFile.annotationAndParam("sv1") { annotation, paramname ->
            TestCase.assertEquals("Annotation", annotation.qualifiedName)
            TestCase.assertEquals(null, paramname)
        }
        uFile.annotationAndParam("sv2") { annotation, paramname ->
            TestCase.assertEquals("Annotation", annotation.qualifiedName)
            TestCase.assertEquals(null, paramname)
        }
        uFile.annotationAndParam("sar1") { annotation, paramname ->
            TestCase.assertEquals("Annotation", annotation.qualifiedName)
            TestCase.assertEquals("strings", paramname)
        }
        uFile.annotationAndParam("sar2") { annotation, paramname ->
            TestCase.assertEquals("Annotation", annotation.qualifiedName)
            TestCase.assertEquals("strings", paramname)
        }
        uFile.annotationAndParam("[sar]1") { annotation, paramname ->
            TestCase.assertEquals("Annotation", annotation.qualifiedName)
            TestCase.assertEquals("strings", paramname)
            val attributeValue = annotation.findAttributeValue(paramname)!!
            val uastAnnotationParamValue = annotation.toUElementOfType<UAnnotation>()!!.findAttributeValue(paramname)!!

            fun assertEqualUast(expected: UElement, psiElement: PsiElement, converter: (PsiElement) -> UElement? = { it.toUElement() }) {
                TestCase.assertEquals("${psiElement.javaClass} should have been converted to ${expected}", expected, converter(psiElement))
            }
            assertEqualUast(uastAnnotationParamValue, attributeValue)
            assertEqualUast(
                uastAnnotationParamValue.cast<UCallExpression>().valueArguments[0],
                attributeValue.cast<PsiArrayInitializerMemberValue>().initializers[0]
            )
            assertEqualUast(
                wrapULiteral(uastAnnotationParamValue.cast<UCallExpression>().valueArguments[0]),
                attributeValue.cast<PsiArrayInitializerMemberValue>().initializers[0]
            ) { it.toUElementOfType<UInjectionHost>() }
        }
        uFile.annotationAndParam("[sar]2") { annotation, paramname ->
            TestCase.assertEquals("Annotation", annotation.qualifiedName)
            TestCase.assertEquals("strings", paramname)
        }
    }

    fun checkCallbackForParametersDisorder(uFilePath: String, uFile: UFile) {
        fun assertArguments(argumentsInPositionalOrder: List<String?>?, refText: String) =
            uFile.findElementByTextFromPsi<UCallExpression>(refText).let { call ->
                Assert.assertEquals(
                    argumentsInPositionalOrder,
                    call.resolve()?.let { psiMethod ->
                        (0 until psiMethod.parameterList.parametersCount).map {
                            call.getArgumentForParameter(it)?.asRenderString()
                        }
                    }
                )
            }

        assertArguments(listOf("2", "2.2"), "global(b = 2.2F, a = 2)")
        assertArguments(listOf(null, "\"bbb\""), "withDefault(d = \"bbb\")")
        assertArguments(listOf("1.3", "3.4"), "atan2(1.3, 3.4)")
        assertArguments(null, "unresolvedMethod(\"param1\", \"param2\")")
        assertArguments(listOf("\"%i %i %i\"", "varargs 1 : 2 : 3"), "format(\"%i %i %i\", 1, 2, 3)")
        assertArguments(listOf("\"%i %i %i\"", "varargs arrayOf(1, 2, 3)"), "format(\"%i %i %i\", arrayOf(1, 2, 3))")
        assertArguments(
            listOf("\"%i %i %i\"", "varargs arrayOf(1, 2, 3) : arrayOf(4, 5, 6)"),
            "format(\"%i %i %i\", arrayOf(1, 2, 3), arrayOf(4, 5, 6))"
        )
        assertArguments(listOf("\"%i %i %i\"", "\"\".chunked(2).toTypedArray()"), "format(\"%i %i %i\", *\"\".chunked(2).toTypedArray())")
        assertArguments(listOf("\"def\"", "8", "7.0"), "with2Receivers(8, 7.0F)")
    }

    fun checkCallbackForLambdas(uFilePath: String, uFile: UFile) {
        checkUtilsStreamLambda(uFile)
        checkLambdaParamCall(uFile)
        checkLocalLambdaCall(uFile)
    }

    private fun checkUtilsStreamLambda(uFile: UFile) {
        val lambda = uFile.findElementByTextFromPsi<ULambdaExpression>("{ it.isEmpty() }")
        TestCase.assertEquals(
            "java.util.function.Predicate<? super java.lang.String>",
            lambda.functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "kotlin.jvm.functions.Function1<? super java.lang.String,? extends java.lang.Boolean>",
            lambda.getExpressionType()?.canonicalText
        )
        val uCallExpression = lambda.uastParent.assertedCast<UCallExpression> { "UCallExpression expected" }
        TestCase.assertTrue(uCallExpression.valueArguments.contains(lambda))
    }

    private fun checkLambdaParamCall(uFile: UFile) {
        val lambdaCall = uFile.findElementByTextFromPsi<UCallExpression>("selectItemFunction()")
        TestCase.assertEquals(
            "UIdentifier (Identifier (selectItemFunction))",
            lambdaCall.methodIdentifier?.asLogString()
        )
        TestCase.assertEquals(
            "selectItemFunction",
            lambdaCall.methodIdentifier?.name
        )
        TestCase.assertEquals(
            "invoke",
            lambdaCall.methodName
        )
        val receiver = lambdaCall.receiver ?: kfail("receiver expected")
        TestCase.assertEquals("UReferenceExpression", receiver.asLogString())
        val uParameter = (receiver as UReferenceExpression).resolve().toUElement() ?: kfail("UElement expected")
        TestCase.assertEquals("UParameter (name = selectItemFunction)", uParameter.asLogString())
    }

    private fun checkLocalLambdaCall(uFile: UFile) {
        val lambdaCall = uFile.findElementByTextFromPsi<UCallExpression>("baz()")
        TestCase.assertEquals(
            "UIdentifier (Identifier (baz))",
            lambdaCall.methodIdentifier?.asLogString()
        )
        TestCase.assertEquals(
            "baz",
            lambdaCall.methodIdentifier?.name
        )
        TestCase.assertEquals(
            "invoke",
            lambdaCall.methodName
        )
        val receiver = lambdaCall.receiver ?: kfail("receiver expected")
        TestCase.assertEquals("UReferenceExpression", receiver.asLogString())
        val uParameter = (receiver as UReferenceExpression).resolve().toUElement() ?: kfail("UElement expected")
        TestCase.assertEquals("ULocalVariable (name = baz)", uParameter.asLogString())
    }

    fun checkCallbackForLocalDeclarations(uFilePath: String, uFile: UFile) {
        checkLocalDeclarationCall(uFile)
        checkLocalConstructorCall(uFile)
    }

    private fun checkLocalDeclarationCall(uFile: UFile) {
        val localFunction =
            uFile.findElementByTextFromPsi<UElement>("bar() == Local()", strict = true).findElementByText<UCallExpression>("bar()")
        TestCase.assertEquals(
            "UIdentifier (Identifier (bar))",
            localFunction.methodIdentifier?.asLogString()
        )
        TestCase.assertEquals(
            "bar",
            localFunction.methodIdentifier?.name
        )
        TestCase.assertEquals(
            "bar",
            localFunction.methodName
        )
        val localFunctionResolved = localFunction.resolve()
        TestCase.assertNotNull(localFunctionResolved)
        val receiver = localFunction.receiver ?: kfail("receiver expected")
        TestCase.assertEquals("UReferenceExpression", receiver.asLogString())
        val uVariable = (receiver as UReferenceExpression).resolve().toUElement() ?: kfail("UElement expected")
        TestCase.assertEquals("ULocalVariable (name = bar)", uVariable.asLogString())
        TestCase.assertEquals((uVariable as ULocalVariable).uastInitializer, localFunctionResolved.toUElement())
    }

    private fun checkLocalConstructorCall(uFile: UFile) {
        val localFunction =
            uFile.findElementByTextFromPsi<UElement>("bar() == Local()", strict = true).findElementByText<UCallExpression>("Local()")
        TestCase.assertEquals(
            "UIdentifier (Identifier (Local))",
            localFunction.methodIdentifier?.asLogString()
        )
        TestCase.assertEquals(
            "Local",
            localFunction.methodIdentifier?.name
        )
        TestCase.assertEquals(
            "<init>",
            localFunction.methodName
        )
        val localFunctionResolved = localFunction.resolve()
        TestCase.assertNotNull(localFunctionResolved)
        val classReference = localFunction.classReference ?: kfail("classReference expected")
        TestCase.assertEquals(
            "USimpleNameReferenceExpression (identifier = <init>, resolvesTo = PsiClass: Local)",
            classReference.asLogString()
        )
        val localClass = classReference.resolve().toUElement() ?: kfail("UElement expected")
        TestCase.assertEquals("UClass (name = Local)", localClass.asLogString())
        val localPrimaryConstructor = localFunctionResolved.toUElementOfType<UMethod>() ?: kfail("constructor expected")
        TestCase.assertTrue(localPrimaryConstructor.isConstructor)
        TestCase.assertEquals(localClass.javaPsi, localPrimaryConstructor.javaPsi.cast<PsiMethod>().containingClass)
    }

    fun checkCallbackForElvis(uFilePath: String, uFile: UFile) {
        TestCase.assertEquals(
            "UTypeReferenceExpression (name = java.lang.String)",
            uFile.findElementByTextFromPsi<UMethod>("fun foo(bar: String): String? = null").returnTypeReference?.asLogString()
        )
        TestCase.assertEquals(
            null,
            uFile.findElementByTextFromPsi<UMethod>("fun bar() = 42").returnTypeReference?.asLogString()
        )
    }

    fun checkCallbackForTypeReferences(uFilePath: String, uFile: UFile) {
        run {
            val localVariable = uFile.findElementByTextFromPsi<UVariable>("val varWithType: String? = \"Not Null\"")
            val typeReference = localVariable.typeReference
            TestCase.assertEquals("java.lang.String", typeReference?.getQualifiedName())
            val sourcePsi = typeReference?.sourcePsi ?: kfail("no sourcePsi")
            TestCase.assertTrue("sourcePsi = $sourcePsi should be physical", sourcePsi.isPhysical)
            TestCase.assertEquals("String?", sourcePsi.text)
        }

        run {
            val localVariable = uFile.findElementByTextFromPsi<UVariable>("val varWithoutType = \"lorem ipsum\"")
            val typeReference = localVariable.typeReference
            TestCase.assertEquals("java.lang.String", typeReference?.getQualifiedName())
            TestCase.assertNull(typeReference?.sourcePsi)
        }

        run {
            val localVariable = uFile.findElementByTextFromPsi<UVariable>("parameter: Int")
            val typeReference = localVariable.typeReference
            TestCase.assertEquals("int", typeReference?.type?.presentableText)
            val sourcePsi = typeReference?.sourcePsi ?: kfail("no sourcePsi")
            TestCase.assertTrue("sourcePsi = $sourcePsi should be physical", sourcePsi.isPhysical)
            TestCase.assertEquals("Int", sourcePsi.text)
        }
    }

    fun checkCallbackForReifiedReturnType(uFilePath: String, uFile: UFile) {
        val methods = uFile.classes.flatMap { it.methods.asIterable() }
        TestCase.assertEquals("""
                function1 -> PsiType:void
                function2 -> PsiType:T
                function2CharSequence -> PsiType:T extends PsiType:CharSequence
                copyWhenGreater -> PsiType:B extends PsiType:T extends PsiType:CharSequence, PsiType:Comparable<? super T>
                function3 -> PsiType:void
                function4 -> PsiType:T
                function5 -> PsiType:int
                function6 -> PsiType:T
                function7 -> PsiType:T
                function8 -> PsiType:T
                function9 -> PsiType:T
                function10 -> PsiType:T
                function11 -> PsiType:T
                function11CharSequence -> PsiType:T extends PsiType:CharSequence
                function12CharSequence -> PsiType:B extends PsiType:T extends PsiType:CharSequence
                Foo -> null
                foo -> PsiType:Z extends PsiType:T
            """.trimIndent(), methods.joinToString("\n") { m ->
            buildString {
                append(m.name).append(" -> ")
                fun PsiType.typeWithExtends(): String = buildString {
                    append(this@typeWithExtends)
                    (this@typeWithExtends as? PsiClassType)?.resolve()?.extendsList?.referencedTypes?.takeIf { it.isNotEmpty() }
                        ?.let { e ->
                            append(" extends ")
                            append(e.joinToString(", ") { it.typeWithExtends() })
                        }
                }
                append(m.returnType?.typeWithExtends())
            }
        })
        for (method in methods.drop(3)) {
            TestCase.assertEquals("assert return types comparable for '${method.name}'", method.returnType, method.returnType)
        }
    }

    fun checkCallbackForReifiedParameters(uFilePath: String, uFile: UFile) {
        val methods = uFile.classes.flatMap { it.methods.asIterable() }

        for (method in methods) {
            TestCase.assertNotNull("method ${method.name} should have source", method.sourcePsi)
            TestCase.assertEquals(
                "method ${method.name} should be equals to converted from sourcePsi",
                method,
                method.sourcePsi.toUElement()
            )
            TestCase.assertEquals("method ${method.name} should be equals to converted from javaPsi", method, method.javaPsi.toUElement())

            for (parameter in method.uastParameters) {
                TestCase.assertNotNull("parameter ${parameter.name} should have source", parameter.sourcePsi)
                TestCase.assertEquals(
                    "parameter ${parameter.name} of method ${method.name} should be equals to converted from sourcePsi",
                    parameter,
                    parameter.sourcePsi.toUElementOfType<UParameter>()
                )
                TestCase.assertEquals(
                    "parameter ${parameter.name} of method ${method.name} should be equals to converted from javaPsi",
                    parameter,
                    parameter.javaPsi.toUElement()
                )
            }
        }
    }

    fun checkCallbackForLambdaParameters(uFilePath: String, uFile: UFile) {
        val errors = mutableListOf<String>()
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                kotlin.runCatching {
                    val classResolveResult =
                        (node.getExpressionType() as? PsiClassType)?.resolveGenerics() ?: kfail("cannot resolve lambda")
                    val psiMethod =
                        LambdaUtil.getFunctionalInterfaceMethod(classResolveResult.element) ?: kfail("cannot get method signature")
                    val methodParameters = psiMethod.getSignature(classResolveResult.substitutor).parameterTypes.toList()
                    val lambdaParameters = node.parameters.map { it.type }

                    TestCase.assertEquals("parameter lists size are different", methodParameters.size, lambdaParameters.size)
                    methodParameters.zip(lambdaParameters).forEachIndexed { index, (interfaceParamType, lambdaParamType) ->
                        TestCase.assertTrue(
                            "unexpected types for param $index: $lambdaParamType cannot be assigned to $interfaceParamType",
                            interfaceParamType.isAssignableFrom(lambdaParamType)
                        )
                    }
                }.onFailure {
                    errors += "${node.getContainingUMethod()?.name}: ${it.message}"
                }

                return super.visitLambdaExpression(node)
            }
        })

        TestCase.assertTrue(
            errors.joinToString(separator = "\n", postfix = "", prefix = "") { it },
            errors.isEmpty()
        )
    }

    fun checkCallbackForComplicatedTypes(uFilePath: String, uFile: UFile) {
        val render = StringBuilder()
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                render.appendLine("${node.asRenderString()} -> typeArguments: ${node.typeArguments}")
                return super.visitCallExpression(node)
            }
        })
        TestCase.assertEquals(
            """
                <init>() -> typeArguments: [PsiType:T]
                getGenericSuperclass() -> typeArguments: []
                getActualTypeArguments() -> typeArguments: []
                first() -> typeArguments: []
            """.trimIndent(), render.toString().trimEnd()
        )
    }

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
            uFile.findElementByText<ULambdaExpression>("{ return@Runnable }").functionalInterfaceType?.canonicalText
        )
        TestCase.assertEquals(
            "java.lang.Runnable",
            uFile.findElementByText<ULambdaExpression>("{ return@l }").functionalInterfaceType?.canonicalText
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

    fun checkCallbackForSimple(uFilePath: String, uFile: UFile) {
        val simpleClass = uFile.classes.find { it.name == "Simple" }
            ?: throw IllegalStateException("Target class not found at ${uFile.asRefNames()}")

        val m = simpleClass.methods.find { it.name == "method" }
            ?: throw IllegalStateException("Target function not found at ${uFile.asRefNames()}")
        // type delegated from LC
        TestCase.assertEquals(PsiType.VOID, m.returnType)
        // type through the base service
        val service = ServiceManager.getService(BaseKotlinUastResolveProviderService::class.java)
        TestCase.assertEquals(PsiType.VOID, service.getType(m.sourcePsi as KtDeclaration, m as UElement))

        val functionCall = m.findElementByText<UElement>("println").uastParent as KotlinUFunctionCallExpression
        // type through the base service: KotlinUElementWithType#getExpressionType
        TestCase.assertEquals("kotlin.Unit", functionCall.getExpressionType()?.canonicalText)
    }

    fun checkSwitchYieldTargets(uFilePath: String, uFile: UFile) {
        uFile.accept(object : AbstractUastVisitor() {
            private val switches: MutableList<USwitchExpression> = mutableListOf()

            override fun visitSwitchExpression(node: USwitchExpression): Boolean {
                switches.add(node)
                return super.visitSwitchExpression(node)
            }

            override fun afterVisitSwitchExpression(node: USwitchExpression) {
                switches.remove(node)
                super.afterVisitSwitchExpression(node)
            }

            override fun visitYieldExpression(node: UYieldExpression): Boolean {
                TestCase.assertNotNull(node.jumpTarget)
                TestCase.assertTrue(node.jumpTarget in switches)
                return super.visitYieldExpression(node)
            }
        })
    }

    fun checkCallbackForRetention(uFilePath: String, uFile: UFile) {
        val anno = uFile.classes.find { it.name == "Anno" }
            ?: throw IllegalStateException("Target class not found at ${uFile.asRefNames()}")
        TestCase.assertTrue("@Anno is not an annotation?!", anno.isAnnotationType)
        for (psi in anno.javaPsi.annotations) {
            val uAnnotation = anno.uAnnotations.find { it.javaPsi == psi } ?: continue
            val rebuiltAnnotation = psi.toUElement(UAnnotation::class.java)
            TestCase.assertNotNull("Should be able to rebuild UAnnotation from $psi", rebuiltAnnotation)
            TestCase.assertEquals(uAnnotation.qualifiedName, rebuiltAnnotation!!.qualifiedName)

            if (rebuiltAnnotation.qualifiedName?.endsWith("Retention") == true) {
                val value = rebuiltAnnotation.findAttributeValue("value")
                val reference = value as? UReferenceExpression
                TestCase.assertNotNull("Can't find the reference to @Retention value", reference)
                TestCase.assertEquals("SOURCE", (reference?.referenceNameElement as? UIdentifier)?.name)
            }
        }
    }

    fun checkReturnJumpTargets(uFilePath: String, uFile: UFile) {
        uFile.accept(object : AbstractUastVisitor() {
            private val methods: MutableList<UMethod> = mutableListOf()
            private val lambdas: MutableList<ULambdaExpression> = mutableListOf()
            private val labels : MutableList<ULabeledExpression> = mutableListOf()

            override fun visitMethod(node: UMethod): Boolean {
                methods.add(node)
                return super.visitMethod(node)
            }

            override fun afterVisitMethod(node: UMethod) {
                methods.remove(node)
                super.afterVisitMethod(node)
            }

            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                lambdas.add(node)
                return super.visitLambdaExpression(node)
            }

            override fun afterVisitLambdaExpression(node: ULambdaExpression) {
                lambdas.remove(node)
                super.afterVisitLambdaExpression(node)
            }

            override fun visitLabeledExpression(node: ULabeledExpression): Boolean {
                labels.add(node)
                return super.visitLabeledExpression(node)
            }

            override fun afterVisitLabeledExpression(node: ULabeledExpression) {
                labels.remove(node)
                super.afterVisitLabeledExpression(node)
            }

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                TestCase.assertNotNull(node.jumpTarget)
                when (val returnTarget = node.jumpTarget) {
                    is UMethod -> { // return@foo
                        TestCase.assertTrue(returnTarget in methods)
                    }
                    is ULambdaExpression -> { // return@forEach
                        TestCase.assertTrue(returnTarget in lambdas)
                    }
                    is ULabeledExpression -> { // return@l
                        TestCase.assertTrue(returnTarget in labels)
                    }
                    else -> TestCase.fail("Unexpected return target: $returnTarget")
                }
                return super.visitReturnExpression(node)
            }
        })
    }

    fun checkCallbackForComplexStrings(uFilePath: String, uFile: UFile) {
        TestCase.assertEquals(
            "\"\"\"\n        text=text\n    \"\"\".trimIndent()",
            uFile.findElementByText<UInjectionHost>("\"\"\"\n        text=text\n    \"\"\"").getStringRoomExpression().sourcePsi?.text
        )
        TestCase.assertEquals(
            "\"\"\"\n        | margined\n    \"\"\".trimMargin()",
            uFile.findElementByText<UInjectionHost>("\"\"\"\n        | margined\n    \"\"\"").getStringRoomExpression().sourcePsi?.text
        )
        TestCase.assertEquals(
            "\"bar\"",
            uFile.findElementByTextFromPsi<UInjectionHost>("\"bar\"").getStringRoomExpression().sourcePsi?.text
        )
        TestCase.assertEquals(
            "\"abc\" + \"cde\" + \"efg\"",
            uFile.findElementByTextFromPsi<UInjectionHost>("\"efg\"").getStringRoomExpression().sourcePsi?.text
        )
    }
}
