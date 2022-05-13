// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.platform

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor.Companion.getTestStateIcon
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.idea.util.string.joinWithEscape
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.platform.js.JsPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import javax.swing.Icon

private val TEST_FQ_NAME = FqName("kotlin.test.Test")
private val IGNORE_FQ_NAME = FqName("kotlin.test.Ignore")
private val GENERIC_KOTLIN_TEST_AVAILABLE_KEY = Key<ParameterizedCachedValue<Boolean, Module>>("GENERIC_KOTLIN_TEST_AVAILABLE")
private val GENERIC_KOTLIN_TEST_AVAILABLE_PROVIDER_WITHOUT_TEST_SCOPE = GenericKotlinTestAvailabilityProvider(false)
private val GENERIC_KOTLIN_TEST_AVAILABLE_PROVIDER_WITH_TEST_SCOPE = GenericKotlinTestAvailabilityProvider(true)

private class GenericKotlinTestAvailabilityProvider(private val test: Boolean) : ParameterizedCachedValueProvider<Boolean, Module> {
    override fun compute(module: Module): CachedValueProvider.Result<Boolean> {
        val project = module.project
        val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(test)
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val testFqNameStr = TEST_FQ_NAME.asString()
        val hasKotlinTest = javaPsiFacade.findClass(testFqNameStr, moduleScope) != null ||
                // `kotlin.test.Test` is a typealias
                KotlinTopLevelTypeAliasFqNameIndex.get(testFqNameStr, project, moduleScope).isNotEmpty() ||
                // `kotlin.test.Test` could be expected/actual annotation class
                KotlinFullClassNameIndex.get(testFqNameStr, project, moduleScope).isNotEmpty()

        return CachedValueProvider.Result.create(hasKotlinTest, ProjectRootManager.getInstance(project))
    }
}

fun getGenericTestIcon(
    declaration: KtNamedDeclaration,
    descriptorProvider: () -> DeclarationDescriptor?,
    initialLocations: () -> List<String>?
): Icon? {
    val locations = initialLocations()?.toMutableList() ?: return null

    // fast check if `kotlin.test.Test` is available in a module scope
    val project = declaration.project
    val index = ProjectFileIndex.getInstance(project)
    val virtualFile = declaration.containingFile.virtualFile
    index.getModuleForFile(virtualFile)?.let { module ->
        val availabilityProvider =
            if (index.isInTestSourceContent(virtualFile)) GENERIC_KOTLIN_TEST_AVAILABLE_PROVIDER_WITH_TEST_SCOPE else
                GENERIC_KOTLIN_TEST_AVAILABLE_PROVIDER_WITHOUT_TEST_SCOPE

        val available = CachedValuesManager.getManager(project)
            .getParameterizedCachedValue(module, GENERIC_KOTLIN_TEST_AVAILABLE_KEY, availabilityProvider, false, module)
        if (available == false) {
            return null
        }
    }

    val clazz = when (declaration) {
        is KtClassOrObject -> declaration
        is KtNamedFunction -> declaration.containingClassOrObject ?: return null
        else -> return null
    }

    val descriptor = descriptorProvider() ?: return null
    if (!descriptor.isKotlinTestDeclaration()) return null

    locations += clazz.parentsWithSelf.filterIsInstance<KtNamedDeclaration>()
        .mapNotNull { it.name }
        .toList().asReversed()

    val testName = (declaration as? KtNamedFunction)?.name
    if (testName != null) {
        locations += "$testName"
    }

    val prefix = if (testName != null) "test://" else "suite://"
    val url = prefix + locations.joinWithEscape('.')

    return getTestStateIcon(listOf("java:$url", url), declaration)
}

private tailrec fun DeclarationDescriptor.isIgnored(): Boolean {
    if (annotations.any { it.fqName == IGNORE_FQ_NAME }) {
        return true
    }

    val containingClass = containingDeclaration as? ClassDescriptor ?: return false
    return containingClass.isIgnored()
}

fun DeclarationDescriptor.isKotlinTestDeclaration(): Boolean {
    if (isIgnored()) {
        return false
    }

    if (annotations.any { it.fqName == TEST_FQ_NAME }) {
        return true
    }

    val classDescriptor = this as? ClassDescriptorWithResolutionScopes ?: return false
    return classDescriptor.declaredCallableMembers.any { it.isKotlinTestDeclaration() }
}

internal fun IdePlatformKind.isCompatibleWith(platform: TargetPlatform): Boolean {
    return when (this) {
        is JvmIdePlatformKind -> platform.has(JvmPlatform::class)
        is NativeIdePlatformKind -> platform.has(NativePlatform::class)
        is JsIdePlatformKind -> platform.has(JsPlatform::class)
        is CommonIdePlatformKind -> true
        else -> false
    }
}