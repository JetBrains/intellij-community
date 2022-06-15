// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.breakpoints.dialog

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.core.util.DescriptorMemberChooserObject
import org.jetbrains.kotlin.psi.KtProperty

fun PsiClass.collectProperties(): Array<DescriptorMemberChooserObject> {
    if (this is KtLightClassForFacade) {
        val result = arrayListOf<DescriptorMemberChooserObject>()
        for (file in this.files) {
            for (declaration in file.declarations.filterIsInstance<KtProperty>()) {
                result.add(DescriptorMemberChooserObject(declaration, declaration.unsafeResolveToDescriptor()))
            }
        }
        return result.toTypedArray()
    }
    if (this is KtLightClass) {
        val origin = this.kotlinOrigin
        if (origin != null) {
            return origin.declarations.filterIsInstance<KtProperty>().map {
                DescriptorMemberChooserObject(it, it.unsafeResolveToDescriptor())
            }.toTypedArray()
        }
    }
    return emptyArray()
}

