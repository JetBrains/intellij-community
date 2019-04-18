/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.java

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.uast.*
import java.util.*

class JavaUFile(override val sourcePsi: PsiJavaFile, override val languagePlugin: UastLanguagePlugin) : UFile, UElement {
  override val packageName: String
    get() = sourcePsi.packageName

  override val imports: List<JavaUImportStatement> by lz {
    sourcePsi.importList?.allImportStatements?.map { JavaUImportStatement(it, this) } ?: listOf()
  }

  override val annotations: List<UAnnotation>
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
  override val psi get() = sourcePsi
}