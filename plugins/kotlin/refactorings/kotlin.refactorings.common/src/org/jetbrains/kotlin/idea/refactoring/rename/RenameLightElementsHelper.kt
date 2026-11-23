// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.propertyNameByAccessor
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.siblings

internal object RenameLightElementsHelper {
    /**
     * What [renameLightMethod] writes, decided on the code before the write.
     *
     * The logic is the master body of [renameLightMethod], split into a decision ([lightMethodRename]) and a write. The ModPlan rename
     * cannot call [renameLightMethod]: the engine writes on copies, and a light method has no copy, as it is no element of a file. So
     * `ModPlanKotlinRenameProvider` takes the decision on the original code and writes each result as an edit of the Kotlin code.
     * [renameLightMethod] runs the same decision, so the logic is in one place.
     * ```
     * @JvmName("getFoo") fun foo() = 1   // rename the light method getFoo to getBar from Java
     * // after: ReplaceJvmName
     * @JvmName("getBar") fun foo() = 1
     * ```
     */
    sealed interface LightMethodRename {
        /** The [JvmName] annotation goes, as the declaration has the new name. */
        class DeleteJvmName(val annotation: PsiElement) : LightMethodRename

        /** The name of the [JvmName] annotation becomes [name]. */
        class ReplaceJvmName(val nameExpression: KtStringTemplateExpression, val name: String) : LightMethodRename

        /** The declaration gets [name]. */
        class RenameOrigin(val origin: PsiNamedElement, val name: String) : LightMethodRename
    }

    /** @return what the rename of [lightMethod] to [name] writes; null if nothing. See [LightMethodRename]. */
    fun lightMethodRename(lightMethod: KtLightMethod, name: String): LightMethodRename? {
        val jvmNameAnnotation = lightMethod.modifierList.findAnnotation(JvmStandardClassIds.JVM_NAME.asString())
        val demangledName =
            (if (lightMethod.isMangled) KotlinRenameRefactoringSupport.getInstance().demangleInternalName(name) else null) ?: name
        val newNameForOrigin = propertyNameByAccessor(demangledName, lightMethod) ?: demangledName
        if (newNameForOrigin == lightMethod.kotlinOrigin?.name) {
            return jvmNameAnnotation?.let { LightMethodRename.DeleteJvmName(it) }
        }
        val jvmExposeBoxedAnnotation =
            lightMethod.modifierList.findAnnotation(JvmStandardClassIds.JVM_EXPOSE_BOXED_ANNOTATION_FQ_NAME.asString())
        val nameExpression = (jvmNameAnnotation?.findAttributeValue("name")
            ?: jvmExposeBoxedAnnotation?.findAttributeValue("jvmName"))?.unwrapped as? KtStringTemplateExpression
        if (nameExpression != null) return LightMethodRename.ReplaceJvmName(nameExpression, name)
        val toRename = lightMethod.kotlinOrigin as? PsiNamedElement ?: return null
        return LightMethodRename.RenameOrigin(toRename, newNameForOrigin)
    }

    /**
     * This method properly updates [JvmName] annotations on [lightMethod] and removes it in case
     * it's no longer necessary. It also accounts for the mangled names.
     *
     * This method is a copy from [org.jetbrains.kotlin.asJava.elements.KtLightMethodImpl.setName].
     * We use it, so we don't have to call [PsiNamedElement.setName] on [KtLightMethod] elements,
     * since they should not be used to modify code.
     *
     * It takes [KtLightMethod] as an argument to handle annotations and mangled names
     * more easily and without any logic duplication.
     *
     * @param lightMethod A light element for Kotlin declaration needs to be renamed.
     * @param name A new JVM name for that declaration.
     */
    fun renameLightMethod(lightMethod: KtLightMethod, name: String) {
        when (val rename = lightMethodRename(lightMethod, name)) {
            is LightMethodRename.DeleteJvmName -> rename.annotation.delete()
            is LightMethodRename.ReplaceJvmName ->
                rename.nameExpression.replace(KtPsiFactory(lightMethod.project).createStringTemplate(rename.name))
            is LightMethodRename.RenameOrigin -> rename.origin.setName(rename.name)
            null -> {}
        }
    }

