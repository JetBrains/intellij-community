package org.jetbrains.plugins.textmate.configuration

import com.intellij.CommonBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.PathUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.textmate.TextMateBundle
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.bundles.TextMateBundleReader
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.ListSelectionModel

data class TextMateConfigurableBundle(@NlsSafe val name: String, val path: String, val enabled: Boolean, val builtin: Boolean)

class TextMateBundlesListPanel : Disposable {
  private val myBundlesList: CheckBoxList<TextMateConfigurableBundle>

  init {
    myBundlesList = object : CheckBoxList<TextMateConfigurableBundle>() {
      init {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        ListSpeedSearch.installOn(this) { box: JCheckBox -> box.text }
      }

      override fun getSecondaryText(index: Int): String? {
        return getItemAt(index)?.let { bundle ->
          if (bundle.builtin) {
            TextMateBundle.message("title.built.in")
          }
          else {
            PathUtil.toSystemDependentName(bundle.path)
          }
        }
      }
    }
  }

  fun getState(): Set<TextMateConfigurableBundle> {
    val result: MutableSet<TextMateConfigurableBundle> = HashSet()
    for (i in 0 until myBundlesList.itemsCount) {
      result.add(myBundlesList.getItemAt(i)!!.copy(enabled = myBundlesList.isItemSelected(i)))
    }
    return result
  }

  fun setState(bundles: Collection<TextMateConfigurableBundle>) {
    myBundlesList.clear()
    for (bean in bundles.sortedWith(compareBy(NaturalComparator.INSTANCE, TextMateConfigurableBundle::name))) {
      myBundlesList.addItem(bean, bean.name, bean.enabled)
    }
  }

  fun createMainComponent(): JPanel {
    return panel {
      row {
        text(TextMateBundle.message("textmate.configuration.description", ApplicationNamesInfo.getInstance().fullProductName))
      }
      row {
        cell(
          ToolbarDecorator.createDecorator(myBundlesList)
            .setRemoveAction(AnActionButtonRunnable {
              val bundlesToDelete = myBundlesList.selectedValuesList.filterIsInstance<JCheckBox>()
              if (bundlesToDelete.isNotEmpty()) {
                val message = bundlesToDelete.joinToString(separator = "\n") { it.text }
                if (yesNo(TextMateBundle.message("textmate.remove.title", bundlesToDelete.size), message)
                    .yesText(CommonBundle.message("button.remove"))
                    .noText(CommonBundle.getCancelButtonText())
                    .icon(null)
                    .ask(myBundlesList)) {
                  ListUtil.removeSelectedItems(myBundlesList)
                }
              }
            })
            .setAddAction {
              val fileToSelect = PropertiesComponent.getInstance().getValue(TEXTMATE_LAST_ADDED_BUNDLE)?.let { lastAddedBundlePath ->
                LocalFileSystem.getInstance().findFileByPath(lastAddedBundlePath)
              }
              val chooserDescriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
              val bundleDirectories = FileChooser.chooseFiles(chooserDescriptor, myBundlesList, null, fileToSelect)
              if (bundleDirectories.isNotEmpty()) {
                var errorMessage: String? = null
                for (bundleDirectory in bundleDirectories) {
                  PropertiesComponent.getInstance().setValue(TEXTMATE_LAST_ADDED_BUNDLE, bundleDirectory.path)
                  val readBundleProcess = ThrowableComputable<TextMateBundleReader?, Exception> {
                    TextMateService.getInstance().readBundle(bundleDirectory.toNioPath())
                  }
                  val bundleReader = runCatching {
                    ProgressManager.getInstance()
                      .runProcessWithProgressSynchronously(readBundleProcess, TextMateBundle.message("button.add.bundle"), true, null)
                  }.getOrNull()
                  if (bundleReader != null) {
                    val bundleDirectoryPath = bundleDirectory.path
                    var alreadyAdded = false
                    for (i in 0 until myBundlesList.itemsCount) {
                      val item = myBundlesList.getItemAt(i)
                      if (item != null && FileUtil.toSystemIndependentName(bundleDirectoryPath) == item.path) {
                        myBundlesList.clearSelection()
                        myBundlesList.selectedIndex = i
                        UIUtil.scrollListToVisibleIfNeeded(myBundlesList)
                        alreadyAdded = true
                        break
                      }
                    }
                    if (!alreadyAdded) {
                      val item = TextMateConfigurableBundle(bundleReader.bundleName, bundleDirectoryPath, enabled = true, builtin = false)
                      myBundlesList.addItem(item, item.name, true)
                    }
                  }
                  else {
                    errorMessage = TextMateBundle.message("message.textmate.bundle.error", bundleDirectory.presentableUrl)
                  }
                }
                if (errorMessage != null) {
                  Messages.showErrorDialog(errorMessage, TextMateBundle.message("title.textmate.bundle.error"))
                }
              }
            }
            .setRemoveActionUpdater {
              for (index in myBundlesList.selectedIndices) {
                val bean = myBundlesList.getItemAt(index)
                if (bean!!.builtin) {
                  return@setRemoveActionUpdater false
                }
              }
              true
            }
            .disableUpDownActions()
            .createPanel())
          .align(Align.FILL)
      }.resizableRow()
    }
  }

  fun isModified(bundles: Set<TextMateConfigurableBundle>): Boolean {
    return getState() != bundles
  }

  companion object {
    private const val TEXTMATE_LAST_ADDED_BUNDLE = "textmate.last.added.bundle"
  }

  override fun dispose() {
  }
}
