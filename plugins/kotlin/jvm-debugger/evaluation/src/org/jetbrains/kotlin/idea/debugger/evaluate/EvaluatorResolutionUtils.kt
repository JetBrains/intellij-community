// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.org.objectweb.asm.Type as AsmType

internal fun AsmType.getClassDescriptor(
    scope: GlobalSearchScope,
    mapBuiltIns: Boolean = true,
    moduleDescriptor: ModuleDescriptor = DefaultBuiltIns.Instance.builtInsModule
): ClassDescriptor? {
    if (AsmUtil.isPrimitive(this)) return null

    val jvmName = JvmClassName.byInternalName(internalName).fqNameForClassNameWithoutDollars

    if (mapBuiltIns) {
        val mappedName = JavaToKotlinClassMap.mapJavaToKotlin(jvmName)
        if (mappedName != null) {
            moduleDescriptor.findClassAcrossModuleDependencies(mappedName)?.let { return it }
        }
    }

    return runReadAction {
        val classes = JavaPsiFacade.getInstance(scope.project ?: return@runReadAction null).findClasses(jvmName.asString(), scope)
        if (classes.isEmpty()) null
        else {
            classes.first().getJavaClassDescriptor()
        }
    }
}