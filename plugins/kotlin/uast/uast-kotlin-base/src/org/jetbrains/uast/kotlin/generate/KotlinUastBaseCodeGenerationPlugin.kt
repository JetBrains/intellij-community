// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin.generate

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UParameterInfo
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.UastElementFactory
import org.jetbrains.uast.kotlin.*
import org.jetbrains.uast.kotlin.internal.KotlinFakeUElement

abstract class KotlinUastBaseCodeGenerationPlugin : UastCodeGenerationPlugin {
    override val language: Language
        get() = KotlinLanguage.INSTANCE

    override fun getElementFactory(project: Project): UastElementFactory =
        KotlinUastElementFactory(project)

    override fun <T : UElement> replace(oldElement: UElement, newElement: T, elementType: Class<T>): T? {
        val oldPsi = oldElement.toSourcePsiFakeAware().singleOrNull() ?: return null
        val newPsi = newElement.sourcePsi?.let {
            when {
                it is KtCallExpression && it.parent is KtQualifiedExpression -> it.parent
                else -> it
            }
        } ?: return null

        val psiFactory = KtPsiFactory(oldPsi.project)
        val oldParentPsi = oldPsi.parent
        val (updOldPsi, updNewPsi) = when {
            oldParentPsi is KtStringTemplateExpression && oldParentPsi.entries.size == 1 ->
                oldParentPsi to newPsi

            oldPsi is KtStringTemplateEntry && newPsi !is KtStringTemplateEntry && newPsi is KtExpression ->
                oldPsi to psiFactory.createBlockStringTemplateEntry(newPsi)

            oldPsi is KtBlockExpression && newPsi is KtBlockExpression -> {
                if (!hasBraces(oldPsi) && hasBraces(newPsi)) {
                    oldPsi to psiFactory.createLambdaExpression("none", newPsi.statements.joinToString("\n") { "println()" }).bodyExpression!!.also {
                        it.statements.zip(newPsi.statements).forEach { it.first.replace(it.second) }
                    }
                } else
                    oldPsi to newPsi
            }

            else ->
                oldPsi to newPsi
        }
        val replaced = (updOldPsi.replace(updNewPsi) as? KtElement)?.let { shortenReference(it) ?: it }
        return when  {
            newElement.sourcePsi is KtCallExpression && replaced is KtQualifiedExpression -> replaced.selectorExpression
            else -> replaced
        }?.toUElementOfExpectedTypes(elementType)
    }

    override fun bindToElement(reference: UReferenceExpression, element: PsiElement): PsiElement? {
        val sourcePsi = reference.sourcePsi ?: return null
        if (sourcePsi !is KtSimpleNameExpression) return null
        return sourcePsi.mainReference.bindToElement(element, KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING)
    }

    override fun shortenReference(reference: UReferenceExpression): UReferenceExpression? {
        val sourcePsi = reference.sourcePsi ?: return null
        if (sourcePsi !is KtElement) return null
        return shortenReference(sourcePsi).toUElementOfType()
    }

    abstract fun shortenReference(sourcePsi: KtElement): PsiElement?

    override fun importMemberOnDemand(reference: UQualifiedReferenceExpression): UExpression? {
        TODO("Not implemented")
    }

    override fun initializeField(uField: UField, uParameter: UParameter): UExpression? {
        val uMethod = uParameter.getParentOfType(UMethod::class.java, false) ?: return null
        val sourcePsi = uMethod.sourcePsi ?: return null
        if (sourcePsi is KtPrimaryConstructor) {
            if (uField.name == uParameter.name) {
                val psiElement = uParameter.sourcePsi ?: return null
                val ktParameter = KtPsiFactory(psiElement.project).createParameter(uField.sourcePsi?.text ?: return null)
                ktParameter.modifierList?.getModifier(KtTokens.FINAL_KEYWORD)?.delete()
                ktParameter.defaultValue?.delete()
                ktParameter.equalsToken?.delete()
                val psiField = uField.sourcePsi
                if (psiField != null) {
                    val nextSibling = psiField.nextSibling
                    if (nextSibling is PsiWhiteSpace) {
                        nextSibling.delete()
                    }
                    psiField.delete()
                }
                psiElement.replace(ktParameter)
                return ktParameter.toUElementOfType()
            }
            else {
                val property = uField.sourcePsi as? KtProperty ?: return null
                property.initializer = KtPsiFactory(property.project).createExpression(uParameter.name)
                return property.initializer.toUElementOfType()
            }
        }

        val body = (sourcePsi as? KtDeclarationWithBody)?.bodyBlockExpression ?: return null
        val ktPsiFactory = KtPsiFactory(sourcePsi.project, true)
        val assignmentExpression = ktPsiFactory.buildExpression {
            if (uField.name == uParameter.name) {
                appendFixedText("this.")
            }
            appendName(Name.identifier(uField.name))
            appendFixedText(" = ")
            appendName(Name.identifier(uParameter.name))
        }

        body.addBefore(assignmentExpression, body.rBrace)
        return assignmentExpression.toUElementOfType()
    }

