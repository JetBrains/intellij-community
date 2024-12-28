// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fe10.junit

import com.intellij.execution.JUnitRecognizer
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.caches.project.implementingDescriptors
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

private class KotlinMultiplatformJUnitRecognizer : JUnitRecognizer() {
    override fun isTestAnnotated(method: PsiMethod): Boolean {
        if (method !is KtLightMethod) return false
        val origin = method.kotlinOrigin ?: return false
        if (!origin.module?.platform.isCommon()) return false

        val moduleDescriptor = origin.containingKtFile.findModuleDescriptor()
        val implModules = moduleDescriptor.implementingDescriptors
        if (implModules.isEmpty()) return false

        val methodDescriptor = origin.resolveToDescriptorIfAny() ?: return false
        return methodDescriptor.annotations.any { it.isExpectOfAnnotation("org.junit.Test", implModules) }
    }
}

private fun AnnotationDescriptor.isExpectOfAnnotation(fqName: String, implModules: Collection<ModuleDescriptor>): Boolean {
    val annotationClass = annotationClass ?: return false
    if (!annotationClass.isExpect) return false
    val classId = annotationClass.classId ?: return false
    val segments = classId.relativeClassName.pathSegments()

    return implModules
        .any { module ->
            module
                .getPackage(classId.packageFqName).memberScope
                .getDescriptorsFiltered(DescriptorKindFilter.CLASSIFIERS) { it == segments.first() }
                .filterIsInstance<TypeAliasDescriptor>()
                .any { it.classDescriptor?.fqNameSafe?.asString() == fqName }
        }
}
