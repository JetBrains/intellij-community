// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments
import java.util.*

@ApiStatus.Internal
class JavaUFile(
  override val sourcePsi: PsiJavaFile,
  override val languagePlugin: UastLanguagePlugin
) : UFile, UElement, JavaUElementWithComments {

  override val packageName: String
    get() = sourcePsi.packageName

  override val imports: List<UImportStatement> by lz {
    sourcePsi.importList?.allImportStatements?.map { JavaUImportStatement(it, this) } ?: listOf()
  }

  override val uAnnotations: List<UAnnotation>
    get() = sourcePsi.packageStatement?.annotationList?.annotations?.map { JavaUAnnotation(it, this) } ?: emptyList()

  override val classes: List<UClass> by lz { sourcePsi.classes.map { JavaUClass.create(it, this) } }

  override val allCommentsInFile: ArrayList<UComment> by lz {
    val comments = ArrayList<UComment>(0)
    sourcePsi.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitComment(comment: PsiComment) {
        comments += UComment(comment, this@JavaUFile)
      }
    })
    comments
  }

  override fun equals(other: Any?): Boolean = (other as? JavaUFile)?.sourcePsi == sourcePsi

  @Suppress("OverridingDeprecatedMember")
  override val psi
    get() = sourcePsi
}
