// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.KotlinUElementWithComments

@ApiStatus.Internal
class KotlinUFile(
    override val psi: KtFile,
    override val languagePlugin: UastLanguagePlugin
) : UFile, KotlinUElementWithComments {

    private val javaPsiPart = UastLazyPart<PsiClassOwner?>()
    private val uAnnotationsPart = UastLazyPart<List<UAnnotation>>()
    private val allCommentsInFilePart = UastLazyPart<List<UComment>>()
    private val importsPart = UastLazyPart<List<UImportStatement>>()
    private val classesPart = UastLazyPart<List<UClass>>()

    private var packageNameValue: String? = null

    override val javaPsi: PsiClassOwner?
        get() = javaPsiPart.getOrBuild {
            val lightClasses = this@KotlinUFile.classes.map { it.javaPsi }.toTypedArray()
            val lightFile = lightClasses.asSequence()
                .mapNotNull { it.containingFile as? PsiClassOwner }
                .firstOrNull()

            if (lightFile == null) null
            else
                object : PsiClassOwner by lightFile {
                    override fun getClasses() = lightClasses
                    override fun setPackageName(packageName: String) = error("Incorrect operation for non-physical Java PSI")
                }
        }

    override val sourcePsi: KtFile = psi

    override val uAnnotations: List<UAnnotation>
        get() = uAnnotationsPart.getOrBuild {
            sourcePsi.annotationEntries.mapNotNull { languagePlugin.convertOpt(it, this) }
        }

    override val packageName: String
        get() {
            if (packageNameValue == null) {
                packageNameValue = sourcePsi.packageFqName.asString()
            }
            return packageNameValue!!
        }

    override val allCommentsInFile: List<UComment>
        get() = allCommentsInFilePart.getOrBuild {
            val comments = ArrayList<UComment>(0)
            sourcePsi.accept(object : PsiRecursiveElementWalkingVisitor() {
                override fun visitComment(comment: PsiComment) {
                    comments += UComment(comment, this@KotlinUFile)
                }
            })
            comments
        }

    override val imports: List<UImportStatement>
        get() = importsPart.getOrBuild {
            sourcePsi.importDirectives.mapNotNull { languagePlugin.convertOpt(it, this@KotlinUFile) }
        }

    override val implicitImports: List<String>
        get() = listOf(
            "kotlin",
            "kotlin.annotation",
            "kotlin.collections",
            "kotlin.comparisons",
            "kotlin.io",
            "kotlin.ranges",
            "kotlin.sequences",
            "kotlin.text",
            "kotlin.math",
            "kotlin.jvm"
        )

    override val classes: List<UClass>
        get() = classesPart.getOrBuild {
            val facadeOrScriptClass = if (sourcePsi.isScript()) sourcePsi.script?.toLightClass() else sourcePsi.findFacadeClass()
            val facadeOrScriptUClass = facadeOrScriptClass?.toUClass()?.let { listOf(it) } ?: emptyList()
            val classes = sourcePsi.declarations.mapNotNull { (it as? KtClassOrObject)?.toLightClass()?.toUClass() }
            facadeOrScriptUClass + classes
        }

    private fun PsiClass.toUClass() = languagePlugin.convertOpt<UClass>(this, this@KotlinUFile)
}
