// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.externalCodeProcessing

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.j2k.ExternalCodeProcessing
import org.jetbrains.kotlin.j2k.ProgressPortionReporter
import org.jetbrains.kotlin.j2k.ReferenceSearcher
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.KotlinNJ2KBundle
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.NewExternalCodeProcessing.MemberKey.*
import org.jetbrains.kotlin.nj2k.fqNameWithoutCompanions
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.JKDeclaration
import org.jetbrains.kotlin.nj2k.types.typeFqName
import org.jetbrains.kotlin.nj2k.types.typeFqNamePossiblyMappedToKotlin
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

class NewExternalCodeProcessing(
    private val referenceSearcher: ReferenceSearcher,
    private val isInConversionContext: (PsiElement) -> Boolean
) : ExternalCodeProcessing {

    private sealed class MemberKey {
        abstract val fqName: FqName

        data class PhysicalMethodKey(override val fqName: FqName, val parameters: List<FqName>) : MemberKey()
        data class LightMethodKey(override val fqName: FqName) : MemberKey()
        data class FieldKey(override val fqName: FqName) : MemberKey()
    }

    private fun MemberKey.toLightMethodKey() = LightMethodKey(fqName)

    private val members = mutableMapOf<MemberKey, JKMemberData>()

    fun isExternalProcessingNeeded(): Boolean =
        members.values.any { it.searchingNeeded }

    context(_: KaSession)
    fun addMember(data: JKMemberData) {
        val key = data.buildKey() ?: return
        members[key] = data
    }

    context(_: KaSession)
    fun getMember(element: JKDeclaration): JKMemberData? = members[element.psi<PsiMember>()?.buildKey()]

    context(_: KaSession)
    fun getMember(element: KtNamedDeclaration): JKMemberData? {
        val key = element.buildKey()
        // For Java record classes, we collect usages of light (non-physical) methods that correspond to the record components.
        // In the resulting Kotlin PSI there are no such methods. So, we have to find the registered light method
        // by the primary constructor property that is the Kotlin's version of a Java record component.
        return members[key] ?: members[key.toLightMethodKey()].takeIf { element.isConstructorDeclaredProperty() }
    }

    context(_: KaSession)
    private fun JKMemberData.buildKey(): MemberKey? {
        val fqName = this.fqName ?: return null
        return when (this) {
            is JKPhysicalMethodData ->
                PhysicalMethodKey(fqName, javaElement.parameterList.parameters.mapNotNull { it.typeFqNamePossiblyMappedToKotlin() })

            is JKLightMethodData -> LightMethodKey(fqName)
            else -> FieldKey(fqName)
        }
    }

    context(_: KaSession)
    private fun PsiMember.buildKey(): MemberKey? {
        val fqName = this.kotlinFqName ?: return null
        return when (this) {
            is PsiMethod -> PhysicalMethodKey(fqName, parameterList.parameters.mapNotNull { it.typeFqNamePossiblyMappedToKotlin() })
            else -> FieldKey(fqName)
        }
    }

    context(_: KaSession)
    private fun KtNamedDeclaration.buildKey() = when (this) {
        is KtNamedFunction -> PhysicalMethodKey(this.fqNameWithoutCompanions, this.valueParameters.mapNotNull { it.typeFqName() })
        else -> FieldKey(this.fqNameWithoutCompanions)
    }

    context(_: KaSession)
    override fun bindJavaDeclarationsToConvertedKotlinOnes(files: List<KtFile>) {
        files.forEach { file ->
            file.forEachDescendantOfType<KtNamedDeclaration> { declaration ->
                if (declaration is KtNamedFunction || declaration is KtProperty || declaration.isConstructorDeclaredProperty()) {
                    val member = getMember(declaration) ?: return@forEachDescendantOfType
                    member.kotlinElementPointer = SmartPointerManager.createPointer(declaration)
                }
            }
        }
    }

    override fun prepareWriteOperation(progress: ProgressIndicator?): () -> Unit {
        progress?.text = KotlinNJ2KBundle.message("progress.searching.usages.to.update")

        val usages = mutableListOf<ExternalUsagesFixer.JKMemberInfoWithUsages>()
        for ((index, member) in members.values.withIndex()) {
            if (progress != null) {
                progress.text2 = member.fqName?.shortName()?.identifier ?: continue
                progress.checkCanceled()

                ProgressManager.getInstance().runProcess(
                    { usages += member.collectUsages() },
                    ProgressPortionReporter(progress, index / members.size.toDouble(), 1.0 / members.size)
                )
            } else {
                usages += member.collectUsages()
            }
        }
        return {
            ExternalUsagesFixer(usages).fix()
        }
    }

    private fun JKMemberData.collectUsages(): ExternalUsagesFixer.JKMemberInfoWithUsages {
        val javaUsages = mutableListOf<PsiElement>()
        val kotlinUsages = mutableListOf<KtElement>()
        if (this is JKMemberDataCameFromJava<*>) referenceSearcher.findUsagesForExternalCodeProcessing(
            javaElement,
            searchJava = searchInJavaFiles,
            searchKotlin = searchInKotlinFiles
        ).forEach { usage ->
            val element = usage.element
            if (isInConversionContext(element)) return@forEach
            when {
                element is KtElement -> kotlinUsages += element
                element.language == JavaLanguage.INSTANCE -> javaUsages += element
            }
        }
        return ExternalUsagesFixer.JKMemberInfoWithUsages(this, javaUsages, kotlinUsages)
    }
}


