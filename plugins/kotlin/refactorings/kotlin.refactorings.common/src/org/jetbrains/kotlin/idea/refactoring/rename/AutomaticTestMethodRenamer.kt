// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticTestRenamerFactory
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import java.util.regex.Pattern

class AutomaticTestMethodRenamerFactory : AutomaticTestRenamerFactory() {
    override fun isApplicable(element: PsiElement): Boolean {
        val psiMethod = element.getRepresentativeLightMethod()
        if (psiMethod !is PsiMethod) return false
        val psiClass = psiMethod.containingClass ?: return false
        return TestFrameworks.detectFramework(psiClass) == null
    }

    override fun createRenamer(element: PsiElement, newName: String, usages: MutableCollection<UsageInfo>?): AutomaticRenamer {
        val psiMethod = element.getRepresentativeLightMethod()
        return AutomaticTestMethodRenamer(psiMethod, psiMethod?.containingClass, newName)
    }

    override fun getOptionName(): String = RefactoringBundle.message("rename.test.method")

    override fun isEnabled(): Boolean = KotlinCommonRefactoringSettings.getInstance().renameTestMethods

    override fun setEnabled(enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().renameTestMethods = enabled
    }
}

class AutomaticTestMethodRenamer(psiMethod: PsiMethod?, containingClass: PsiClass?, newName: String) : AutomaticRenamer() {
    init {
        findMethodsToReplace(psiMethod, containingClass)
        suggestAllNames(psiMethod?.name, newName)
    }


    private fun findMethodsToReplace(psiMethod: PsiMethod?, containingClass: PsiClass?) {
        if (containingClass == null || psiMethod == null) return
        val module = ModuleUtilCore.findModuleForPsiElement(containingClass) ?: return
        val moduleScope = GlobalSearchScope.moduleWithDependentsScope(module)

        val cache = PsiShortNamesCache.getInstance(containingClass.getProject())

        val classPattern = Pattern.compile(".*${containingClass.name}.*")
        val methodPattern = Pattern.compile(".*${psiMethod.name}.*", Pattern.CASE_INSENSITIVE)

        for (eachName in ContainerUtil.newHashSet(*cache.getAllClassNames())) {
            if (classPattern.matcher(eachName).matches()) {
                for (eachClass in cache.getClassesByName(eachName, moduleScope)) {
                    if (TestFrameworks.detectFramework(eachClass) != null) {
                        eachClass.methods.forEach {
                            if (methodPattern.matcher(it.name).matches()) {
                                myElements.add(it)
                            }
                        }
                    }
                }
            }
        }
    }


    override fun getDialogTitle(): String = RefactoringBundle.message("rename.test.method.title")

    override fun getDialogDescription(): String = RefactoringBundle.message("rename.test.method.description")

    override fun entityName(): String = RefactoringBundle.message("rename.test.method.entity.name")
}