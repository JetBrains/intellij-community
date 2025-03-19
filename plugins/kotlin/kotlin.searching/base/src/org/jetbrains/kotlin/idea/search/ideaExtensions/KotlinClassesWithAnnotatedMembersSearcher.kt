// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassesWithAnnotatedMembersSearch
import com.intellij.psi.search.searches.ScopedQueryExecutor
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.searching.KotlinAnnotatedElementsSearcher
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class KotlinClassesWithAnnotatedMembersSearcher : ScopedQueryExecutor<PsiClass, ClassesWithAnnotatedMembersSearch.Parameters> {
    override fun getScope(param: ClassesWithAnnotatedMembersSearch.Parameters): GlobalSearchScope {
        return GlobalSearchScope.getScopeRestrictedByFileTypes(param.annotationClass.project.allScope(), KotlinFileType.INSTANCE)
    }

    override fun execute(queryParameters: ClassesWithAnnotatedMembersSearch.Parameters, consumer: Processor<in PsiClass>): Boolean {
        val processed = hashSetOf<KtClassOrObject>()
        return KotlinAnnotatedElementsSearcher.processAnnotatedMembers(queryParameters.annotationClass,
                                                                       queryParameters.scope,
                                                                       { it.getNonStrictParentOfType<KtClassOrObject>() !in processed }) { declaration, _ ->
            val ktClass = declaration.getNonStrictParentOfType<KtClassOrObject>()
            if (ktClass != null && processed.add(ktClass)) {
                val lightClass = ktClass.toLightClass()
                if (lightClass != null) consumer.process(lightClass) else true
            } else
                true
        }
    }
}