    override fun changeLabel(returnExpression: UReturnExpression, context: PsiElement): UReturnExpression {
        if (returnExpression is KotlinUImplicitReturnExpression) return returnExpression
        val factory = getElementFactory(context.project)

        return factory.createReturnExpression(expression = returnExpression.returnExpression, inLambda = true, context) ?: returnExpression
    }
}

private fun hasBraces(oldPsi: KtBlockExpression): Boolean = oldPsi.lBrace != null && oldPsi.rBrace != null

open class KotlinUastElementFactory(project: Project) : UastElementFactory {
    private val contextlessPsiFactory = KtPsiFactory(project)

    private fun psiFactory(context: PsiElement?): KtPsiFactory {
        return if (context != null) KtPsiFactory.contextual(context) else contextlessPsiFactory
    }

    protected open fun PsiType?.suggestName(context: PsiElement?): String {
        return "v"
    }

    protected open fun PsiType?.getFQname(context: PsiElement?): String? {
        return getClassId()?.asFqNameString()
    }

    private fun PsiType?.getClassId(): ClassId? {
        return when {
            this == PsiTypes.intType() -> StandardClassIds.Int
            this == PsiTypes.byteType() -> StandardClassIds.Byte
            this == PsiTypes.charType() -> StandardClassIds.Char
            this == PsiTypes.longType() -> StandardClassIds.Long
            this == PsiTypes.doubleType() -> StandardClassIds.Double
            this == PsiTypes.floatType() -> StandardClassIds.Float
            this == PsiTypes.shortType() -> StandardClassIds.Short
            this == PsiTypes.booleanType() -> StandardClassIds.Boolean
            this is PsiArrayType -> StandardClassIds.Array
            this is PsiClassType -> resolve()?.qualifiedName?.let { qName ->
                JavaToKotlinClassMap.mapJavaToKotlin(FqName(qName)) ?: ClassId.fromString(qName)
            }
            else -> null
        }
    }

    protected open fun moveLambdaOutsideParenthesis(methodCall: KtCallExpression) {
        TODO("Not implemented")
    }

    override fun createQualifiedReference(qualifiedName: String, context: PsiElement?): UQualifiedReferenceExpression? {
        return psiFactory(context).createExpression(qualifiedName).let {
            when (it) {
                is KtDotQualifiedExpression -> KotlinUQualifiedReferenceExpression(it, null)
                is KtSafeQualifiedExpression -> KotlinUSafeQualifiedExpression(it, null)
                else -> null
            }
        }
    }

    override fun createMethodFromText(methodText: String, context: PsiElement?): UMethod? =
        psiFactory(context).createFunction(methodText).toUElementOfType()

