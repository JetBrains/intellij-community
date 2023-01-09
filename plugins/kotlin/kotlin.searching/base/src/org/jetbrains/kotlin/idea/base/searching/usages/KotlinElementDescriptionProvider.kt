// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.collapseSpaces
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

open class KotlinElementDescriptionProviderBase : ElementDescriptionProvider {
    private tailrec fun KtNamedDeclaration.parentForFqName(): KtNamedDeclaration? {
        val parent = getStrictParentOfType<KtNamedDeclaration>() ?: return null
        if (parent is KtProperty && parent.isLocal) return parent.parentForFqName()
        return parent
    }

    private fun KtNamedDeclaration.name() = nameAsName ?: Name.special("<no name provided>")

    private fun KtNamedDeclaration.fqName(): FqNameUnsafe {
        containingClassOrObject?.let {
            if (it is KtObjectDeclaration && it.isCompanion()) {
                return it.fqName().child(name())
            }
            return FqNameUnsafe("${it.name()}.${name()}")
        }

        val internalSegments = generateSequence(this) { it.parentForFqName() }
            .filterIsInstance<KtNamedDeclaration>()
            .map { it.name ?: "<no name provided>" }
            .toList()
            .asReversed()
        val packageSegments = containingKtFile.packageFqName.pathSegments()
        return FqNameUnsafe((packageSegments + internalSegments).joinToString("."))
    }

    private fun KtTypeReference.renderShort(): String {
        return accept(
            object : KtVisitor<String, Unit>() {
                private val visitor get() = this

                override fun visitTypeReference(typeReference: KtTypeReference, data: Unit): String {
                    val typeText = typeReference.typeElement?.accept(this, data) ?: "???"
                    return if (typeReference.hasParentheses()) "($typeText)" else typeText
                }

                override fun visitIntersectionType(definitelyNotNullType: KtIntersectionType, data: Unit): String {
                    return buildString {
                        append(definitelyNotNullType.getLeftTypeRef()?.typeElement?.accept(visitor, data) ?: "???")
                        append(" & ")
                        append(definitelyNotNullType.getRightTypeRef()?.typeElement?.accept(visitor, data) ?: "???")
                    }
                }

                override fun visitDynamicType(type: KtDynamicType, data: Unit) = type.text

                override fun visitFunctionType(type: KtFunctionType, data: Unit): String {
                    return buildString {
                        type.receiverTypeReference?.let { append(it.accept(visitor, data)).append('.') }
                        type.parameters.joinTo(this, prefix = "(", postfix = ")") { it.accept(visitor, data) }
                        append(" -> ")
                        append(type.returnTypeReference?.accept(visitor, data) ?: "???")
                    }
                }

                override fun visitNullableType(nullableType: KtNullableType, data: Unit): String {
                    val innerTypeText = nullableType.innerType?.accept(this, data) ?: return "???"
                    return "$innerTypeText?"
                }

                override fun visitSelfType(type: KtSelfType, data: Unit) = type.text

                override fun visitUserType(type: KtUserType, data: Unit): String {
                    return buildString {
                        append(type.referencedName ?: "???")

                        val arguments = type.typeArguments
                        if (arguments.isNotEmpty()) {
                            arguments.joinTo(this, prefix = "<", postfix = ">") {
                                it.typeReference?.accept(visitor, data) ?: it.text
                            }
                        }
                    }
                }

                override fun visitParameter(parameter: KtParameter, data: Unit) = parameter.typeReference?.accept(this, data) ?: "???"
            },
            Unit
        )
    }

    //TODO: Implement in FIR
    protected open val PsiElement.isRenameJavaSyntheticPropertyHandler get() = false
    protected open val PsiElement.isRenameKotlinPropertyProcessor get() = false

    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        val shouldUnwrap = location !is UsageViewShortNameLocation && location !is UsageViewLongNameLocation
        val targetElement = if (shouldUnwrap) element.unwrapped ?: element else element

