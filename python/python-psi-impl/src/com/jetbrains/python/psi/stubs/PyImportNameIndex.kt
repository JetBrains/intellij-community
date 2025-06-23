package com.jetbrains.python.psi.stubs

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.jetbrains.python.psi.PyImportElement

class PyImportNameIndex : StringStubIndexExtension<PyImportElement>() {
  companion object {
    @JvmField
    val  KEY: StubIndexKey<String, PyImportElement> = StubIndexKey.createIndexKey("python.import.name")

    fun containsImport(name: String, project: Project): Boolean {
      val projectScope = GlobalSearchScope.projectScope(project)
      var found = false
      StubIndex.getInstance().processElements(KEY, name, project, projectScope, PyImportElement::class.java) {
        found = true
        return@processElements false
      }
      return found
    }
  }

  override fun getKey(): StubIndexKey<String, PyImportElement> = KEY
}