    override fun createCallExpression(
        receiver: UExpression?,
        methodName: String,
        parameters: List<UExpression>,
        expectedReturnType: PsiType?,
        kind: UastCallKind,
        context: PsiElement?
    ): UCallExpression? {
        if (kind != UastCallKind.METHOD_CALL) return null

        val psiFactory = psiFactory(context)

        val name = methodName.quoteIfNeeded()
        val methodCall = psiFactory.createExpression(
            buildString {
                if (receiver != null) {
                    append("a")
                    (receiver.sourcePsi?.nextSibling as? PsiWhiteSpace)?.let { whitespaces ->
                        append(whitespaces.text)
                    }

                    receiver.comments.takeIf { it.isNotEmpty() }?.let {
                        append(receiver.comments.joinToString { it.text })
                        append("\n")
                    }
                    append(".")
                }
                append(name)
                append("()")
            }
        ).getPossiblyQualifiedCallExpression() ?: return null

        if (receiver != null) {
            methodCall.parentAs<KtDotQualifiedExpression>()?.receiverExpression?.replace(wrapULiteral(receiver).sourcePsi!!)
        }

        val valueArgumentList = methodCall.valueArgumentList
        for (parameter in parameters) {
            valueArgumentList?.addArgument(psiFactory.createArgument(wrapULiteral(parameter).sourcePsi as KtExpression))
        }

        if (context !is KtElement) return KotlinUFunctionCallExpression(methodCall, null)

        val analyzableMethodCall = psiFactory.getAnalyzableMethodCall(methodCall, context)
        moveLambdaOutsideParenthesis(analyzableMethodCall)

        if (expectedReturnType == null) return KotlinUFunctionCallExpression(analyzableMethodCall, null)

        val methodCallPsiType = KotlinUFunctionCallExpression(analyzableMethodCall, null).getExpressionType()
        if (methodCallPsiType == null || !expectedReturnType.isAssignableFrom(GenericsUtil.eliminateWildcards(methodCallPsiType))) {
            val typeParams = (expectedReturnType as? PsiClassType)?.parameters?.map { it.getFQname(context) }
            if (typeParams == null) return KotlinUFunctionCallExpression(analyzableMethodCall, null)

            for (typeParam in typeParams) {
                val typeParameter = psiFactory.createTypeArgument(typeParam.orEmpty())
                analyzableMethodCall.addTypeArgument(typeParameter)
            }
            return KotlinUFunctionCallExpression(analyzableMethodCall, null)
        }
        return KotlinUFunctionCallExpression(analyzableMethodCall, null)
    }

    private fun KtPsiFactory.getAnalyzableMethodCall(methodCall: KtCallExpression, context: KtElement): KtCallExpression {
        val analyzableElement = ((createExpressionCodeFragment("(null)", context).copy() as KtExpressionCodeFragment)
            .getContentElement()!! as KtParenthesizedExpression).expression!!

        val isQualified = methodCall.parent is KtQualifiedExpression
        return if (isQualified) {
            (analyzableElement.replaced(methodCall.parent) as KtQualifiedExpression).lastChild as KtCallExpression
        } else {
            analyzableElement.replaced(methodCall)
        }
    }

    override fun createCallableReferenceExpression(
        receiver: UExpression?,
        methodName: String,
        context: PsiElement?
    ): UCallableReferenceExpression? {
        val text = receiver?.sourcePsi?.text ?: ""
        val callableExpression = psiFactory(context).createCallableReferenceExpression("$text::$methodName") ?: return null
        return KotlinUCallableReferenceExpression(callableExpression, null)
    }

    override fun createStringLiteralExpression(text: String, context: PsiElement?): UExpression {
        val literal = psiFactory(context).createExpression(StringUtil.wrapWithDoubleQuote(text)) as KtStringTemplateExpression
        return KotlinStringTemplateUPolyadicExpression(literal, null)
    }

    override fun createLongConstantExpression(long: Long, context: PsiElement?): UExpression? {
        return when (val literalExpr = psiFactory(context).createExpression(long.toString() + "L")) {
            is KtConstantExpression -> KotlinULiteralExpression(literalExpr, null)
            is KtPrefixExpression -> KotlinUPrefixExpression(literalExpr, null)
            else -> null
        }
    }

    override fun createNullLiteral(context: PsiElement?): ULiteralExpression {
        return psiFactory(context).createExpression("null").toUElementOfType()!!
    }

    /*override*/ fun createIntLiteral(value: Int, context: PsiElement?): ULiteralExpression {
        return psiFactory(context).createExpression(value.toString()).toUElementOfType()!!
    }

