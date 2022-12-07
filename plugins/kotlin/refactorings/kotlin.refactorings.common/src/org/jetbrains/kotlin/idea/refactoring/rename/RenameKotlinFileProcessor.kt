// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiFileProcessor
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.psi.KtFile

class RenameKotlinFileProcessor : RenamePsiFileProcessor() {
    class FileRenamingPsiClassWrapper(
        private val psiClass: KtLightClassForFacade,
        private val file: KtFile
    ) : KtLightClassForFacade by psiClass {
        override fun isValid() = file.isValid
        override fun equals(other: Any?): Boolean = other === this ||
                other is FileRenamingPsiClassWrapper &&
                other.psiClass == psiClass &&
                other.file == file

        override fun hashCode(): Int = psiClass.hashCode() * 31 + file.hashCode()
        override fun getSourceElement(): PsiElement? = psiClass.sourceElement
    }

    override fun canProcessElement(element: PsiElement): Boolean {
        return element is KtFile && RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(element)
    }

    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope
    ) {
        val ktFile = element as? KtFile ?: return
        if (FileTypeManager.getInstance().getFileTypeByFileName(newName) != KotlinFileType.INSTANCE) {
            return
        }

        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return

        val fileInfo = JvmFileClassUtil.getFileClassInfoNoResolve(ktFile)
        if (!fileInfo.withJvmName) {
            val facadeFqName = fileInfo.facadeClassFqName
            val project = ktFile.project
            val facadeClass = JavaPsiFacade.getInstance(project)
                .findClass(facadeFqName.asString(), GlobalSearchScope.moduleScope(module)) as? KtLightClassForFacade
            if (facadeClass != null) {
                allRenames[FileRenamingPsiClassWrapper(facadeClass, ktFile)] = PackagePartClassUtils.getFilePartShortName(newName)
            }
        }
    }

    override fun renameElement(element: PsiElement, newName: String, usages: Array<UsageInfo>, listener: RefactoringElementListener?) {
        val kotlinUsages = ArrayList<UsageInfo>(usages.size)

        KotlinRenameRefactoringSupport.getInstance().processForeignUsages(element, newName, usages, fallbackHandler = {
            kotlinUsages += it
        })

        super.renameElement(element, newName, kotlinUsages.toTypedArray(), listener)
    }
}
