// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.propertyNameByAccessor
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.JvmNames
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.siblings

internal object RenameLightElementsHelper {

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
        val jvmNameAnnotation = lightMethod.modifierList.findAnnotation(JvmNames.JVM_NAME.asString())
        val demangledName =
            (if (lightMethod.isMangled) KotlinRenameRefactoringSupport.getInstance().demangleInternalName(name) else null) ?: name
        val newNameForOrigin = propertyNameByAccessor(demangledName, lightMethod) ?: demangledName
        if (newNameForOrigin == lightMethod.kotlinOrigin?.name) {
            jvmNameAnnotation?.delete()
            return
        }
        val nameExpression = jvmNameAnnotation?.findAttributeValue("name")?.unwrapped as? KtStringTemplateExpression
        if (nameExpression != null) {
            nameExpression.replace(KtPsiFactory(lightMethod.project).createStringTemplate(name))
        } else {
            val toRename = lightMethod.kotlinOrigin as? PsiNamedElement ?: return
            toRename.setName(newNameForOrigin)
        }
    }

    /**
     * Properly renames facade Kotlin class with regard to [JvmName].
     *
     * This method is a copy from [org.jetbrains.kotlin.asJava.classes.KtLightClassForFacadeBase.setName].
     */
    fun renameFacadeLightClass(classForFacade: KtLightClassForFacade, name: String): PsiElement {
        for (file in classForFacade.files) {
            val jvmNameEntry = JvmFileClassUtil.findAnnotationEntryOnFileNoResolve(file, JvmNames.JVM_NAME_SHORT)

            if (PackagePartClassUtils.getFilePartShortName(file.name) == name) {
                jvmNameEntry?.delete()
                continue
            }

            if (jvmNameEntry == null) {
                val newFileName = PackagePartClassUtils.getFileNameByFacadeName(name)
                val facadeDir = file.parent
                if (newFileName != null && facadeDir != null && facadeDir.findFile(newFileName) == null) {
                    file.name = newFileName
                    continue
                }

                val psiFactory = KtPsiFactory(classForFacade.project)
                val annotationText = "${JvmNames.JVM_NAME_SHORT}(\"$name\")"
                val newFileAnnotationList = psiFactory.createFileAnnotationListWithAnnotation(annotationText)
                val annotationList = file.fileAnnotationList
                if (annotationList != null) {
                    annotationList.add(newFileAnnotationList.annotationEntries.first())
                } else {
                    val anchor = file.firstChild.siblings().firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }
                    file.addBefore(newFileAnnotationList, anchor)
                }
                continue
            }

            val jvmNameExpression = jvmNameEntry.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
                ?: continue
            ElementManipulators.handleContentChange(jvmNameExpression, name)
        }

        return classForFacade
    }

}