    private fun KtExpression.ensureBlockExpressionBraces(psiFactory: KtPsiFactory): KtExpression {
        if (this !is KtBlockExpression || hasBraces(this)) return this
        val blockExpression = psiFactory.createBlock(this.statements.joinToString("\n") { "println()" })
        for ((placeholder, statement) in blockExpression.statements.zip(this.statements)) {
            placeholder.replace(statement)
        }
        return blockExpression
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createIfExpression(condition: UExpression, thenBranch: UExpression, elseBranch: UExpression?): UIfExpression? {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createIfExpression(condition, thenBranch, elseBranch, null)
    }

    override fun createIfExpression(
        condition: UExpression,
        thenBranch: UExpression,
        elseBranch: UExpression?,
        context: PsiElement?
    ): UIfExpression? {
        val conditionPsi = condition.sourcePsi as? KtExpression ?: return null
        val thenBranchPsi = thenBranch.sourcePsi as? KtExpression ?: return null
        val elseBranchPsi = elseBranch?.sourcePsi as? KtExpression

        val psiFactory = psiFactory(context)

        return KotlinUIfExpression(
            psiFactory.createIf(
                conditionPsi,
                thenBranchPsi.ensureBlockExpressionBraces(psiFactory),
                elseBranchPsi?.ensureBlockExpressionBraces(psiFactory)
            ),
            givenParent = null
        )
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createParenthesizedExpression(expression: UExpression): UParenthesizedExpression? {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createParenthesizedExpression(expression, null)
    }

    override fun createParenthesizedExpression(expression: UExpression, context: PsiElement?): UParenthesizedExpression? {
        val source = expression.sourcePsi ?: return null
        val parenthesized = psiFactory(context).createExpression("(${source.text})") as? KtParenthesizedExpression ?: return null
        return KotlinUParenthesizedExpression(parenthesized, null)
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createSimpleReference(name: String): USimpleNameReferenceExpression {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createSimpleReference(name, null)
    }

    override fun createSimpleReference(name: String, context: PsiElement?): USimpleNameReferenceExpression {
        return KotlinUSimpleReferenceExpression(psiFactory(context).createSimpleName(name), null)
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createSimpleReference(variable: UVariable): USimpleNameReferenceExpression? {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createSimpleReference(variable, null)
    }

    override fun createSimpleReference(variable: UVariable, context: PsiElement?): USimpleNameReferenceExpression? {
        return createSimpleReference(variable.name ?: return null, context)
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createReturnExpresion(expression: UExpression?, inLambda: Boolean): UReturnExpression {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createReturnExpression(expression, inLambda, null)
    }

    override fun createReturnExpression(expression: UExpression?, inLambda: Boolean, context: PsiElement?): UReturnExpression {
        val label = if (inLambda && context != null) getParentLambdaLabelName(context)?.let { "@$it" } ?: "" else ""
        val returnExpression = psiFactory(context).createExpression("return$label 1") as KtReturnExpression
        val sourcePsi = expression?.sourcePsi
        if (sourcePsi != null) {
            returnExpression.returnedExpression!!.replace(sourcePsi)
        } else {
            returnExpression.returnedExpression?.delete()
        }
        return KotlinUReturnExpression(returnExpression, null)
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createBinaryExpression(
        leftOperand: UExpression,
        rightOperand: UExpression,
        operator: UastBinaryOperator
    ): UBinaryExpression? {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createBinaryExpression(leftOperand, rightOperand, operator, null)
    }

    override fun createBinaryExpression(
        leftOperand: UExpression,
        rightOperand: UExpression,
        operator: UastBinaryOperator,
        context: PsiElement?
    ): UBinaryExpression? {
        val binaryExpression = joinBinaryExpression(leftOperand, rightOperand, operator, context) ?: return null
        return KotlinUBinaryExpression(binaryExpression, null)
    }

    private fun joinBinaryExpression(
        leftOperand: UExpression,
        rightOperand: UExpression,
        operator: UastBinaryOperator,
        context: PsiElement?
    ): KtBinaryExpression? {
        val leftPsi = leftOperand.sourcePsi ?: return null
        val rightPsi = rightOperand.sourcePsi ?: return null

        val binaryExpression = psiFactory(context).createExpression("a ${operator.text} b") as? KtBinaryExpression ?: return null
        binaryExpression.left?.replace(leftPsi)
        binaryExpression.right?.replace(rightPsi)
        return binaryExpression
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createFlatBinaryExpression(
        leftOperand: UExpression,
        rightOperand: UExpression,
        operator: UastBinaryOperator
    ): UPolyadicExpression? {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createFlatBinaryExpression(leftOperand, rightOperand, operator, null)
    }

    override fun createFlatBinaryExpression(
        leftOperand: UExpression,
        rightOperand: UExpression,
        operator: UastBinaryOperator,
        context: PsiElement?
    ): UPolyadicExpression? {
        fun unwrapParentheses(exp: KtExpression?) {
            if (exp !is KtParenthesizedExpression) return
            if (!KtPsiUtil.areParenthesesUseless(exp)) return
            exp.expression?.let { exp.replace(it) }
        }

        val binaryExpression = joinBinaryExpression(leftOperand, rightOperand, operator, context) ?: return null
        unwrapParentheses(binaryExpression.left)
        unwrapParentheses(binaryExpression.right)

        return psiFactory(context).createExpression(binaryExpression.text).toUElementOfType()!!
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createBlockExpression(expressions: List<UExpression>): UBlockExpression {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createBlockExpression(expressions, null)
    }

    override fun createBlockExpression(expressions: List<UExpression>, context: PsiElement?): UBlockExpression {
        val sourceExpressions = expressions.flatMap { it.toSourcePsiFakeAndCommentsAware() }
        val block = psiFactory(context).createBlock(
            sourceExpressions.joinToString(separator = "\n") { "println()" }
        )
        for ((placeholder, psiElement) in block.statements.zip(sourceExpressions)) {
            placeholder.replace(psiElement)
        }
        return KotlinUBlockExpression(block, null)
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createDeclarationExpression(declarations: List<UDeclaration>): UDeclarationsExpression {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createDeclarationExpression(declarations, null)
    }

    override fun createDeclarationExpression(declarations: List<UDeclaration>, context: PsiElement?): UDeclarationsExpression {
        return object : KotlinUDeclarationsExpression(null), KotlinFakeUElement {
            override var declarations: List<UDeclaration> = declarations
            override fun unwrapToSourcePsi(): List<PsiElement> = declarations.flatMap { it.toSourcePsiFakeAware() }
        }
    }

    override fun createLambdaExpression(
        parameters: List<UParameterInfo>,
        body: UExpression,
        context: PsiElement?
    ): ULambdaExpression? {
        val newLambdaStatements = if (body is UBlockExpression) {
            body.expressions.flatMap { member ->
                when (member) {
                    is UReturnExpression -> member.returnExpression?.toSourcePsiFakeAware().orEmpty()
                    else -> member.toSourcePsiFakeAware()
                }
            }
        } else
            listOf(body.sourcePsi!!)

        val ktLambdaExpression = psiFactory(context).createLambdaExpression(
          parameters.joinToString(", ") { p ->
              StringBuilder().apply {
                  append(p.suggestedName ?: p.type.suggestName(context))
                  p.type.getFQname(context)?.let { append(": ").append(it) }
              }
          },
          newLambdaStatements.joinToString("\n") { "placeholder" }
        )

        for ((old, new) in ktLambdaExpression.bodyExpression!!.statements.zip(newLambdaStatements)) {
            old.replace(new)
        }

        return ktLambdaExpression.toUElementOfType()!!
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createLambdaExpression(parameters: List<UParameterInfo>, body: UExpression): ULambdaExpression? {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createLambdaExpression(parameters, body, null)
    }

    override fun createLocalVariable(
        suggestedName: String?,
        type: PsiType?,
        initializer: UExpression,
        immutable: Boolean,
        context: PsiElement?
    ): ULocalVariable {
        val function = psiFactory(context).createFunction(buildString {
            append("fun foo() { ")
            append(if (immutable) "val" else "var")
            append(" ")
            append(suggestedName ?: type.suggestName(context))
            type.getFQname(context)?.let { append(": ").append(it) }
            append(" = null")
            append("}")
        })

        val ktVariable = PsiTreeUtil.findChildOfType(function, KtVariableDeclaration::class.java)!!
        val newVariable = ktVariable.initializer!!.replace(initializer.sourcePsi!!).parent
        return newVariable.toUElementOfType<UVariable>() as ULocalVariable
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated("use version with context parameter")
    fun createLocalVariable(
        suggestedName: String?,
        type: PsiType?,
        initializer: UExpression,
        immutable: Boolean
    ): ULocalVariable {
        logger<KotlinUastElementFactory>().error("Please switch caller to the version with a context parameter")
        return createLocalVariable(suggestedName, type, initializer, immutable, null)
    }
}

private inline fun <reified T : PsiElement> PsiElement.replaced(newElement: T): T {
    if (this == newElement) {
        return newElement
    }

    return when (val result = replace(newElement)) {
        is T -> result
        else -> (result as KtParenthesizedExpression).expression as T
    }
}


private fun getParentLambdaLabelName(context: PsiElement): String? {
    val lambdaExpression = context.getNonStrictParentOfType<KtLambdaExpression>() ?: return null
    lambdaExpression.parentAs<KtLabeledExpression>()?.let { return it.getLabelName() }
    val callExpression = lambdaExpression.getStrictParentOfType<KtCallExpression>() ?: return null
    callExpression.valueArguments.find {
        it.getArgumentExpression()?.unpackFunctionLiteral(allowParentheses = false) === lambdaExpression
    } ?: return null
    return callExpression.getCallNameExpression()?.text
}