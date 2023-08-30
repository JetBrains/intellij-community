// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import org.jetbrains.kotlin.idea.base.injection.KotlinLanguageInjectionSupportBase
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner

internal class K2KotlinLanguageInjectionSupport: KotlinLanguageInjectionSupportBase() {
    override fun KtModifierListOwner.findAnnotation(name: FqName): KtAnnotationEntry? = findAnnotation(ClassId.topLevel(name))

    override fun KtModifierListOwner.addAnnotation(name: FqName, templateString: String) {
        addAnnotation(ClassId.topLevel(name), templateString)
    }

    override fun getPatternClasses() = arrayOf(KotlinPatterns::class.java)
}