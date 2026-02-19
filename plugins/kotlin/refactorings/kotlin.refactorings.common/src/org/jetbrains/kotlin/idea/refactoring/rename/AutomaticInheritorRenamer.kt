// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.psi.KtClass

class AutomaticInheritorRenamer(klass: KtClass, newName: String) : AutomaticRenamer() {
    init {
        val lightClass = klass.toLightClass()
        if (lightClass != null) {
            for (inheritorLightClass in ClassInheritorsSearch.search(lightClass, true).findAll()) {
                if ((inheritorLightClass.unwrapped as? PsiNamedElement)?.name != null) {
                    myElements.add(inheritorLightClass.unwrapped as PsiNamedElement)
                }
            }
        }

        suggestAllNames(klass.name, newName)
    }

    override fun getDialogTitle() = RefactoringBundle.message("rename.inheritors.title")
    override fun getDialogDescription() = JavaRefactoringBundle.message("rename.inheritors.with.the.following.names.to.title")
    override fun entityName() = JavaRefactoringBundle.message("entity.name.inheritor")
}

class AutomaticInheritorRenamerFactory : AutomaticRenamerFactory {
    override fun isApplicable(element: PsiElement) = element is KtClass
    override fun getOptionName() = RefactoringBundle.message("rename.inheritors")
    override fun isEnabled() = KotlinCommonRefactoringSettings.getInstance().renameInheritors
    override fun setEnabled(enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().renameInheritors = enabled
    }

    override fun createRenamer(element: PsiElement, newName: String, usages: Collection<UsageInfo>): AutomaticRenamer {
        return AutomaticInheritorRenamer(element as KtClass, newName)
    }
}