    /**
     * What [renameFacadeLightClass] writes in one file of the facade class, decided on the code before the write.
     *
     * The logic is the master body of [renameFacadeLightClass], split into a decision ([facadeFileRename]) and a write, for the reason of
     * [LightMethodRename]. A second reason: [RenameFile] sets the name of the file, which the engine writes as an edit of the file, not of
     * its copy. [renameFacadeLightClass] runs the same decision.
     * ```
     * // utils.kt, no @file:JvmName
     * fun foo() {}   // rename the facade class UtilsKt to HelpersKt from Java
     * // after: RenameFile, the file is Helpers.kt
     * ```
     */
    sealed interface FacadeFileRename {
        /** The [JvmName] annotation of the file goes, as the name of the file gives the new name. */
        class DeleteJvmName(val annotation: PsiElement) : FacadeFileRename

        /** The file gets [newFileName], which gives the new name. */
        class RenameFile(val file: KtFile, val newFileName: String) : FacadeFileRename

        /** The file gets a [JvmName] annotation with the new name. */
        class AddJvmName(val file: KtFile, val name: String) : FacadeFileRename

        /** The name of the [JvmName] annotation of the file becomes [name]. */
        class ReplaceJvmName(val nameExpression: KtStringTemplateExpression, val name: String) : FacadeFileRename
    }

    /** @return what the rename of a facade class to [name] writes in [file], one of its files; null if nothing. See [FacadeFileRename]. */
    fun facadeFileRename(file: KtFile, name: String): FacadeFileRename? {
        val jvmNameEntry = JvmFileClassUtil.findAnnotationEntryOnFileNoResolve(file, JvmStandardClassIds.JVM_NAME_SHORT)
        return when {
            PackagePartClassUtils.getFilePartShortName(file.name) == name -> jvmNameEntry?.let { FacadeFileRename.DeleteJvmName(it) }
            jvmNameEntry == null -> {
                val newFileName = PackagePartClassUtils.getFileNameByFacadeName(name)
                val facadeDir = file.parent
                if (newFileName != null && facadeDir != null && facadeDir.findFile(newFileName) == null) {
                    FacadeFileRename.RenameFile(file, newFileName)
                }
                else {
                    FacadeFileRename.AddJvmName(file, name)
                }
            }
            else -> (jvmNameEntry.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression)
                ?.let { FacadeFileRename.ReplaceJvmName(it, name) }
        }
    }

    /**
     * Adds the [JvmName] annotation with [name] to [file]. The code is the [FacadeFileRename.AddJvmName] branch of the master
     * [renameFacadeLightClass]. The ModPlan rename runs it on the copy of the file.
     */
    fun addJvmName(file: KtFile, name: String) {
        val psiFactory = KtPsiFactory(file.project)
        val annotationText = "${JvmStandardClassIds.JVM_NAME_SHORT}(\"$name\")"
        val newFileAnnotationList = psiFactory.createFileAnnotationListWithAnnotation(annotationText)
        val annotationList = file.fileAnnotationList
        if (annotationList != null) {
            annotationList.add(newFileAnnotationList.annotationEntries.first())
        } else {
            val anchor = file.firstChild.siblings().firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }
            file.addBefore(newFileAnnotationList, anchor)
        }
    }

    /**
     * Properly renames facade Kotlin class with regard to [JvmName].
     *
     * This method is a copy from [org.jetbrains.kotlin.asJava.classes.KtLightClassForFacadeBase.setName].
     */
    fun renameFacadeLightClass(classForFacade: KtLightClassForFacade, name: String): PsiElement {
        for (file in classForFacade.files) {
            when (val rename = facadeFileRename(file, name)) {
                is FacadeFileRename.DeleteJvmName -> rename.annotation.delete()
                is FacadeFileRename.RenameFile -> rename.file.name = rename.newFileName
                is FacadeFileRename.AddJvmName -> addJvmName(rename.file, rename.name)
                is FacadeFileRename.ReplaceJvmName -> ElementManipulators.handleContentChange(rename.nameExpression, rename.name)
                null -> {}
            }
        }
        return classForFacade
    }

}