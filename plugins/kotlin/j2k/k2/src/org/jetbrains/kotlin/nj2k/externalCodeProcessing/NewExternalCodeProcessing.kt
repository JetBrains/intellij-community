// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.externalCodeProcessing

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.j2k.ExternalCodeProcessing
import org.jetbrains.kotlin.j2k.ReferenceSearcher
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.fqNameWithoutCompanions
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.JKDeclaration
import org.jetbrains.kotlin.nj2k.types.typeFqName
import org.jetbrains.kotlin.nj2k.types.typeFqNamePossiblyMappedToKotlin
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

internal sealed class J2kMemberKey {
    abstract val fqName: FqName

    data class PhysicalMethodKey(override val fqName: FqName, val parameters: List<FqName>) : J2kMemberKey()
    data class LightMethodKey(override val fqName: FqName) : J2kMemberKey()
    data class FieldKey(override val fqName: FqName) : J2kMemberKey()
}

internal fun J2kMemberKey.toLightMethodKey() = J2kMemberKey.LightMethodKey(fqName)

internal fun JKMemberData.buildMemberKey(): J2kMemberKey? {
    val fqName = this.fqName ?: return null
    return when (this) {
        is JKPhysicalMethodData ->
            J2kMemberKey.PhysicalMethodKey(
                fqName,
                javaElement.parameterList.parameters.mapNotNull { it.typeFqNamePossiblyMappedToKotlin() })

        is JKLightMethodData -> J2kMemberKey.LightMethodKey(fqName)
        else -> J2kMemberKey.FieldKey(fqName)
    }
}

internal fun PsiMember.buildMemberKey(): J2kMemberKey? {
    val fqName = this.kotlinFqName ?: return null
    return when (this) {
        is PsiMethod -> J2kMemberKey.PhysicalMethodKey(
            fqName,
            parameterList.parameters.mapNotNull { it.typeFqNamePossiblyMappedToKotlin() })

        else -> J2kMemberKey.FieldKey(fqName)
    }
}

internal fun PsiMethod.buildLightMethodKey(): J2kMemberKey.LightMethodKey? =
    kotlinFqName?.let(J2kMemberKey::LightMethodKey)

internal fun KtNamedDeclaration.buildMemberKey(): J2kMemberKey = when (this) {
    is KtNamedFunction -> J2kMemberKey.PhysicalMethodKey(this.fqNameWithoutCompanions, this.valueParameters.mapNotNull { it.typeFqName() })
    else -> J2kMemberKey.FieldKey(this.fqNameWithoutCompanions)
}

class OriginalJavaPsiContext internal constructor(
    private val originalMembersByKey: Map<J2kMemberKey, PsiMember>,
    private val originalClassesByQualifiedName: Map<String, PsiClass>,
) {
    fun resolve(member: PsiMember): PsiMember? =
        member.buildMemberKey()?.let(originalMembersByKey::get)
            ?: (member as? PsiMethod)?.buildLightMethodKey()?.let(originalMembersByKey::get)

    fun resolve(psiClass: PsiClass): PsiClass? =
        psiClass.qualifiedName?.let(originalClassesByQualifiedName::get)

    companion object {
        val Empty: OriginalJavaPsiContext = OriginalJavaPsiContext(emptyMap(), emptyMap())
    }
}

class NewExternalCodeProcessing(
    private val referenceSearcher: ReferenceSearcher,
    private val isInConversionContext: (PsiElement) -> Boolean,
    private val originalJavaPsiContext: OriginalJavaPsiContext = OriginalJavaPsiContext.Empty,
) : ExternalCodeProcessing {

    private val members = mutableMapOf<J2kMemberKey, JKMemberData>()

    fun isExternalProcessingNeeded(): Boolean =
        members.values.any { it.searchingNeeded }

    fun addMember(data: JKMemberData) {
        data.buildMemberKey() ?: return
        val key = data.buildMemberKey() ?: return
        members[key] = data
    }

    fun registerField(field: PsiField) {
        val originalField = originalJavaPsiContext.resolve(field) as? PsiField ?: field
        addMember(JKFieldDataFromJava(originalField))
    }

    fun registerPhysicalMethod(method: PsiMethod) {
        val originalMethod = originalJavaPsiContext.resolve(method) as? PsiMethod ?: method
        addMember(JKPhysicalMethodData(originalMethod))
    }

    fun registerLightMethod(method: PsiMethod) {
        val originalMethod = originalJavaPsiContext.resolve(method) as? PsiMethod ?: method
        addMember(JKLightMethodData(originalMethod))
    }

    fun getMember(element: JKDeclaration): JKMemberData? = members[element.psi<PsiMember>()?.buildMemberKey()]

    fun getMember(element: KtNamedDeclaration): JKMemberData? {
        val key = element.buildMemberKey()
        // For Java record classes, we collect usages of light (non-physical) methods that correspond to the record components.
        // In the resulting Kotlin PSI there are no such methods. So, we have to find the registered light method
        // by the primary constructor property that is the Kotlin's version of a Java record component.
        return members[key] ?: members[key.toLightMethodKey()].takeIf { element.isConstructorDeclaredProperty() }
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

    override fun collectUsages(): List<ExternalUsagesFixer.JKMemberInfoWithUsages> {
        return members.values.map { it.collectUsages() }
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
