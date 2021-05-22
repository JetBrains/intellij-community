// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A class wrapper to be used in [UastVisitor].
 */
interface UClass : UDeclaration, PsiClass {
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiClass

  override val javaPsi: PsiClass

  override fun getQualifiedName(): String?

  override fun isInterface(): Boolean

  override fun isAnnotationType(): Boolean

  /**
   * Returns a [UClass] wrapper of the superclass of this class, or null if this class is [java.lang.Object].
   */
  @Deprecated("will return null if existing superclass is not convertable to Uast, use `javaPsi.superClass` instead",
              ReplaceWith("javaPsi.superClass"))
  override fun getSuperClass(): UClass? {
    val superClass = javaPsi.superClass ?: return null
    return UastFacade.convertWithParent(superClass)
  }

  val uastSuperTypes: List<UTypeReferenceExpression>

  /**
   * Returns [UDeclaration] wrappers for the class declarations.
   */
  val uastDeclarations: List<UDeclaration>

  private inline fun <reified T : UElement> convertOrReport(psiElement: PsiElement, parent: UElement): T? =
    convertOrReport(psiElement, parent, T::class.java)

  private fun <T : UElement> convertOrReport(psiElement: PsiElement, parent: UElement, expectedType: Class<T>): T? {
    fun getInfoString() = buildString {
      appendln("context:${this@UClass.javaClass}")
      appendln("psiElement:${psiElement.javaClass}")
      appendln("psiElementContent:${runCatching { psiElement.text }}")
    }

    val plugin = this.sourcePsi?.let { UastFacade.findPlugin(it) } ?: UastFacade.findPlugin(psiElement)
    if (plugin == null) {
      LOG.error("cant get UAST plugin for $this to convert element $psiElement", Attachment("info.txt", getInfoString()))
      return null
    }
    val result = expectedType.cast(plugin.convertElement(psiElement, parent, expectedType))
    if (result == null) {
      LOG.error("failed to convert element $psiElement in $this", Attachment("info.txt", getInfoString()))
    }
    return result
  }

  override fun getFields(): Array<UField> =
    javaPsi.fields.mapNotNull { convertOrReport<UField>(it, this) }.toTypedArray()

  override fun getInitializers(): Array<UClassInitializer> =
    javaPsi.initializers.mapNotNull { convertOrReport<UClassInitializer>(it, this) }.toTypedArray()

  override fun getMethods(): Array<UMethod> =
    javaPsi.methods.mapNotNull { convertOrReport<UMethod>(it, this) }.toTypedArray()

  override fun getInnerClasses(): Array<UClass> =
    javaPsi.innerClasses.mapNotNull { convertOrReport<UClass>(it, this) }.toTypedArray()

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitClass(this)) return
    uAnnotations.acceptList(visitor)
    uastDeclarations.acceptList(visitor)
    visitor.afterVisitClass(this)
  }

  override fun asRenderString(): String = buildString {
    append(javaPsi.renderModifiers())
    val kind = when {
      javaPsi.isAnnotationType -> "annotation"
      javaPsi.isInterface -> "interface"
      javaPsi.isEnum -> "enum"
      else -> "class"
    }
    append(kind).append(' ').append(javaPsi.name)
    val superTypes = uastSuperTypes
    if (superTypes.isNotEmpty()) {
      append(" : ")
      append(superTypes.joinToString { it.asRenderString() })
    }
    appendln(" {")
    uastDeclarations.forEachIndexed { _, declaration ->
      appendln(declaration.asRenderString().withMargin)
    }
    append("}")
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitClass(this, data)
}

private val LOG = Logger.getInstance(UClass::class.java)

interface UAnonymousClass : UClass, PsiAnonymousClass {
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiAnonymousClass
}

@Deprecated("no more needed, use UClass", ReplaceWith("UClass"))
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
interface UClassTypeSpecific : UClass