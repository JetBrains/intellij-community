// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.injection

import org.jetbrains.kotlin.idea.base.injection.KotlinLanguageInjectionSupportBase
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.idea.util.findAnnotation as findAnnotationK1

internal class KotlinLanguageInjectionSupport : KotlinLanguageInjectionSupportBase() {
    override fun KtModifierListOwner.findAnnotation(name: FqName): KtAnnotationEntry? = findAnnotationK1(name)

    override fun KtModifierListOwner.addAnnotation(name: FqName, templateString: String) {
        addAnnotation(name, annotationInnerText = templateString)
    }

    override fun getPatternClasses() = arrayOf(KotlinPatterns::class.java)
}