// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.codeinsight.utils.isAnnotatedDeep
import org.jetbrains.kotlin.idea.codeinsight.utils.isConvertableToExpressionBody
import org.jetbrains.kotlin.idea.codeinsight.utils.replaceWithExpressionBodyPreservingComments
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.util.areTypeArgumentsRedundant
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.psi.*

internal object Generator : ExtractFunctionGenerator<KaType, ExtractionResult>() {
    override val nameGenerator: IExtractionNameSuggester<KaType> = KotlinNameSuggester

    override fun createTypeDescriptor(data: IExtractionData): TypeDescriptor<KaType> {
        return KotlinTypeDescriptor(data)
    }

    override fun IExtractionGeneratorConfiguration<KaType>.collapseBody(
        blockExpression: KtBlockExpression
    ) {
        val declarationWithBody = blockExpression.parent as? KtDeclarationWithBody ?: return

        if (declarationWithBody.isConvertableToExpressionBody()) {
            declarationWithBody.replaceWithExpressionBodyPreservingComments()
        }
    }

    override fun resolveNameConflict(property: KtProperty) {
        //TODO resolve name conflicts for extract property https://youtrack.jetbrains.com/issue/KTIJ-21139
    }

    override fun checkTypeArgumentsAreRedundant(args: KtTypeArgumentList): Boolean {
        return args.arguments.none { it.typeReference?.isAnnotatedDeep() == true } &&
                analyze(args) { areTypeArgumentsRedundant(args, approximateFlexible = false) }
    }

    override fun IExtractionGeneratorConfiguration<KaType>.createExtractionResult(
        declaration: KtNamedDeclaration,
        duplicatesReplacer: Map<KotlinPsiRange, () -> Unit>
    ): ExtractionResult = ExtractionResult(this as ExtractionGeneratorConfiguration, declaration, duplicatesReplacer)
}