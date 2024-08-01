// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.ApiVersion.Companion.KOTLIN_1_8
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK1Mode
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.conversions.ReplaceType.REPLACE_SELECTOR
import org.jetbrains.kotlin.nj2k.conversions.ReplaceType.REPLACE_WITH_QUALIFIER
import org.jetbrains.kotlin.nj2k.symbols.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression.LiteralType.INT
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression.LiteralType.STRING
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class BuiltinMembersConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    private val conversions: Map<String, List<Conversion>> =
        ConversionsHolder(symbolProvider, typeFactory).getConversions()

    context(KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKExpression) return recurse(element)
        return recurse(element.convert() ?: element)
    }

    context(KaSession)
    private fun JKExpression.convert(): JKExpression? {
        val selector = when (this) {
            is JKQualifiedExpression -> selector
            else -> this
        }

        val conversion = selector.getConversion() ?: return null
        val newSelector = conversion.createBuilder().build(selector)

        if (this is JKQualifiedExpression && conversion.replaceType == REPLACE_WITH_QUALIFIER) {
            newSelector.commentsAfter += receiver.commentsBefore
            newSelector.commentsAfter += receiver.commentsAfter
            newSelector.commentsAfter += selector.commentsBefore
            newSelector.commentsAfter += selector.commentsAfter
        }

        return when (conversion.replaceType) {
            REPLACE_SELECTOR -> {
                if (this is JKQualifiedExpression) {
                    this.selector = newSelector
                    this
                } else newSelector
            }

            REPLACE_WITH_QUALIFIER -> newSelector
        }.let { expression ->
            conversion.actionAfter?.invoke(expression.copyTreeAndDetach()) ?: expression
        }
    }

    private fun JKExpression.getConversion(): Conversion? = when (this) {
        is JKCallExpression ->
            conversions[identifier.deepestFqName()]?.firstOrNull { conversion ->
                if (conversion.from !is Method) return@firstOrNull false
                if (conversion.filter?.invoke(this) == false) return@firstOrNull false
                if (conversion.byArgumentsFilter?.invoke(arguments.arguments.map { it.value }) == false) return@firstOrNull false
                true
            }

        is JKFieldAccessExpression ->
            conversions[identifier.deepestFqName()]?.firstOrNull { conversion ->
                if (conversion.from !is Field) return@firstOrNull false
                if (conversion.filter?.invoke(this) == false) return@firstOrNull false
                true
            }

        is JKMethodAccessExpression ->
            conversions[identifier.deepestFqName()]?.firstOrNull { conversion ->
                if (conversion.from !is Method) return@firstOrNull false
                if (conversion.to !is Method) return@firstOrNull false
                if (conversion.filter?.invoke(this) == false) return@firstOrNull false
                true
            }

        is JKNewExpression ->
            conversions[classSymbol.deepestFqName()]?.firstOrNull { conversion ->
                if (conversion.from !is NewExpression) return@firstOrNull false
                if (conversion.filter?.invoke(this) == false) return@firstOrNull false
                if (conversion.byArgumentsFilter?.invoke(arguments.arguments.map { it.value }) == false) return@firstOrNull false
                true
            }

        else -> null
    }?.takeIf { conversion ->
        conversion.sinceKotlin?.let { it <= moduleApiVersion } ?: true
    }

    private interface ResultBuilder {
        context(KaSession)
        fun build(from: JKExpression): JKExpression
    }

    private inner class MethodBuilder(
        private val fqName: String,
        private val parameterTypesFqNames: List<String>?,
        private val argumentsProvider: (JKArgumentList) -> JKArgumentList,
        private val canMoveLambdaOutsideParentheses: Boolean = false
    ) : ResultBuilder {
        context(KaSession)
        override fun build(from: JKExpression): JKExpression {
            val methodSymbol = if (parameterTypesFqNames == null) {
                symbolProvider.provideMethodSymbol(fqName)
            } else {
                symbolProvider.provideMethodSymbolWithExactSignature(fqName, parameterTypesFqNames)
            }

            val type = determineNewExpressionType(methodSymbol, from)

            return when (from) {
                is JKCallExpression -> {
                    JKCallExpressionImpl(
                        methodSymbol,
                        argumentsProvider(from::arguments.detached()),
                        from::typeArgumentList.detached(),
                        type,
                        canMoveLambdaOutsideParentheses
                    )
                }

                is JKFieldAccessExpression ->
                    JKCallExpressionImpl(
                        methodSymbol,
                        JKArgumentList(),
                        JKTypeArgumentList(),
                        type
                    )

                is JKMethodAccessExpression -> JKMethodAccessExpression(methodSymbol, type)
                is JKNewExpression ->
                    JKCallExpressionImpl(
                        methodSymbol,
                        argumentsProvider(from::arguments.detached()),
                        JKTypeArgumentList(),
                        type,
                        canMoveLambdaOutsideParentheses
                    )

                else -> error("Bad conversion")
            }.withFormattingFrom(from)
        }
    }

    private inner class FieldBuilder(private val fqName: String) : ResultBuilder {
        context(KaSession)
        override fun build(from: JKExpression): JKExpression {
            if (from !is JKCallExpression && from !is JKFieldAccessExpression) error("Bad conversion")
            val symbol = symbolProvider.provideFieldSymbol(fqName)
            val type = determineNewExpressionType(symbol, from)
            return JKFieldAccessExpression(symbol, type).withFormattingFrom(from)
        }
    }

    private inner class ExtensionMethodBuilder(private val fqName: String) : ResultBuilder {
        context(KaSession)
        override fun build(from: JKExpression): JKExpression {
            if (from !is JKCallExpression) error("Bad conversion")

            // Before transforming the call to an extension function call,
            // we need to handle the implicit cast on the first argument.
            // Otherwise, it won't be handled later in the conversion pipeline,
            // since it won't be an argument anymore, but a call receiver.
            ImplicitCastsConversion(context).applyToElement(from)

            val methodSymbol = symbolProvider.provideMethodSymbol(fqName)
            val arguments = from.arguments::arguments.detached()
            val type = determineNewExpressionType(methodSymbol, from)

            return JKQualifiedExpression(
                arguments.first()::value.detached().parenthesizeIfCompoundExpression(),
                JKCallExpressionImpl(
                    methodSymbol,
                    JKArgumentList(arguments.drop(1)),
                    from::typeArgumentList.detached(),
                    type
                ),
                type
            ).withFormattingFrom(from)
        }
    }

    private inner class CustomExpressionBuilder(val builder: (JKExpression) -> JKExpression) : ResultBuilder {
        context(KaSession)
        override fun build(from: JKExpression): JKExpression = builder(from)
    }

    private fun Conversion.createBuilder(): ResultBuilder = when (to) {
        is Method -> MethodBuilder(to.fqName, to.parameterTypesFqNames, argumentsProvider ?: { it }, to.canMoveLambdaOutsideParentheses)
        is Field -> FieldBuilder(to.fqName)
        is ExtensionMethod -> ExtensionMethodBuilder(to.fqName)
        is CustomExpression -> CustomExpressionBuilder(to.expressionBuilder)
        else -> error("Bad conversion")
    }

    // Usually, the types of original and replacement expressions should be semantically the same,
    // but this is not always the case with number types (ex. with java.lang.Math vs. kotlin.math methods)
    context(KaSession)
    private fun determineNewExpressionType(newSymbol: JKSymbol, originalExpression: JKExpression): JKType? {
        val symbolType = when (newSymbol) {
            is JKMethodSymbol -> newSymbol.returnType
            is JKFieldSymbol -> newSymbol.fieldType
            else -> null
        }
        return symbolType ?: originalExpression.calculateType(typeFactory)
    }
}

