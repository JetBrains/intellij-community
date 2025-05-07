// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.util.IconUtil
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class PyRepositoriesList(val project: Project) : MasterDetailsComponent() {

  init {
    initTree()
    service<PyPackageRepositories>()
      .repositories
      .map { MyNode(PyRepositoryListItem(it, project)) }
      .forEach { addNode(it, myRoot) }
  }

  override fun createActions(fromPopup: Boolean): List<AnAction> {
    val add = object : DumbAwareAction({ CommonBundle.message("button.add") },
                                       Presentation.NULL_STRING,
                                       IconUtil.addIcon) {
      override fun actionPerformed(e: AnActionEvent) {
        val uniqueName = UniqueNameGenerator.generateUniqueName(
          PyBundle.message("python.packaging.repository.form.default.name"),
          remainingRepositories().map { it.name }.toMutableList())

        val newNode = MyNode(PyRepositoryListItem(PyPackageRepository(uniqueName, null, null), project))
        addNode(newNode, myRoot)
        selectNodeInTree(newNode)
      }
    }
    return listOf(add, MyDeleteAction())
  }

  override fun apply() {
    super.apply()
    val service = service<PyPackageRepositories>()
    service.repositories.clear()
    remainingRepositories().forEach { service.repositories.add(it) }
  }

  override fun processRemovedItems() {
    val remaining = remainingRepositories().toSet()
    service<PyPackageRepositories>().repositories
      .filter { it !in remaining }
      .forEach { it.clearCredentials() }
  }

  override fun wasObjectStored(editableObject: Any?): Boolean {
    val repo = editableObject as? PyPackageRepository ?: return false
    return service<PyPackageRepositories>().repositories.any { it == repo }
  }

  override fun getDisplayName(): String {
    return PyBundle.message("python.packaging.repository.manage.dialog.name")
  }

  private fun remainingRepositories(): Sequence<PyPackageRepository> {
    return TreeUtil.treeNodeTraverser(myRoot)
      .filter(MyNode::class.java)
      .asSequence()
      .filter { it != myRoot }
      .map { (it.configurable as PyRepositoryListItem).repository }
  }
}