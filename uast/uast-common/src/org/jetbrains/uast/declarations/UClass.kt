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
package org.jetbrains.uast

import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A class wrapper to be used in [UastVisitor].
 */
interface UClass : UDeclaration, PsiClass {
  override val psi: PsiClass

  override fun getQualifiedName(): String?

  /**
   * Returns a [UClass] wrapper of the superclass of this class, or null if this class is [java.lang.Object].
   */
  override fun getSuperClass(): UClass? {
    val superClass = psi.superClass ?: return null
    return getUastContext().convertWithParent(superClass)
  }

  val uastSuperTypes: List<UTypeReferenceExpression>

  /**
   * Returns [UDeclaration] wrappers for the class declarations.
   */
  val uastDeclarations: List<UDeclaration>

  override fun getFields(): Array<UField> =
    psi.fields.map { getLanguagePlugin().convert<UField>(it, this) }.toTypedArray()

  override fun getInitializers(): Array<UClassInitializer> =
    psi.initializers.map { getLanguagePlugin().convert<UClassInitializer>(it, this) }.toTypedArray()

  override fun getMethods(): Array<UMethod> =
    psi.methods.map { getLanguagePlugin().convert<UMethod>(it, this) }.toTypedArray()

  override fun getInnerClasses(): Array<UClass> =
    psi.innerClasses.map { getLanguagePlugin().convert<UClass>(it, this) }.toTypedArray()

  override fun asLogString() = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitClass(this)) return
    annotations.acceptList(visitor)
    uastDeclarations.acceptList(visitor)
    visitor.afterVisitClass(this)
  }

  override fun asRenderString() = buildString {
    append(psi.renderModifiers())
    val kind = when {
      psi.isAnnotationType -> "annotation"
      psi.isInterface -> "interface"
      psi.isEnum -> "enum"
      else -> "class"
    }
    append(kind).append(' ').append(psi.name)
    val superTypes = uastSuperTypes
    if (superTypes.isNotEmpty()) {
      append(" : ")
      append(superTypes.joinToString { it.asRenderString() })
    }
    appendln(" {")
    uastDeclarations.forEachIndexed { index, declaration ->
      appendln(declaration.asRenderString().withMargin)
    }
    append("}")
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) =
    visitor.visitClass(this, data)
}

interface UAnonymousClass : UClass, PsiAnonymousClass {
  override val psi: PsiAnonymousClass
}