private enum class ReplaceType { REPLACE_SELECTOR, REPLACE_WITH_QUALIFIER }

private data class Conversion(
    val from: SymbolInfo,
    val to: Info,
    val sinceKotlin: ApiVersion? = null,
    val replaceType: ReplaceType = REPLACE_SELECTOR,
    val filter: ((JKExpression) -> Boolean)? = null,
    val byArgumentsFilter: ((List<JKExpression>) -> Boolean)? = null,
    val argumentsProvider: ((JKArgumentList) -> JKArgumentList)? = null,
    val actionAfter: ((JKExpression) -> JKExpression)? = null
) {
    infix fun sinceKotlin(apiVersion: ApiVersion): Conversion =
        copy(sinceKotlin = apiVersion)

    infix fun withReplaceType(replaceType: ReplaceType): Conversion =
        copy(replaceType = replaceType)

    infix fun withFilter(filter: (JKExpression) -> Boolean): Conversion =
        copy(filter = filter)

    infix fun withByArgumentsFilter(filter: (List<JKExpression>) -> Boolean): Conversion =
        copy(byArgumentsFilter = filter)

    infix fun withArgumentsProvider(argumentsProvider: (JKArgumentList) -> JKArgumentList): Conversion =
        copy(argumentsProvider = argumentsProvider)

    infix fun andAfter(actionAfter: (JKExpression) -> JKExpression): Conversion =
        copy(actionAfter = actionAfter)
}

private interface Info

private interface SymbolInfo : Info {
    val fqName: String

    infix fun convertTo(to: Info): Conversion = Conversion(this, to)
}

private data class Method(
    override val fqName: String,
    val parameterTypesFqNames: List<String>? = null,
    val canMoveLambdaOutsideParentheses: Boolean = false
) : SymbolInfo

private data class NewExpression(override val fqName: String) : SymbolInfo

private data class Field(override val fqName: String) : SymbolInfo

private data class ExtensionMethod(override val fqName: String) : SymbolInfo

private data class CustomExpression(val expressionBuilder: (JKExpression) -> JKExpression) : Info

private class ConversionsHolder(private val symbolProvider: JKSymbolProvider, private val typeFactory: JKTypeFactory) {
    fun getConversions(): Map<String, List<Conversion>> {
        val conversions = primitiveConversions +
                objectConversions +
                collectionConversions +
                streamConversions +
                enumConversions +
                throwableConversions +
                stringConversions +
                arrayConversions +
                printlnConversions +
                mathConversions
        return conversions.groupBy { it.from.fqName }
    }

    private val neutralLocaleFQNames: List<String> = listOf(
        "java.util.Locale.ROOT",
        "java.util.Locale.US",
        "java.util.Locale.ENGLISH"
    )

