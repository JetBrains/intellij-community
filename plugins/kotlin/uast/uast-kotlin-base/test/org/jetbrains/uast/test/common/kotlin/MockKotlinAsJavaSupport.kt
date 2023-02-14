// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.KtFakeLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript

open class MockKotlinAsJavaSupport(private val baseKotlinAsJavaSupport: KotlinAsJavaSupport) : KotlinAsJavaSupport() {
    override fun createFacadeForSyntheticFile(file: KtFile): KtLightClassForFacade =
        baseKotlinAsJavaSupport.createFacadeForSyntheticFile(file)

    override fun findClassOrObjectDeclarations(fqName: FqName, searchScope: GlobalSearchScope): Collection<KtClassOrObject> =
        baseKotlinAsJavaSupport.findClassOrObjectDeclarations(fqName, searchScope)

    override fun findClassOrObjectDeclarationsInPackage(
      packageFqName: FqName,
      searchScope: GlobalSearchScope
    ): Collection<KtClassOrObject> = baseKotlinAsJavaSupport.findClassOrObjectDeclarationsInPackage(packageFqName, searchScope)

    override fun findFilesForFacade(facadeFqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> =
        baseKotlinAsJavaSupport.findFilesForFacade(facadeFqName, searchScope)

    override fun findFilesForFacadeByPackage(packageFqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> =
        baseKotlinAsJavaSupport.findFilesForFacadeByPackage(packageFqName, searchScope)

    override fun findFilesForPackage(packageFqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> =
        baseKotlinAsJavaSupport.findFilesForPackage(packageFqName, searchScope)

    override fun getLightFacade(file: KtFile): KtLightClassForFacade? = baseKotlinAsJavaSupport.getLightFacade(file)

    override fun getFacadeClasses(facadeFqName: FqName, scope: GlobalSearchScope): Collection<KtLightClassForFacade> =
        baseKotlinAsJavaSupport.getFacadeClasses(facadeFqName, scope)

    override fun getFacadeClassesInPackage(packageFqName: FqName, scope: GlobalSearchScope): Collection<KtLightClassForFacade> =
        baseKotlinAsJavaSupport.getFacadeClassesInPackage(packageFqName, scope)

    override fun getFacadeNames(packageFqName: FqName, scope: GlobalSearchScope): Collection<String> =
        baseKotlinAsJavaSupport.getFacadeNames(packageFqName, scope)

    override fun getFakeLightClass(classOrObject: KtClassOrObject): KtFakeLightClass =
        baseKotlinAsJavaSupport.getFakeLightClass(classOrObject)

    override fun getKotlinInternalClasses(fqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> =
        baseKotlinAsJavaSupport.getKotlinInternalClasses(fqName, scope)

    override fun getLightClass(classOrObject: KtClassOrObject): KtLightClass? =
        baseKotlinAsJavaSupport.getLightClass(classOrObject)

    override fun getLightClassForScript(script: KtScript): KtLightClass?=
        baseKotlinAsJavaSupport.getLightClassForScript(script)

    override fun getScriptClasses(scriptFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> =
        baseKotlinAsJavaSupport.getScriptClasses(scriptFqName, scope)

    override fun getSubPackages(fqn: FqName, scope: GlobalSearchScope): Collection<FqName> =
        baseKotlinAsJavaSupport.getSubPackages(fqn, scope)

    override fun packageExists(fqName: FqName, scope: GlobalSearchScope): Boolean =
        baseKotlinAsJavaSupport.packageExists(fqName, scope)
}