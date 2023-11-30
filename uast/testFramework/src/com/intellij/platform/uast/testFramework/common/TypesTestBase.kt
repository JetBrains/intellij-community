// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.uast.testFramework.common

import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.UastVisitor
import java.io.File

interface TypesTestBase {
  fun getTypesFile(testName: String): File

  private fun UFile.asLogTypes() = TypesLogger().apply {
    this@asLogTypes.accept(this)
  }.toString()

  fun check(testName: String, file: UFile) {
    val valuesFile = getTypesFile(testName)

    assertEqualsToFile("Log values", valuesFile, file.asLogTypes())
  }

  class TypesLogger : UastVisitor {

    val builder: StringBuilder = StringBuilder()

    var level: Int = 0

    override fun visitElement(node: UElement): Boolean {
      val initialLine = node.asLogString() + " [" + run {
        val renderString = node.asRenderString().lines()
        if (renderString.size == 1) {
          renderString.single()
        }
        else {
          renderString.first() + "..." + renderString.last()
        }
      } + "]"

      (1..level).forEach { builder.append("    ") }
      builder.append(initialLine)
      if (node is UExpression) {
        val value = node.getExpressionType()
        value?.let { psiType ->
          builder.append(" : ")
          psiType.annotations.takeIf { it.isNotEmpty() }?.joinTo(builder, ", ", "{", "}") { annotation ->
            "@${annotation.qualifiedName}(${
              annotation.attributes.joinToString { attr ->
                attr.attributeName + " = " + when (val v = attr.attributeValue) {
                  is JvmAnnotationConstantValue -> v.constantValue
                  is JvmAnnotationEnumFieldValue -> v.fieldName
                  else -> v
                }
              }
            })"
          }
          builder.append(psiType)
        }
      }
      builder.appendLine()
      level++
      return false
    }

    override fun afterVisitElement(node: UElement) {
      level--
    }

    override fun toString(): String = builder.toString()
  }
}