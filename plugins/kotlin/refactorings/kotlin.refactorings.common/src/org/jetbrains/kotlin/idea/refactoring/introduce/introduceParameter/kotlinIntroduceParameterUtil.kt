// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.introduce.*
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*


fun selectNewParameterContext(
    editor: Editor,
    file: KtFile,
    continuation: (elements: List<PsiElement>, targetParent: PsiElement) -> Unit
) {
    selectElementsWithTargetParent(
        operationName = INTRODUCE_PARAMETER,
        editor = editor,
        file = file,
        title = KotlinBundle.message("title.introduce.parameter.to.declaration"),
        elementKinds = listOf(ElementKind.EXPRESSION),
        elementValidator = ::validateExpressionElements,
        getContainers = { _, parent ->
            val parents = parent.parents
            val stopAt = (parent.parents.zip(parent.parents.drop(1)))
                .firstOrNull { isObjectOrNonInnerClass(it.first) }
                ?.second

            (if (stopAt != null) parent.parents.takeWhile { it != stopAt } else parents)
                .filter {
                    ((it is KtClass && !it.isInterface() && it !is KtEnumEntry) || it is KtNamedFunction || it is KtSecondaryConstructor) &&
                            ((it as KtNamedDeclaration).getValueParameterList() != null || it.nameIdentifier != null)
                }
                .toList()
        },
        continuation = continuation
    )
}

interface KotlinIntroduceParameterHelper<Descriptor> {
    class Default<D> : KotlinIntroduceParameterHelper<D>

    fun configure(descriptor: IntroduceParameterDescriptor<Descriptor>): IntroduceParameterDescriptor<Descriptor> = descriptor
}

val INTRODUCE_PARAMETER: String
    @Nls
    get() = KotlinBundle.message("name.introduce.parameter1")
val INTRODUCE_LAMBDA_PARAMETER: String
    @Nls
    get() = KotlinBundle.message("name.introduce.lambda.parameter")
