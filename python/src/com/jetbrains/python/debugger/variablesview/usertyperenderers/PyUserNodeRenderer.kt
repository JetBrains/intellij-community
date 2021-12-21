// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.variablesview.usertyperenderers

import com.jetbrains.python.PyBundle
import com.jetbrains.python.debugger.PyUserTypeRenderer
import java.io.Serializable

class PyUserNodeRenderer(var isEnabled: Boolean, existingNames: List<String>?) {
  var name: String = getNewRendererName(existingNames)
  var toType: String = "object"
  var typeCanonicalImportPath = ""
  var typeQualifiedName: String = ""
  var typeSourceFile: String = ""
  var moduleRootHasOneTypeWithSameName: Boolean = false
  val valueRenderer = PyNodeValueRenderer()
  val childrenRenderer = PyNodeChildrenRenderer()

  fun clone(): PyUserNodeRenderer {
    val renderer = PyUserNodeRenderer(isEnabled, null)
    renderer.name = name
    renderer.toType = toType
    renderer.typeCanonicalImportPath = typeCanonicalImportPath
    renderer.typeQualifiedName = typeQualifiedName
    renderer.typeSourceFile = typeSourceFile
    renderer.moduleRootHasOneTypeWithSameName = moduleRootHasOneTypeWithSameName
    renderer.valueRenderer.isDefault = valueRenderer.isDefault
    renderer.valueRenderer.expression = valueRenderer.expression
    renderer.childrenRenderer.isDefault = childrenRenderer.isDefault
    renderer.childrenRenderer.children = childrenRenderer.children
    renderer.childrenRenderer.appendDefaultChildren = childrenRenderer.appendDefaultChildren
    return renderer
  }

  data class PyNodeValueRenderer(
    var isDefault: Boolean = true,
    var expression: String = ""
  ) : Serializable

  data class PyNodeChildrenRenderer(
    var isDefault: Boolean = true,
    var children: List<ChildInfo> = listOf(),
    var appendDefaultChildren: Boolean = false
  ) : Serializable

  data class ChildInfo(var expression: String = "") : Serializable

  fun isDefault() = valueRenderer.isDefault && childrenRenderer.isDefault

  fun hasNoEmptyTypeInfo() = typeQualifiedName != "" || typeCanonicalImportPath != ""

  fun isApplicable() = hasNoEmptyTypeInfo() && isEnabled

  fun equalTo(other: PyUserNodeRenderer): Boolean {
    return other.isEnabled == isEnabled &&
           other.name == name &&
           other.toType == toType &&
           other.valueRenderer == valueRenderer &&
           other.childrenRenderer == childrenRenderer
  }

  override fun toString() = name

  fun convertRenderer(): PyUserTypeRenderer {
    val children = childrenRenderer.children.map { PyUserTypeRenderer.ChildInfo(it.expression) }
    return PyUserTypeRenderer(
      toType,
      typeCanonicalImportPath,
      typeQualifiedName,
      typeSourceFile,
      moduleRootHasOneTypeWithSameName,
      valueRenderer.isDefault,
      valueRenderer.expression,
      childrenRenderer.isDefault,
      childrenRenderer.appendDefaultChildren,
      children
    )
  }
}

fun getNewRendererName(existingNames: List<String>?): String {
  val default = PyBundle.message("form.debugger.variables.view.user.type.renderers.unnamed")
  if (existingNames == null || existingNames.isEmpty()) return default
  val duplicatedNames = existingNames.filter { it.startsWith(default) }.size
  return if (duplicatedNames > 0) {
    "$default ($duplicatedNames)"
  }
  else {
    default
  }
}