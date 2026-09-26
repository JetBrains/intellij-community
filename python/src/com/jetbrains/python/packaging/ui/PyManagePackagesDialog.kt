// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.ui

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import com.intellij.webcore.packaging.ManagePackagesDialog
import com.intellij.webcore.packaging.PackageManagementService
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.packaging.cache.firstPageOrEmpty
import com.jetbrains.python.packaging.cache.remainingItemsAfterPageIndex
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import javax.swing.DefaultListSelectionModel
import javax.swing.JList

internal class PyManagePackagesDialog(
  myProject: Project,
  myController: PackageManagementService,
  myPackageListener: PackageManagementService.Listener?,
  notificationPanel: PackagesNotificationPanel = PackagesNotificationPanel(),
) : ManagePackagesDialog(
  myProject,
  myController,
  myPackageListener,
  notificationPanel,
) {
  init {
    myPackages.cellRenderer = PyTableRenderer()
    myPackages.selectionModel =
      // make the truncated items count row unselectable
      object : DefaultListSelectionModel() {
        override fun setSelectionInterval(index0: Int, index1: Int) {
          moreItemsIndex?.also {
            if (it in indexesToRange(index0, index1)) {
              return
            }
          }
          super.setSelectionInterval(index0, index1)
        }

        override fun addSelectionInterval(index0: Int, index1: Int) {
          moreItemsIndex?.also {
            if (it in indexesToRange(index0, index1)) {
              return
            }
          }
          super.addSelectionInterval(index0, index1)
        }

        private fun indexesToRange(index0: Int, index1: Int): IntRange =
          if (index0 > index1) index1..index0 else index0..index1
      }
  }

  var moreItemsIndex: Int? = null

  override fun initModel() {
    myPackagesModel = PyPackagesModel(mutableListOf())

    val application = ApplicationManager.getApplication()
    application.invokeLater(Runnable {
      myPackages.setModel(myPackagesModel!!)

      mySelectedPackageName?.also {
        myFilter.filter = it
      }

      myFilter.filter()
      doSelectPackage(mySelectedPackageName)
    }, ModalityState.any())
  }

  private inner class PyPackagesModel(packages: MutableList<RepoPackage>) : PackagesModel(packages) {
    private var filterJob: Job? = null
    private val coroutineScope: CoroutineScope
      get() = ApplicationManager.getApplication().service<PyManagePackagesDialogService>().coroutineScope
    
    override fun filter(filter: String) {
      filterJob?.cancel()
      filterJob = coroutineScope.launch(Dispatchers.IO + ModalityState.current().asContextElement()) {
        val result = PyPiPackageRepository.search(filter)
        val page = result.firstPageOrEmpty().successOrNull?.asSequence() ?: emptySequence()
        val filtered = page.map { RepoPackage(it, null, null) }.toList()
        val toSelect = filtered.find { StringUtil.equalsIgnoreCase(it.name, filter) }

        if (isActive) {
          withContext(Dispatchers.EDT) {
            myView.clear()
            myPackages.clearSelection()
            myView.addAll((filtered))
            if (toSelect != null) myPackages.setSelectedValue(toSelect, true)

            if (result.pages.size > 1) {
              myView.add(MoreItemsNotice(result.remainingItemsAfterPageIndex(0)))
              moreItemsIndex = filtered.size
            }
            else {
              moreItemsIndex = null
            }

            fireContentsChanged(this, 0, myView.size)
          }
        }
      }
    }
  }

  private inner class PyTableRenderer : MyTableRenderer() {
    override fun getListCellRendererComponent(
      list: JList<out RepoPackage>,
      repoPackage: RepoPackage,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ): Component {
      if (repoPackage is MoreItemsNotice) {
        myNameComponent.clear()
        myRepositoryComponent.clear()

        myNameComponent.append(
          LangBundle.message("hidden.rows.label", repoPackage.hiddenCount),
          SimpleTextAttributes.GRAYED_ATTRIBUTES,
        )

        myPanel.background = if (index % 2 == 1) UIUtil.getListBackground() else UIUtil.getDecoratedRowColor()

        return myPanel
      }

      return super.getListCellRendererComponent(list, repoPackage, index, isSelected, cellHasFocus)
    }
  }
}

private class MoreItemsNotice(val hiddenCount: Int) : RepoPackage("empty", null, null)

@Service
private class PyManagePackagesDialogService(val coroutineScope: CoroutineScope)