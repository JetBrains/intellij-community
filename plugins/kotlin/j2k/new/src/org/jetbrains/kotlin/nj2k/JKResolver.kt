// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.util.firstNotNullResult

class JKResolver(val project: Project, module: Module?, private val contextElement: PsiElement) {
    private val scope = module?.let {
        GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(it)
    } ?: GlobalSearchScope.allScope(project)

    fun resolveDeclaration(fqName: FqName): PsiElement? =
        resolveFqNameOfKtClassByIndex(fqName)
            ?: resolveFqNameOfJavaClassByIndex(fqName)
            ?: resolveFqNameOfKtFunctionByIndex(fqName)
            ?: resolveFqNameOfKtPropertyByIndex(fqName)
            ?: resolveFqName(fqName)

    fun resolveClass(fqName: FqName): PsiElement? =
        resolveFqNameOfKtClassByIndex(fqName)
            ?: resolveFqNameOfJavaClassByIndex(fqName)
            ?: resolveFqName(fqName)

    fun resolveMethod(fqName: FqName): PsiElement? =
        resolveFqNameOfKtFunctionByIndex(fqName)
            ?: resolveFqName(fqName)

    fun resolveMethodWithExactSignature(methodFqName: FqName, parameterTypesFqNames: List<FqName>): PsiElement? =
        resolveFqNameOfKtFunctionByIndex(methodFqName) {
            it.valueParameters.size == parameterTypesFqNames.size &&
                    it.valueParameters.map { param -> param.resolveToParameterDescriptorIfAny()?.type?.fqName } == parameterTypesFqNames
        }

    fun resolveField(fqName: FqName): PsiElement? =
        resolveFqNameOfKtPropertyByIndex(fqName)
            ?: resolveFqName(fqName)

    private fun resolveFqNameOfKtClassByIndex(fqName: FqName): KtDeclaration? {
        val fqNameString = fqName.asString()
        val classesPsi = KotlinFullClassNameIndex.getInstance()[fqNameString, project, scope]
        val typeAliasesPsi = KotlinTopLevelTypeAliasFqNameIndex.getInstance()[fqNameString, project, scope]

        return selectNearest(classesPsi, typeAliasesPsi)
    }

    private fun resolveFqNameOfJavaClassByIndex(fqName: FqName): PsiClass? {
        val fqNameString = fqName.asString()
        return JavaFullClassNameIndex.getInstance()[fqNameString, project, scope]
            .firstOrNull {
                it.qualifiedName == fqNameString
            }
    }

    private fun resolveFqNameOfKtFunctionByIndex(fqName: FqName, filter: (KtNamedFunction) -> Boolean = { true }): KtNamedFunction? =
        KotlinTopLevelFunctionFqnNameIndex.getInstance()[fqName.asString(), project, scope].firstOrNull { filter(it) }

    private fun resolveFqNameOfKtPropertyByIndex(fqName: FqName): KtProperty? =
        KotlinTopLevelPropertyFqnNameIndex.getInstance()[fqName.asString(), project, scope].firstOrNull()


    private fun resolveFqName(fqName: FqName): PsiElement? {
        if (fqName.isRoot) return null
        return constructImportDirectiveWithContext(fqName)
            .getChildOfType<KtDotQualifiedExpression>()
            ?.selectorExpression
            ?.references
            ?.firstNotNullResult(PsiReference::resolve)
    }

    private fun constructImportDirectiveWithContext(fqName: FqName): KtImportDirective {
        val importDirective = KtPsiFactory(contextElement).createImportDirective(ImportPath(fqName, false))
        importDirective.containingKtFile.analysisContext = contextElement.containingFile
        return importDirective
    }

    private fun selectNearest(classesPsi: Collection<KtDeclaration>, typeAliasesPsi: Collection<KtTypeAlias>): KtDeclaration? =
        when {
            typeAliasesPsi.isEmpty() -> classesPsi.firstOrNull()
            classesPsi.isEmpty() -> typeAliasesPsi.firstOrNull()
            else -> (classesPsi.asSequence() + typeAliasesPsi.asSequence()).minWithOrNull(Comparator { o1, o2 ->
                scope.compare(o1.containingFile.virtualFile, o2.containingFile.virtualFile)
            })
        }
}
