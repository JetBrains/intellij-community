// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments

@ApiStatus.Internal
class JavaUFile(
  override val sourcePsi: PsiJavaFile,
  override val languagePlugin: UastLanguagePlugin
) : UFile, UElement, JavaUElementWithComments {

  private val importsPart = UastLazyPart<List<UImportStatement>>()
  private val classesPart = UastLazyPart<List<UClass>>()
  private val allCommentsInFilePart = UastLazyPart<List<UComment>>()

  override val packageName: String
    get() = sourcePsi.packageName

  override val imports: List<UImportStatement>
    get() = importsPart.getOrBuild {
      sourcePsi.importList?.allImportStatements?.map { JavaUImportStatement(it, this) } ?: listOf()
    }

  override val implicitImports: List<String>
    get() = sourcePsi.implicitlyImportedPackages.toList()

  override val uAnnotations: List<UAnnotation>
    get() = sourcePsi.packageStatement?.annotationList?.annotations?.map { JavaUAnnotation(it, this) } ?: emptyList()

  override val classes: List<UClass>
    get() = classesPart.getOrBuild { sourcePsi.classes.map { JavaUClass.create(it, this) } }

  override val allCommentsInFile: List<UComment>
    get() = allCommentsInFilePart.getOrBuild {
      val comments = ArrayList<UComment>(0)
      sourcePsi.accept(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitComment(comment: PsiComment) {
          comments += UComment(comment, this@JavaUFile)
        }
      })
      comments
    }

  override fun equals(other: Any?): Boolean = (other as? JavaUFile)?.sourcePsi == sourcePsi

  override fun hashCode(): Int = sourcePsi.hashCode()

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiJavaFile
    get() = sourcePsi
}
