// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.ConverterSettings.Companion.defaultSettings
import org.jetbrains.kotlin.j2k.OldJavaToKotlinConverter
import org.jetbrains.kotlin.j2k.Result
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

fun PsiElement.j2kText(settings: ConverterSettings = defaultSettings): String? =
    convertToKotlin(settings)?.results?.single()?.text //TODO: insert imports

fun PsiElement.convertToKotlin(settings: ConverterSettings = defaultSettings): Result? {
    if (language != JavaLanguage.INSTANCE) return null
    val j2kConverter = OldJavaToKotlinConverter(project, settings)
    return j2kConverter.elementsToKotlin(listOf(this))
}

fun PsiExpression.j2k(settings: ConverterSettings = defaultSettings): KtExpression? {
    val text = j2kText(settings) ?: return null
    return KtPsiFactory(project).createExpression(text)
}

@JvmOverloads
fun PsiMember.j2k(settings: ConverterSettings = defaultSettings): KtNamedDeclaration? {
    val text = j2kText(settings) ?: return null
    return KtPsiFactory(project).createDeclaration(text)
}