        fun elementKind() = when (targetElement) {
            is KtClass -> if (targetElement.isInterface())
                KotlinBundle.message("find.usages.interface")
            else
                KotlinBundle.message("find.usages.class")
            is KtObjectDeclaration -> if (targetElement.isCompanion())
                KotlinBundle.message("find.usages.companion.object")
            else
                KotlinBundle.message("find.usages.object")
            is KtNamedFunction -> KotlinBundle.message("find.usages.function")
            is KtPropertyAccessor -> KotlinBundle.message(
                "find.usages.for.property",
                (if (targetElement.isGetter)
                    KotlinBundle.message("find.usages.getter")
                else
                    KotlinBundle.message("find.usages.setter"))
            ) + " "
            is KtFunctionLiteral -> KotlinBundle.message("find.usages.lambda")
            is KtPrimaryConstructor, is KtSecondaryConstructor -> KotlinBundle.message("find.usages.constructor")
            is KtProperty -> if (targetElement.isLocal)
                KotlinBundle.message("find.usages.variable")
            else
                KotlinBundle.message("find.usages.property")
            is KtTypeParameter -> KotlinBundle.message("find.usages.type.parameter")
            is KtParameter -> KotlinBundle.message("find.usages.parameter")
            is KtDestructuringDeclarationEntry -> KotlinBundle.message("find.usages.variable")
            is KtTypeAlias -> KotlinBundle.message("find.usages.type.alias")
            is KtLabeledExpression -> KotlinBundle.message("find.usages.label")
            is KtImportAlias -> KotlinBundle.message("find.usages.import.alias")
            is KtLightClassForFacade -> KotlinBundle.message("find.usages.facade.class")
            else -> {
                //TODO Implement in FIR
                when {
                    targetElement.isRenameJavaSyntheticPropertyHandler -> KotlinBundle.message("find.usages.property")
                    targetElement.isRenameKotlinPropertyProcessor -> KotlinBundle.message("find.usages.property.accessor")
                    else -> null
                }
            }
        }

        val namedElement = if (targetElement is KtPropertyAccessor) {
            targetElement.parent as? KtProperty
        } else targetElement as? PsiNamedElement

        if (namedElement == null) {
            return if (targetElement is KtElement) "'" + StringUtil.shortenTextWithEllipsis(
                targetElement.text.collapseSpaces(),
                53,
                0
            ) + "'" else null
        }

        if (namedElement.language != KotlinLanguage.INSTANCE) return null

        return when (location) {
            is UsageViewTypeLocation -> elementKind()
            is UsageViewShortNameLocation, is UsageViewLongNameLocation -> namedElement.name
            is RefactoringDescriptionLocation -> {
                val kind = elementKind() ?: return null
                if (namedElement !is KtNamedDeclaration) return null
                val renderFqName = location.includeParent() &&
                        namedElement !is KtTypeParameter &&
                        namedElement !is KtParameter &&
                        namedElement !is KtConstructor<*>
                @Suppress("HardCodedStringLiteral")
                val desc = when (namedElement) {
                    is KtFunction -> {
                        val baseText = buildString {
                            append(namedElement.name ?: "")
                            namedElement.valueParameters.joinTo(this, prefix = "(", postfix = ")") {
                                (if (it.isVarArg) "vararg " else "") + (it.typeReference?.renderShort() ?: "")
                            }
                            namedElement.receiverTypeReference?.let { append(" on ").append(it.renderShort()) }
                        }
                        val parentFqName = if (renderFqName) namedElement.fqName().parent() else null
                        if (parentFqName?.isRoot != false) baseText else "${parentFqName.asString()}.$baseText"
                    }
                    else -> (if (renderFqName) namedElement.fqName().asString() else namedElement.name) ?: ""
                }

                "$kind ${CommonRefactoringUtil.htmlEmphasize(desc)}"
            }
            is HighlightUsagesDescriptionLocation -> {
                val kind = elementKind() ?: return null
                if (namedElement !is KtNamedDeclaration) return null
                "$kind ${namedElement.name}"
            }
            else -> null
        }
    }
}
