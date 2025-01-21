// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifierList
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

/**
 * @property elementsAndTexts a list of `PsiElement`s and text segments to be converted
 * @property importsAndPackage Kotlin code of the package declaration and significant non-static import statements of the original Java file
 * @property sourceJavaFile a copy of the whole file containing the to-be-converted Java code fragment
 */
class ConversionData private constructor(
    val elementsAndTexts: ElementAndTextList,
    val importsAndPackage: String,
    val sourceJavaFile: PsiJavaFile
) {
    companion object {
        fun prepare(copiedCode: CopiedJavaCode, project: Project): ConversionData {
            val startOffsets = copiedCode.startOffsets.clone()
            val endOffsets = copiedCode.endOffsets.clone()
            assert(startOffsets.size == endOffsets.size) { "Must have the same size" }

            var fileText = copiedCode.fileText
            var file = PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, fileText) as PsiJavaFile

            val importsAndPackage = extractSignificantImportsAndPackage(file)

            val newFileText = ConversionTextClipper.clipTextIfNeeded(file, fileText, startOffsets, endOffsets)
            if (newFileText != null) {
                fileText = newFileText
                file = PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, newFileText) as PsiJavaFile
            }

            val elementsAndTexts = ElementAndTextList()
            for (i in startOffsets.indices) {
                elementsAndTexts.collectElementsToConvert(file, fileText, TextRange(startOffsets[i], endOffsets[i]))
            }

            return ConversionData(elementsAndTexts, importsAndPackage, file)
        }
    }
}

/**
 * Collects the `PsiElement`s in the specified text range of a given Java file to convert them.
 * The parts of `PsiElement`s that don't completely fit inside the range are added as plain text instead.
 *
 * Note: if the user selects some part of a complete `PsiElement` (for example, only the header of a `PsiMethod`),
 * then normally we would have to convert some leaf `PsiElement`s individually (`PsiIdentifier`, `PsiParameterList`, etc.).
 * However, the J2K converter doesn't handle such small elements well.
 * I.e., the result will be better if we convert the `PsiMethod` (even if it is incomplete), rather than its individual parts.
 *
 * To solve this problem, there is a [ConversionTextClipper] object, that does some PSI surgery on the conversion file
 * to strip `PsiElement`s that don't fit the conversion range, and transform the PSI to make it more suitable for J2K.
 * In our example above, this would make `file.elementsInRange(range)` return an incomplete `PsiMethod` instead of a bunch of leafs.
 *
 * See the test [org.jetbrains.kotlin.j2k.k2.K2JavaToKotlinCopyPasteConversionTestGenerated.testMethodDeclarationWithNoBody].
 */
private fun ElementAndTextList.collectElementsToConvert(
    file: PsiJavaFile,
    fileText: String,
    range: TextRange
) {
    val elements = file.elementsInRange(range).ifEmpty {
        // Couldn't parse a single complete `PsiElement` in the range, so add the whole code as text
        addText(fileText.substring(range.startOffset, range.endOffset))
        return
    }

    // Normal case: found some complete `PsiElement`s in the range
    val prefix = fileText.substring(range.startOffset, elements.first().textRange.startOffset)
    addText(prefix)

    for (element in elements) {
        when (element) {
            is PsiComment -> {
                // Don't convert comments separately, because later during conversion they will become attached
                // to the neighboring converted elements and so will be duplicated.
                continue
            }

            is PsiModifierList -> {
                // Conversion of PsiModifierList is not supported by the converter,
                // but we can still convert single annotations inside it individually.
                addElements(element.allChildren.toList())
            }

            else -> addElement(element)
        }
    }

    val postfix = fileText.substring(elements.last().textRange.endOffset, range.endOffset)
    addText(postfix)
}

/**
 * Builds a string containing the Kotlin code of the package declaration
 * and significant non-static import statements of the given Java [sourceFile].
 *
 * "Significant" means everything except platform Java classes that are mapped to Kotlin.
 */
private fun extractSignificantImportsAndPackage(sourceFile: PsiJavaFile): String {
    return buildString {
        val packageName = sourceFile.packageName
        if (packageName.isNotEmpty()) {
            append("package $packageName\n")
        }

        val importList = sourceFile.importList ?: return@buildString
        for (import in importList.importStatements) {
            val qualifiedName = import.qualifiedName ?: continue
            if (import.isOnDemand) {
                append("import $qualifiedName.*\n")
            } else {
                val fqName = FqNameUnsafe(qualifiedName)
                if (fqName.isSafe && JavaToKotlinClassMap.isJavaPlatformClass(fqName.toSafe())) continue
                append("import $qualifiedName\n")
            }
        }
        //TODO: static imports
    }
}