    private val primitiveConversions: List<Conversion> = listOf(
        Method("java.lang.Boolean.valueOf") convertTo valueOfReplacement() withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Byte.valueOf") convertTo valueOfReplacement() withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Short.valueOf") convertTo valueOfReplacement() withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Integer.valueOf") convertTo valueOfReplacement() withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Long.valueOf") convertTo valueOfReplacement() withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Float.valueOf") convertTo valueOfReplacement() withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Double.valueOf") convertTo valueOfReplacement() withReplaceType REPLACE_WITH_QUALIFIER,

        Method("java.lang.Boolean.parseBoolean") convertTo ExtensionMethod("kotlin.text.toBoolean") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Byte.parseByte") convertTo ExtensionMethod("kotlin.text.toByte") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Short.parseShort") convertTo ExtensionMethod("kotlin.text.toShort") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Integer.parseInt") convertTo ExtensionMethod("kotlin.text.toInt") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size <= 2 },
        Method("java.lang.Long.parseLong") convertTo ExtensionMethod("kotlin.text.toLong") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size <= 2 },
        Method("java.lang.Float.parseFloat") convertTo ExtensionMethod("kotlin.text.toFloat") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Double.parseDouble") convertTo ExtensionMethod("kotlin.text.toDouble") withReplaceType REPLACE_WITH_QUALIFIER,

        Method("java.lang.Number.byteValue") convertTo Method("kotlin.Number.toByte"),
        Method("java.lang.Number.doubleValue") convertTo Method("kotlin.Number.toDouble"),
        Method("java.lang.Number.floatValue") convertTo Method("kotlin.Number.toFloat"),
        Method("java.lang.Number.intValue") convertTo Method("kotlin.Number.toInt"),
        Method("java.lang.Number.longValue") convertTo Method("kotlin.Number.toLong"),
        Method("java.lang.Number.shortValue") convertTo Method("kotlin.Number.toShort"),

        Method("java.lang.Boolean.toString") convertTo ExtensionMethod("kotlin.Boolean.toString") withByArgumentsFilter { it.size == 1 } withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Character.toString") convertTo ExtensionMethod("kotlin.Char.toString") withReplaceType REPLACE_WITH_QUALIFIER withByArgumentsFilter { arguments ->
            if (arguments.size != 1) return@withByArgumentsFilter false
            arguments[0].calculateType(typeFactory) == JKJavaPrimitiveType.CHAR
        },
        Method("java.lang.Byte.toString") convertTo ExtensionMethod("kotlin.Byte.toString") withByArgumentsFilter { it.size == 1 } withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Short.toString") convertTo ExtensionMethod("kotlin.Short.toString") withByArgumentsFilter { it.size == 1 } withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Integer.toString") convertTo ExtensionMethod("kotlin.Int.toString") withByArgumentsFilter { it.size == 1 } withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Integer.toString") convertTo primitiveToStringWithRadix() withByArgumentsFilter (::primitiveToStringWithRadixArgumentsFilter) withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Long.toString") convertTo ExtensionMethod("kotlin.Long.toString") withByArgumentsFilter { it.size == 1 } withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Long.toString") convertTo primitiveToStringWithRadix() withByArgumentsFilter (::primitiveToStringWithRadixArgumentsFilter) withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Float.toString") convertTo ExtensionMethod("kotlin.Float.toString") withByArgumentsFilter { it.size == 1 } withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Double.toString") convertTo ExtensionMethod("kotlin.Double.toString") withByArgumentsFilter { it.size == 1 } withReplaceType REPLACE_WITH_QUALIFIER,

        Field("java.lang.Byte.MIN_VALUE") convertTo Field("kotlin.Byte.Companion.MIN_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Byte.MAX_VALUE") convertTo Field("kotlin.Byte.Companion.MAX_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Short.MIN_VALUE") convertTo Field("kotlin.Short.Companion.MIN_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Short.MAX_VALUE") convertTo Field("kotlin.Short.Companion.MAX_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Integer.MIN_VALUE") convertTo Field("kotlin.Int.Companion.MIN_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Integer.MAX_VALUE") convertTo Field("kotlin.Int.Companion.MAX_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Long.MIN_VALUE") convertTo Field("kotlin.Long.Companion.MIN_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Long.MAX_VALUE") convertTo Field("kotlin.Long.Companion.MAX_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Float.MIN_VALUE") convertTo Field("kotlin.Float.Companion.MIN_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Float.MAX_VALUE") convertTo Field("kotlin.Float.Companion.MAX_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Float.POSITIVE_INFINITY") convertTo Field("kotlin.Float.Companion.POSITIVE_INFINITY") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Float.NEGATIVE_INFINITY") convertTo Field("kotlin.Float.Companion.NEGATIVE_INFINITY") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Float.NaN") convertTo Field("kotlin.Float.Companion.NaN") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Double.MIN_VALUE") convertTo Field("kotlin.Double.Companion.MIN_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Double.MAX_VALUE") convertTo Field("kotlin.Double.Companion.MAX_VALUE") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Double.POSITIVE_INFINITY") convertTo Field("kotlin.Double.Companion.POSITIVE_INFINITY") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Double.NEGATIVE_INFINITY") convertTo Field("kotlin.Double.Companion.NEGATIVE_INFINITY") withReplaceType REPLACE_WITH_QUALIFIER,
        Field("java.lang.Double.NaN") convertTo Field("kotlin.Double.Companion.NaN") withReplaceType REPLACE_WITH_QUALIFIER,

        Method("java.lang.Character.toUpperCase") convertTo ExtensionMethod("kotlin.text.uppercaseChar") sinceKotlin ApiVersion.KOTLIN_1_5
                withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Character.toLowerCase") convertTo ExtensionMethod("kotlin.text.lowercaseChar") sinceKotlin ApiVersion.KOTLIN_1_5
                withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Character.toTitleCase") convertTo ExtensionMethod("kotlin.text.titlecaseChar") sinceKotlin ApiVersion.KOTLIN_1_5
                withReplaceType REPLACE_WITH_QUALIFIER,

        Method("java.lang.Character.digit") convertTo CustomExpression { expression ->
            val arguments = (expression as JKCallExpression).arguments.arguments
            if (arguments.size != 2) return@CustomExpression expression

            val digit = arguments[0]::value.detached()
            val radix = arguments[1]::value.detached()
            val argumentList = if (radix is JKLiteralExpression && radix.literal == "10") JKArgumentList() else JKArgumentList(radix)
            JKBinaryExpression(
                JKQualifiedExpression(
                    digit,
                    JKCallExpressionImpl(
                        symbolProvider.provideMethodSymbol("kotlin.text.digitToIntOrNull"),
                        argumentList
                    )
                ),
                JKLiteralExpression("-1", JKLiteralExpression.LiteralType.INT),
                JKKtOperatorImpl(JKOperatorToken.ELVIS, typeFactory.types.int)
            )
        }
                sinceKotlin ApiVersion.KOTLIN_1_5
                withReplaceType REPLACE_WITH_QUALIFIER,
    )

    private val objectConversions: List<Conversion> = listOf(
        Method("java.lang.Object.getClass") convertTo Field("kotlin.jvm.javaClass"),
        Method("java.lang.Object.notify") convertTo castReceiverToJavaLangObject() withReplaceType REPLACE_WITH_QUALIFIER
                withFilter ::filterReceiverCastToJavaLangObject,
        Method("java.lang.Object.notifyAll") convertTo castReceiverToJavaLangObject() withReplaceType REPLACE_WITH_QUALIFIER
                withFilter ::filterReceiverCastToJavaLangObject,
        Method("java.lang.Object.wait") convertTo castReceiverToJavaLangObject() withReplaceType REPLACE_WITH_QUALIFIER
                withFilter ::filterReceiverCastToJavaLangObject,
    )

    private val collectionConversions: List<Conversion> = listOf(
        Method("java.util.Iterable.forEach") convertTo Method("kotlin.collections.Iterable.forEach", canMoveLambdaOutsideParentheses = true),

        Method("java.util.Map.entrySet") convertTo Field("kotlin.collections.Map.entries"),
        Method("java.util.Map.keySet") convertTo Field("kotlin.collections.Map.keys"),
        Method("java.util.Map.size") convertTo Field("kotlin.collections.Map.size"),
        Method("java.util.Map.values") convertTo Field("kotlin.collections.Map.values"),
        Method("java.util.Map.compute") convertTo Method("java.util.Map.compute", canMoveLambdaOutsideParentheses = true),
        Method("java.util.Map.computeIfAbsent") convertTo Method("java.util.Map.computeIfAbsent", canMoveLambdaOutsideParentheses = true),
        Method("java.util.Map.computeIfPresent") convertTo Method("java.util.Map.computeIfPresent", canMoveLambdaOutsideParentheses = true),
        Method("java.util.Map.merge") convertTo Method("java.util.Map.merge", canMoveLambdaOutsideParentheses = true),
        Method("java.util.Map.replaceAll") convertTo Method("java.util.Map.replaceAll", canMoveLambdaOutsideParentheses = true),
        Method("java.util.Map.forEach") convertTo CustomExpression { expression ->
            val forEachExpression = expression as JKCallExpression
            val lambdaArgument = forEachExpression.arguments.arguments.singleOrNull()?.value?.safeAs<JKLambdaExpression>()
                ?: return@CustomExpression expression
            if (lambdaArgument.parameters.size != 2) return@CustomExpression expression

            // Analogous to changes made by `JavaMapForEachInspection`
            JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.collections.Map.forEach"),
                JKArgumentList(
                    JKLambdaExpression(
                        lambdaArgument::statement.detached(),
                        listOf(
                            JKKtDestructuringDeclaration(
                                lambdaArgument.parameters.map {
                                    JKKtDestructuringDeclarationEntry(it::type.detached(), it::name.detached())
                                }
                            )
                        )
                    )
                ),
                canMoveLambdaOutsideParentheses = true
            )
        },

        Method("java.util.Collection.size") convertTo Field("kotlin.collections.Collection.size"),
        Method("java.util.Collection.remove") convertTo Method("kotlin.collections.MutableCollection.remove"),
        Method("java.util.Collection.removeIf") convertTo Method("kotlin.collections.MutableCollection.removeIf", canMoveLambdaOutsideParentheses = true),
        Method("java.util.Collection.toArray") convertTo Method("kotlin.collections.toTypedArray") withByArgumentsFilter { it.isEmpty() },
        Method("java.util.Collection.toArray") convertTo Method("kotlin.collections.toTypedArray") withByArgumentsFilter {
            it.singleOrNull()?.let { parameter ->
                parameter.safeAs<JKCallExpression>()?.identifier?.fqName == "kotlin.arrayOfNulls"
            } == true
        } withArgumentsProvider { JKArgumentList() },

        Method("java.util.List.remove") convertTo Method("kotlin.collections.MutableCollection.removeAt"),
        Method("java.util.List.replaceAll") convertTo Method("kotlin.collections.MutableCollection.replaceAll", canMoveLambdaOutsideParentheses = true),
        Method("java.util.Map.Entry.getKey") convertTo Field("kotlin.collections.Map.Entry.key"),
        Method("java.util.Map.Entry.getValue") convertTo Field("kotlin.collections.Map.Entry.value"),

        Method("java.util.Set.of") convertTo Method(setOfFqName()) withReplaceType REPLACE_WITH_QUALIFIER withByArgumentsFilter ::setOfFilter,
        Method("java.util.List.of") convertTo Method(listOfFqName()) withReplaceType REPLACE_WITH_QUALIFIER withByArgumentsFilter ::listOfFilter,
        Method("java.util.Collections.singletonList") convertTo Method(listOfFqName()) withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.util.Collections.singleton") convertTo Method(setOfFqName()) withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.util.Collections.emptyList") convertTo Method(emptyListFqName()) withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.util.Collections.emptySet") convertTo Method(emptySetFqName()) withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.util.Collections.emptyMap") convertTo Method(emptyMapFqName()) withReplaceType REPLACE_WITH_QUALIFIER,
    )

    // For now, these conversions only mark that the last lambda can be moved outside parentheses
    private val streamConversions: List<Conversion> = listOf(
        Method("java.util.stream.Stream.allMatch") convertTo Method("java.util.stream.Stream.allMatch", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.anyMatch") convertTo Method("java.util.stream.Stream.anyMatch", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.collect") convertTo Method("java.util.stream.Stream.collect", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.dropWhile") convertTo Method("java.util.stream.Stream.dropWhile", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.filter") convertTo Method("java.util.stream.Stream.filter", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.flatMap") convertTo Method("java.util.stream.Stream.flatMap", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.flatMapToDouble") convertTo Method("java.util.stream.Stream.flatMapToDouble", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.flatMapToInt") convertTo Method("java.util.stream.Stream.flatMapToInt", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.flatMapToLong") convertTo Method("java.util.stream.Stream.flatMapToLong", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.forEach") convertTo Method("java.util.stream.Stream.forEach", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.forEachOrdered") convertTo Method("java.util.stream.Stream.forEachOrdered", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.generate") convertTo Method("java.util.stream.Stream.generate", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.iterate") convertTo Method("java.util.stream.Stream.iterate", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.map") convertTo Method("java.util.stream.Stream.map", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.mapMulti") convertTo Method("java.util.stream.Stream.mapMulti", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.mapMultiToDouble") convertTo Method("java.util.stream.Stream.mapMultiToDouble", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.mapMultiToInt") convertTo Method("java.util.stream.Stream.mapMultiToInt", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.mapMultiToLong") convertTo Method("java.util.stream.Stream.mapMultiToLong", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.mapToDouble") convertTo Method("java.util.stream.Stream.mapToDouble", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.mapToInt") convertTo Method("java.util.stream.Stream.mapToInt", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.mapToLong") convertTo Method("java.util.stream.Stream.mapToLong", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.noneMatch") convertTo Method("java.util.stream.Stream.noneMatch", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.peek") convertTo Method("java.util.stream.Stream.peek", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.reduce") convertTo Method("java.util.stream.Stream.reduce", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.sorted") convertTo Method("java.util.stream.Stream.sorted", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.takeWhile") convertTo Method("java.util.stream.Stream.takeWhile", canMoveLambdaOutsideParentheses = true),
        Method("java.util.stream.Stream.toArray") convertTo Method("java.util.stream.Stream.toArray", canMoveLambdaOutsideParentheses = true),
    )

    private val enumConversions: List<Conversion> = listOf(
        Method("java.lang.Enum.name") convertTo Field("kotlin.Enum.name"),
        Method("java.lang.Enum.ordinal") convertTo Field("kotlin.Enum.ordinal"),
        Method("java.lang.Enum.valueOf") convertTo CustomExpression { expression ->
            val arguments = (expression as JKCallExpression).arguments.arguments
            if (arguments.size != 2) return@CustomExpression expression
            val classLiteral = arguments[0]::value.detached() as? JKClassLiteralExpression ?: return@CustomExpression expression
            val enumEntryName = arguments[1]::value.detached()
            val typeElement = classLiteral::classType.detached()
            JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.enumValueOf"),
                JKArgumentList(enumEntryName),
                JKTypeArgumentList(typeElement),
                typeElement.type
            )
        } withReplaceType REPLACE_WITH_QUALIFIER,
    )

    private val throwableConversions: List<Conversion> = listOf(
        Method("java.lang.Throwable.getCause") convertTo Field("kotlin.Throwable.cause"),
        Method("java.lang.Throwable.getMessage") convertTo Field("kotlin.Throwable.message"),
    )

    private val stringConversions: List<Conversion> = listOf(
        Method("java.lang.CharSequence.length") convertTo Field("kotlin.String.length"),
        Method("java.lang.CharSequence.charAt") convertTo Method("kotlin.String.get"),
        Method("java.lang.String.strip") convertTo Method("kotlin.text.trim") withByArgumentsFilter { it.isEmpty() },
        Method("java.lang.String.stripLeading") convertTo Method("kotlin.text.trimStart") withByArgumentsFilter { it.isEmpty() },
        Method("java.lang.String.stripTrailing") convertTo Method("kotlin.text.trimEnd") withByArgumentsFilter { it.isEmpty() },

        Method("java.lang.String.indexOf")
                convertTo Method("kotlin.text.indexOf", parameterTypesFqNames = listOf("kotlin.String", "kotlin.Int", "kotlin.Boolean"))
                withByArgumentsFilter { it.firstOrNull()?.calculateType(typeFactory)?.isStringType() == true },
        Method("java.lang.String.indexOf")
                convertTo Method("kotlin.text.indexOf", parameterTypesFqNames = listOf("kotlin.Char", "kotlin.Int", "kotlin.Boolean"))
                withByArgumentsFilter { it.firstOrNull()?.calculateType(typeFactory)?.isStringType() == false },

        Method("java.lang.String.lastIndexOf")
                convertTo Method("kotlin.text.lastIndexOf", parameterTypesFqNames = listOf("kotlin.String", "kotlin.Int", "kotlin.Boolean"))
                withByArgumentsFilter { it.firstOrNull()?.calculateType(typeFactory)?.isStringType() == true },
        Method("java.lang.String.lastIndexOf")
                convertTo Method("kotlin.text.lastIndexOf", parameterTypesFqNames = listOf("kotlin.Char", "kotlin.Int", "kotlin.Boolean"))
                withByArgumentsFilter { it.firstOrNull()?.calculateType(typeFactory)?.isStringType() == false },

        Method("java.lang.String.getBytes") convertTo Method("kotlin.text.toByteArray")
                withByArgumentsFilter { it.singleOrNull()?.calculateType(typeFactory)?.isStringType() == true }
                withArgumentsProvider { arguments ->
            val argument = arguments.arguments.single()::value.detached()
            val call = JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.text.charset"),
                JKArgumentList(argument)
            )
            JKArgumentList(call)
        },
        Method("java.lang.String.getBytes") convertTo Method("kotlin.text.toByteArray"),
        Method("java.lang.String.valueOf") convertTo ExtensionMethod("kotlin.Any.toString") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.isNotEmpty() && it.first().calculateType(typeFactory)?.isArrayType() == false },

        Method("java.lang.String.getChars") convertTo Method("kotlin.text.toCharArray") withByArgumentsFilter { it.size == 4 }
                withArgumentsProvider { argumentList ->
            val srcBeginArgument = argumentList.arguments[0]::value.detached()
            val srcEndArgument = argumentList.arguments[1]::value.detached()
            val dstArgument = argumentList.arguments[2]::value.detached()
            val dstBeginArgument = argumentList.arguments[3]::value.detached()
            JKArgumentList(dstArgument, dstBeginArgument, srcBeginArgument, srcEndArgument)
        },
        Method("java.lang.String.valueOf") convertTo Method("kotlin.String") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.isNotEmpty() && it.first().calculateType(typeFactory)?.isArrayType() == true },
        Method("java.lang.String.copyValueOf") convertTo Method("kotlin.String") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.isNotEmpty() && it.first().calculateType(typeFactory)?.isArrayType() == true },
        Method("java.lang.String.replaceAll") convertTo Method("kotlin.text.replace") withArgumentsProvider ::convertFirstArgumentToRegex,
        Method("java.lang.String.replaceFirst") convertTo Method("kotlin.text.replaceFirst") withArgumentsProvider ::convertFirstArgumentToRegex,
        Method("java.lang.String.equalsIgnoreCase") convertTo Method("kotlin.text.equals") withArgumentsProvider { arguments ->
            JKArgumentList(
                arguments::arguments.detached() + JKNamedArgument(
                    JKLiteralExpression("true", JKLiteralExpression.LiteralType.BOOLEAN),
                    JKNameIdentifier("ignoreCase")
                )
            )
        },

        Method("java.lang.String.toUpperCase") convertTo Method("kotlin.text.uppercase") sinceKotlin ApiVersion.KOTLIN_1_5
                withArgumentsProvider ::stringConversionArgumentsProvider,
        Method("java.lang.String.toLowerCase") convertTo Method("kotlin.text.lowercase") sinceKotlin ApiVersion.KOTLIN_1_5
                withArgumentsProvider ::stringConversionArgumentsProvider,
        Method("java.lang.String.compareToIgnoreCase") convertTo Method("kotlin.text.compareTo") withArgumentsProvider { arguments ->
            JKArgumentList(
                arguments::arguments.detached() + JKNamedArgument(
                    JKLiteralExpression("true", JKLiteralExpression.LiteralType.BOOLEAN),
                    JKNameIdentifier("ignoreCase")
                )
            )
        },
        Method("java.lang.String.matches") convertTo Method("kotlin.text.matches") withArgumentsProvider ::convertFirstArgumentToRegex,
        Method("java.lang.String.regionMatches") convertTo Method("kotlin.text.regionMatches") withByArgumentsFilter { it.size == 5 }
                withArgumentsProvider { arguments ->
            val detachedArguments = arguments::arguments.detached()
            JKArgumentList(
                detachedArguments.drop(1) + JKNamedArgument(
                    detachedArguments.first()::value.detached().also {
                        it.clearFormatting()
                    },
                    JKNameIdentifier("ignoreCase")
                )
            )
        },

        Method("java.lang.String.concat") convertTo CustomExpression { expression ->
            if (expression !is JKCallExpression) error("Expression should be JKCallExpression")
            val parent = expression.parent.cast<JKQualifiedExpression>()
            val firstArgument = parent::receiver.detached()
            val secondArgument = expression.arguments.arguments.first()::value.detached()

            // Drop the line break to avoid awkward formatting of binary expression with operator on next line
            firstArgument.lineBreaksAfter = 0

            JKBinaryExpression(
                firstArgument,
                secondArgument,
                JKKtOperatorImpl(JKOperatorToken.PLUS, typeFactory.types.string)
            ).parenthesize().withFormattingFrom(parent)
        } withReplaceType REPLACE_WITH_QUALIFIER,

        // We request the `split` function with the exact signature `split(regex: Regex, limit: Int = 0)`
        // (see `JKSymbolProvider.provideMethodSymbolWithExactSignature`).
        // Otherwise, `JKSymbolProvider` might choose a `split` overload with different parameters
        // and `ImplicitCastsConversion` downstream will try to insert a nonsensical cast
        // of the `limit` argument to Boolean.
        Method("java.lang.String.split") convertTo Method(
            "kotlin.text.split",
            listOf("kotlin.text.Regex", "kotlin.Int")
        ) andAfter { expression ->
            val arguments = expression.cast<JKQualifiedExpression>().selector.cast<JKCallExpression>().arguments
            val patternArgument = arguments.arguments.first()::value.detached().callOn(
                symbolProvider.provideMethodSymbol("kotlin.text.toRegex")
            )
            val limit: Int? = if (arguments.arguments.size == 2) {
                arguments.arguments.last().value.asLiteralTextWithPrefix()?.toIntOrNull()
            } else {
                0
            }

            arguments.arguments = when {
                limit == null -> {
                    // limit is not a constant, need to make it non-negative
                    val limitArgument = arguments.arguments.last()::value.detached().callOn(
                        symbolProvider.provideMethodSymbol("kotlin.ranges.coerceAtLeast"),
                        listOf(JKLiteralExpression("0", JKLiteralExpression.LiteralType.INT))
                    )
                    listOf(JKArgumentImpl(patternArgument), JKArgumentImpl(limitArgument))
                }

                limit <= 0 -> {
                    // negative: same behavior as split(regex) in Kotlin
                    // zero or absent limit: cases are equivalent in Kotlin
                    listOf(JKArgumentImpl(patternArgument))
                }

                else -> {
                    // positive: same behavior as split(regex, limit) in Kotlin
                    val limitArgument = arguments.arguments.last()::value.detached()
                    listOf(
                        JKArgumentImpl(patternArgument),
                        JKNamedArgument(limitArgument, JKNameIdentifier("limit"))
                    )
                }
            }

            return@andAfter if (limit == 0) {
                // zero or absent limit: discard trailing empty strings to match Java behavior
                expression.callOn(
                    symbolProvider.provideMethodSymbol("kotlin.collections.dropLastWhile"),
                    listOf(
                        JKLambdaExpression(
                            JKKtItExpression(typeFactory.types.string).callOn(
                                symbolProvider.provideMethodSymbol("kotlin.text.isEmpty")
                            ).asStatement()
                        )
                    ),
                    canMoveLambdaOutsideParentheses = true
                )
            } else {
                expression
            }.castToTypedArray()
        },

        Method("java.lang.String.trim") convertTo Method("kotlin.text.trim", canMoveLambdaOutsideParentheses = true) withArgumentsProvider {
            JKArgumentList(
                JKLambdaExpression(
                    JKExpressionStatement(
                        JKBinaryExpression(
                            //TODO replace with `it` parameter
                            JKFieldAccessExpression(
                                JKUnresolvedField(
                                    StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier,
                                    typeFactory
                                )
                            ),
                            JKLiteralExpression("' '", JKLiteralExpression.LiteralType.CHAR),
                            JKKtOperatorImpl(JKOperatorToken.LTEQ, typeFactory.types.boolean)
                        )
                    )
                )
            )
        },
        Method("java.lang.String.format") convertTo CustomExpression { expression ->
            JKClassAccessExpression(symbolProvider.provideClassSymbol(StandardNames.FqNames.string))
                .callOn(
                    symbolProvider.provideMethodSymbol("kotlin.text.String.format"),
                    (expression as JKCallExpression).arguments::arguments.detached()
                )
        } withReplaceType REPLACE_WITH_QUALIFIER,

        Method("java.lang.String.lines") convertTo CustomExpression { expression ->
            if (expression !is JKCallExpression) error("Expression should be JKCallExpression")
            val parent = expression.parent.cast<JKQualifiedExpression>()

            parent::receiver.detached()
                .callOn(symbolProvider.provideMethodSymbol("kotlin.text.lineSequence"))
                .callOn(symbolProvider.provideMethodSymbol("kotlin.streams.asStream"))
                .withFormattingFrom(parent)
        } withReplaceType REPLACE_WITH_QUALIFIER,

        // It is the constructor of "kotlin.String" and not "java.lang.String" because
        // java.lang.String was already converted to kotlin.String in a previous TypeMappingConversion
        NewExpression("kotlin.String") convertTo CustomExpression { newExpression ->
            if (newExpression !is JKNewExpression) throw IllegalStateException()

            fun stringFactoryFunctionCall(newArguments: List<JKArgument> = newExpression.arguments::arguments.detached()) =
                JKCallExpressionImpl(symbolProvider.provideMethodSymbol("kotlin.text.String"), JKArgumentList(newArguments))

            val arguments = newExpression.arguments.arguments
            return@CustomExpression when (arguments.size) {
                // `new String()` is the same as a literal empty string ""
                0 -> JKLiteralExpression("\"\"", type = STRING)

                1 -> {
                    val first = arguments.first().value
                    if (first.calculateType(typeFactory)?.isStringType() == true) {
                        first.detach(arguments.first())
                        if (first is JKLiteralExpression) {
                            // `new String("str")` is the same a literal string "str"
                            first
                        } else {
                            // Explicit String copy constructor call may be needed to optimize memory consumption
                            // See https://stackoverflow.com/a/1803312/992380
                            JKTypeCastExpression(
                                JKNewExpression(symbolProvider.provideClassSymbol("java.lang.String"), JKArgumentList(first)),
                                JKTypeElement(typeFactory.types.string)
                            ).parenthesize()
                        }
                    } else {
                        stringFactoryFunctionCall()
                    }
                }

                else -> {
                    val last = arguments.last().value
                    if ((arguments.size == 2 || arguments.size == 4) && last.calculateType(typeFactory)?.isStringType() == true) {
                        // the last argument is a charset in a string form, we need to convert it to a Charset object
                        val detachedArguments = newExpression.arguments::arguments.detached()
                        last.detach(arguments.last())
                        val charsetCall = JKCallExpressionImpl(
                            symbolProvider.provideMethodSymbol("kotlin.text.charset"),
                            JKArgumentList(last)
                        )
                        val newArguments = detachedArguments.dropLast(1) + listOf(JKArgumentImpl(charsetCall))
                        stringFactoryFunctionCall(newArguments)
                    } else {
                        stringFactoryFunctionCall()
                    }
                }
            }
        },
    )

    private val arrayConversions: List<Conversion> = listOf(
        Method("java.util.Arrays.copyOf") convertTo ExtensionMethod("kotlin.collections.copyOf") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size == 2 },
        Method("java.util.Arrays.equals") convertTo ExtensionMethod("kotlin.collections.contentEquals") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size == 2 },
        Method("java.util.Arrays.deepEquals") convertTo ExtensionMethod("kotlin.collections.contentDeepEquals") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size == 2 },
        Method("java.util.Arrays.hashCode") convertTo ExtensionMethod("kotlin.collections.contentHashCode") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size == 1 },
        Method("java.util.Arrays.deepHashCode") convertTo ExtensionMethod("kotlin.collections.contentDeepHashCode") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size == 1 },
        Method("java.util.Arrays.toString") convertTo ExtensionMethod("kotlin.collections.contentToString") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size == 1 },
        Method("java.util.Arrays.deepToString") convertTo ExtensionMethod("kotlin.collections.contentDeepToString") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter { it.size == 1 },
        Method("java.util.Arrays.asList") convertTo Method("kotlin.collections.mutableListOf") withReplaceType REPLACE_WITH_QUALIFIER
                withByArgumentsFilter ::asListFilter,
    )

    private val printlnConversions: List<Conversion> = listOf(
        Method("java.io.PrintStream.println") convertTo Method("kotlin.io.println") withReplaceType REPLACE_WITH_QUALIFIER withFilter ::isSystemOutCall,
        Method("java.io.PrintStream.print") convertTo Method("kotlin.io.print") withReplaceType REPLACE_WITH_QUALIFIER withFilter ::isSystemOutCall
    )

    private val mathConversions: List<Conversion> = listOf(
        Method("java.lang.Math.abs") convertTo Method("kotlin.math.abs") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.acos") convertTo Method("kotlin.math.acos") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.asin") convertTo Method("kotlin.math.asin") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.atan") convertTo Method("kotlin.math.atan") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.atan2") convertTo Method("kotlin.math.atan2") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.cbrt") convertTo Method("kotlin.math.cbrt") withReplaceType REPLACE_WITH_QUALIFIER sinceKotlin KOTLIN_1_8,
        Method("java.lang.Math.ceil") convertTo Method("kotlin.math.ceil") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.cos") convertTo Method("kotlin.math.cos") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.cosh") convertTo Method("kotlin.math.cosh") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.exp") convertTo Method("kotlin.math.exp") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.expm1") convertTo Method("kotlin.math.expm1") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.floor") convertTo Method("kotlin.math.floor") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.hypot") convertTo Method("kotlin.math.hypot") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.IEEEremainder") convertTo ExtensionMethod("kotlin.math.IEEErem") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.log") convertTo Method("kotlin.math.ln") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.log1p") convertTo Method("kotlin.math.ln1p") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.log10") convertTo Method("kotlin.math.log10") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.max") convertTo Method("kotlin.math.max") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.min") convertTo Method("kotlin.math.min") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.nextDown") convertTo ExtensionMethod("kotlin.math.nextDown") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.nextAfter") convertTo ExtensionMethod("kotlin.math.nextTowards") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.nextUp") convertTo ExtensionMethod("kotlin.math.nextUp") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.pow") convertTo ExtensionMethod("kotlin.math.pow") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.rint") convertTo Method("kotlin.math.round") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.signum") convertTo Method("kotlin.math.sign") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.sin") convertTo Method("kotlin.math.sin") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.sinh") convertTo Method("kotlin.math.sinh") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.sqrt") convertTo Method("kotlin.math.sqrt") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.tan") convertTo Method("kotlin.math.tan") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.tanh") convertTo Method("kotlin.math.tanh") withReplaceType REPLACE_WITH_QUALIFIER,
        Method("java.lang.Math.copySign") convertTo ExtensionMethod("kotlin.math.withSign") withReplaceType REPLACE_WITH_QUALIFIER,
    )

    private fun primitiveToStringWithRadix(): CustomExpression = CustomExpression { expression ->
        val arguments = (expression as JKCallExpression).arguments::arguments.detached()
        val receiver = arguments[0]::value.detached()
        val radix = arguments[1]::value.detached()

        val newRadix = if (radix is JKLiteralExpression) {
            val radixValue = radix.literal.toInt().coerceIn(2, 36)
            JKLiteralExpression(radixValue.toString(), INT)
        } else {
            radix.callOn(
                symbolProvider.provideMethodSymbol("kotlin.ranges.coerceIn"),
                listOf(JKLiteralExpression("2", INT), JKLiteralExpression("36", INT))
            )
        }
        val newArguments = listOf(JKArgumentImpl(newRadix))
        receiver.callOn(symbolProvider.provideMethodSymbol("kotlin.text.toString"), newArguments).withFormattingFrom(expression)
    }

    private fun primitiveToStringWithRadixArgumentsFilter(arguments: List<JKExpression>): Boolean {
        if (arguments.size != 2) return false
        val radix = arguments[1]
        if (radix !is JKLiteralExpression) return true
        return radix.literal.toIntOrNull() != null
    }

    private fun castReceiverToJavaLangObject(): CustomExpression = CustomExpression { expr ->
        val parent = expr.parent ?: return@CustomExpression expr
        val (receiver, selector) = if (parent is JKQualifiedExpression) {
            parent.receiver.detach(parent)
            parent.selector.detach(parent)
            parent.receiver to parent.selector
        } else {
            JKThisExpression(JKLabelEmpty()) to expr.copyTreeAndDetach()
        }

        val objectTypeElement = JKTypeElement(JKClassType(symbolProvider.provideClassSymbol("java.lang.Object"), nullability = NotNull))
        val existingCast = (receiver as? JKParenthesizedExpression)?.expression as? JKTypeCastExpression

        return@CustomExpression if ((existingCast?.type?.type as? JKClassType)?.classReference?.fqName == "kotlin.Any") {
            // If we already have a cast to Any, replace "Any" with "Object" and don't produce a double cast
            existingCast.type = objectTypeElement
            JKQualifiedExpression(receiver, selector)
        } else {
            val cast = JKTypeCastExpression(receiver, objectTypeElement).parenthesize()
            JKQualifiedExpression(cast, selector)
        }
    }

    private fun filterReceiverCastToJavaLangObject(expr: JKExpression): Boolean {
        val parent = expr.parent as? JKQualifiedExpression ?: return true
        val receiver = parent.receiver as? JKParenthesizedExpression ?: return true
        val cast = receiver.expression as? JKTypeCastExpression ?: return true
        return (cast.type.type as? JKClassType)?.classReference?.fqName != "java.lang.Object"
    }

    private fun valueOfReplacement(): CustomExpression = CustomExpression { expression ->
        val arguments = (expression as JKCallExpression).arguments
        if (arguments.arguments.isEmpty()) return@CustomExpression expression
        val detachedArguments = arguments::arguments.detached()

        val firstArgument = detachedArguments[0]::value.detached()
        // Extract simple name of numeric class (for example, "Short" from "java.lang.Short.valueOf")
        val typeName = expression.identifier.fqName.split(".").dropLast(1).lastOrNull()?.let {
            if (it == "Integer") "Int" else it
        }
        val result = if (firstArgument.calculateType(typeFactory)?.isStringType() == true) {
            firstArgument.callOn(
                symbolProvider.provideMethodSymbol("kotlin.text.String.to$typeName"),
                detachedArguments.drop(1)
            )
        } else {
            // this is a call like `Short.valueOf(short)`, which can be converted to just `short`
            firstArgument
        }

        result.withFormattingFrom(expression)
    }

    private fun convertFirstArgumentToRegex(arguments: JKArgumentList): JKArgumentList {
        val detachedArguments = arguments::arguments.detached()
        val first =
            detachedArguments.first()::value.detached().callOn(
                symbolProvider.provideMethodSymbol("kotlin.text.toRegex")
            )
        return JKArgumentList(listOf(JKArgumentImpl(first)) + detachedArguments.drop(1))
    }

    private fun JKExpression.callOn(symbol: JKMethodSymbol, arguments: List<JKArgument> = emptyList()) =
        JKQualifiedExpression(
            this.parenthesizeIfCompoundExpression(),
            JKCallExpressionImpl(symbol, JKArgumentList(arguments), JKTypeArgumentList())
        )

    private fun isSystemOutCall(expression: JKExpression): Boolean =
        expression.parent
            ?.safeAs<JKQualifiedExpression>()
            ?.receiver
            ?.let { receiver ->
                when (receiver) {
                    is JKFieldAccessExpression -> receiver
                    is JKQualifiedExpression -> receiver.selector as? JKFieldAccessExpression
                    else -> null
                }
            }?.identifier
            ?.deepestFqName() == "java.lang.System.out"

    private fun JKExpression.castToTypedArray(): JKQualifiedExpression =
        callOn(symbolProvider.provideMethodSymbol("kotlin.collections.toTypedArray"))

    private fun stringConversionArgumentsProvider(arguments: JKArgumentList): JKArgumentList {
        if (arguments.arguments.isEmpty()) {
            return JKArgumentList(
                JKArgumentImpl(
                    JKClassAccessExpression(symbolProvider.provideClassSymbol("java.util.Locale")).callOn(
                        symbolProvider.provideMethodSymbol("java.util.Locale.getDefault")
                    )
                )
            )
        }

        val singleArgument = arguments.arguments.singleOrNull() ?: return arguments
        val expression = (singleArgument.value as? JKQualifiedExpression) ?: return arguments

        if (expression.selector.identifier?.fqName in neutralLocaleFQNames) return JKArgumentList()
        return arguments
    }

    @Suppress("UNCHECKED_CAST")
    private fun listOfFilter(arguments: List<JKExpression>): Boolean =
        arguments.containsOnlyLiterals() && !(arguments as List<JKLiteralExpression>).containsNull()

    @Suppress("UNCHECKED_CAST")
    private fun asListFilter(arguments: List<JKExpression>): Boolean =
        arguments.containsOnlyLiterals() &&
                (arguments.size > 1 || !(arguments as List<JKLiteralExpression>).containsNull())

    @Suppress("UNCHECKED_CAST")
    private fun setOfFilter(arguments: List<JKExpression>): Boolean =
        arguments.containsOnlyLiterals() &&
                (arguments as List<JKLiteralExpression>).containsOnlyUnique()
                && !arguments.containsNull()

    private fun List<JKExpression>.containsOnlyLiterals(): Boolean =
        all { it is JKLiteralExpression }

    private fun List<JKLiteralExpression>.containsOnlyUnique(): Boolean =
        size == distinctBy { it.literal }.size

    private fun List<JKLiteralExpression>.containsNull(): Boolean =
        any { it.isNull() }

    // TODO: before mutability inference is implemented in K2 (KTIJ-29148),
    //   use mutable versions of collection creation functions

    private fun setOfFqName(): String =
        if (isK1Mode()) "kotlin.collections.setOf" else "kotlin.collections.mutableSetOf"

    private fun listOfFqName(): String =
        if (isK1Mode()) "kotlin.collections.listOf" else "kotlin.collections.mutableListOf"

    private fun emptyListFqName(): String =
        if (isK1Mode()) "kotlin.collections.emptyList" else "kotlin.collections.mutableListOf"

    private fun emptySetFqName(): String =
        if (isK1Mode()) "kotlin.collections.emptySet" else "kotlin.collections.mutableSetOf"

    private fun emptyMapFqName(): String =
        if (isK1Mode()) "kotlin.collections.emptyMap" else "kotlin.collections.mutableMapOf"
}
