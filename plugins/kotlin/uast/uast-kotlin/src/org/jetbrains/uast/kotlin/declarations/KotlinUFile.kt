// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.KotlinUElementWithComments
import java.util.*

class KotlinUFile(
    override val psi: KtFile,
    override val languagePlugin: UastLanguagePlugin = kotlinUastPlugin
) : UFile, KotlinUElementWithComments {
    override val packageName: String
        get() = psi.packageFqName.asString()

    override val uAnnotations: List<UAnnotation>
        get() = psi.annotationEntries.map { KotlinUAnnotation(it, this) }

    override val javaPsi: PsiClassOwner? by lz {
        val lightClasses = this@KotlinUFile.classes.map { it.javaPsi }.toTypedArray()
        val lightFile = lightClasses.asSequence()
            .mapNotNull { it.containingFile as? PsiClassOwner }
            .firstOrNull()

        if (lightFile == null) null
        else
            object : PsiClassOwner by lightFile {
                override fun getClasses() = lightClasses
                override fun setPackageName(packageName: String?) = error("Incorrect operation for non-physical Java PSI")
            }
    }

    override val sourcePsi: KtFile = psi

    override val allCommentsInFile by lz {
        val comments = ArrayList<UComment>(0)
        psi.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitComment(comment: PsiComment) {
                comments += UComment(comment, this@KotlinUFile)
            }
        })
        comments
    }
    
    override val imports by lz { psi.importDirectives.map { KotlinUImportStatement(it, this) } }

    override val classes by lz {
        val facadeOrScriptClass = if (psi.isScript()) psi.script?.toLightClass() else psi.findFacadeClass()
        val classes = psi.declarations.mapNotNull { (it as? KtClassOrObject)?.toLightClass()?.toUClass() }

        (facadeOrScriptClass?.toUClass()?.let { listOf(it) } ?: emptyList()) + classes
    }

    private fun PsiClass.toUClass() = languagePlugin.convertOpt<UClass>(this, this@KotlinUFile)
}
