package com.intellij.settingsSync.jba

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.actions.ShowLogAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.jba.auth.JBAAuthService
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.io.Compressor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.cloudconfig.FileVersionInfo
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

internal class SettingsSyncTroubleshootingAction : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isSettingsSyncEnabledInSettings() &&
                                         RemoteCommunicatorHolder.getAuthService() is JBAAuthService
  }

  override fun actionPerformed(e: AnActionEvent) {
    val remoteCommunicator = RemoteCommunicatorHolder.getRemoteCommunicator() ?: run {
      Messages.showErrorDialog(e.project,
                               "No remote communicator available",
                               SettingsSyncBundle.message("troubleshooting.dialog.title"))
      return
    }
    if (remoteCommunicator !is CloudConfigServerCommunicator) {
      Messages.showErrorDialog(e.project,
                               SettingsSyncBundle.message("troubleshooting.dialog.error.wrong.configuration", remoteCommunicator::class),
                               SettingsSyncBundle.message("troubleshooting.dialog.title"))
      return
    }

    try {
      val fileStructure =
        ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
          collectFileStructure(remoteCommunicator)
        }, SettingsSyncBundle.message("troubleshooting.loading.info.progress.dialog.title"), false, e.project)
      TroubleshootingDialog(e.project, remoteCommunicator, fileStructure).show()
    }
    catch (ex: Exception) {
      LOG.error(ex)
      if (Messages.OK == Messages.showOkCancelDialog(e.project,
                                                     SettingsSyncBundle.message("troubleshooting.dialog.error.check.log.file.for.errors"),
                                                     SettingsSyncBundle.message("troubleshooting.dialog.error.loading.info.failed"),
                                                     ShowLogAction.getActionName(), CommonBundle.getCancelButtonText(), null)) {
        ShowLogAction.showLog()
      }
    }
  }

  private data class Version(val filePath: @NlsSafe String, val fileVersion: FileVersionInfo, val snapshot: SettingsSnapshot?)

  private fun collectFileStructure(remoteCommunicator: CloudConfigServerCommunicator): TreeNode.Root {
    return processFileOrDir("/", remoteCommunicator) as TreeNode.Root
  }

  private fun processFileOrDir(path: String, remoteCommunicator: CloudConfigServerCommunicator): TreeNode {
    val client = remoteCommunicator.client
    try {
      val children = client.list(path)
      if (children.isEmpty()) {
        if (path == "/") { // the root is empty => no files on the server
          return TreeNode.Root(emptyList())
        }
        val fileVersion = client.getLatestVersion(path)
        val version = getVersion(path, fileVersion, remoteCommunicator)
        return TreeNode.Leaf(version)
      }
      else {
        val childNodes = mutableListOf<TreeNode>()
        for (child in children) {
          val childPath = if (path == "/") child else "${path.trim('/')}/$child"
          childNodes += processFileOrDir(childPath, remoteCommunicator)
        }
        return if (path == "/") TreeNode.Root(childNodes) else TreeNode.Branch(childNodes)
      }
    }
    catch (e: FileNotFoundException) {
      // The cloud-config library supports only 1 level of nested folders
      // It throws a FNFE from client.list(path) when path is already on the level 1 ('/idea/some_file')
      // for us, it means that it is a file
      val fileVersion = client.getLatestVersion(path)
      val version = getVersion(path, fileVersion, remoteCommunicator)
      return TreeNode.Leaf(version)
    }
  }

  private sealed class TreeNode {
    open class Branch(val children: List<TreeNode>) : TreeNode()
    class Root(children: List<TreeNode>) : Branch(children)
    class Leaf(val version: Version) : TreeNode()
  }

  private fun getVersion(filePath: String, version: FileVersionInfo, remoteCommunicator: CloudConfigServerCommunicator): Version {
    val stream = remoteCommunicator.downloadSnapshot(filePath, version)!!
    if (filePath.endsWith(".zip")) {
      return Version(filePath, version, extractSnapshotFromZipStream(stream))
    }
    else {
      return Version(filePath, version, null)
    }
  }

  private class TroubleshootingDialog(val project: Project?,
                                      val remoteCommunicator: CloudConfigServerCommunicator,
                                      val rootNode: TreeNode.Branch
  ) : DialogWrapper(project, true) {

    val userData = RemoteCommunicatorHolder.getCurrentUserData()

    init {
      title = SettingsSyncBundle.message("troubleshooting.dialog.title")
      init()
    }

    override fun createActions(): Array<Action> {
      setCancelButtonText(CommonBundle.getCloseButtonText())
      cancelAction.putValue(DEFAULT_ACTION, true)
      return arrayOf(cancelAction)
    }

    override fun createCenterPanel(): JComponent {
      return panel {
        statusRow()
        serverUrlRow()
        loginNameRow(userData)
        emailRow(userData)
        appInfoRow()

        row(SettingsSyncBundle.message("troubleshooting.dialog.files")) {}
        generateFileSubTree(this, rootNode)
      }
    }

    private fun generateFileSubTree(panel: Panel, node: TreeNode) {
      fun generateSubTreesForChildren(node: TreeNode.Branch) {
        for (child in node.children) {
          generateFileSubTree(panel, child)
        }
      }

      when (node) {
        is TreeNode.Root -> generateSubTreesForChildren(node)
        is TreeNode.Branch -> panel.indent {
          generateSubTreesForChildren(node)
        }
        is TreeNode.Leaf -> {
          val itIsSettingsSnapshotFile = node.version.filePath.endsWith(".zip")
          panel.versionRow(node.version, showHistoryButton = itIsSettingsSnapshotFile, showDeleteButton = true)
        }
      }
    }

    private fun Panel.statusRow() =
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.local.status.label"))
        label(if (SettingsSyncSettings.Companion.getInstance().syncEnabled) IdeBundle.message("plugins.configurable.enabled")
              else IdeBundle.message("plugins.configurable.disabled"))
      }.layout(RowLayout.PARENT_GRID)

    private fun Panel.serverUrlRow() =
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.server.url.label"))
        copyableLabel(CloudConfigServerCommunicator.defaultUrl)
      }.layout(RowLayout.PARENT_GRID)

    private fun Panel.loginNameRow(userData: SettingsSyncUserData?) =
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.login.label"))
        copyableLabel(userData?.name)
      }.layout(RowLayout.PARENT_GRID)

    private fun Panel.emailRow(userData: SettingsSyncUserData?) =
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.email.label"))
        copyableLabel(userData?.email)
      }.layout(RowLayout.PARENT_GRID)

    private fun Panel.appInfoRow() {
      val appInfo = getLocalApplicationInfo()
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.applicationId.label"))
        copyableLabel(appInfo.applicationId)
      }.layout(RowLayout.PARENT_GRID)
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.username.label"))
        copyableLabel(appInfo.userName)
      }.layout(RowLayout.PARENT_GRID)
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.hostname.label"))
        copyableLabel(appInfo.hostName)
      }.layout(RowLayout.PARENT_GRID)
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.configFolder.label"))
        copyableLabel(appInfo.configFolder)
      }.layout(RowLayout.PARENT_GRID)
    }

    private fun String.shorten() = StringUtil.shortenTextWithEllipsis(this, 12, 5, true)

    private fun Panel.versionRow(version: Version, showHistoryButton: Boolean, showDeleteButton: Boolean) = row {
      val split = version.filePath.split("/")
      val folder = split.subList(0, split.size - 1).joinToString("/").run {
        if (isNotEmpty()) "$this/" else ""
      }
      val file = split[split.size - 1]
      label("<html><b>$folder</b>$file</html>")
      copyableLabel(formatDate(version.fileVersion.modifiedDate))
      copyableLabel(version.fileVersion.versionId.shorten())

      val snapshot = version.snapshot
      if (snapshot != null) {
        label(SettingsSyncBundle.message("troubleshooting.dialog.machineInfo.label"))
        val appInfo = snapshot.metaInfo.appInfo
        val text = if (appInfo != null) {
          val appId = appInfo.applicationId
          val thisOrThat = if (appId == SettingsSyncLocalSettings.Companion.getInstance().applicationId) "[this]  " else "[other]"
          "$thisOrThat ${appId.toString().shorten()} - ${appInfo.userName} - ${appInfo.hostName} - ${appInfo.configFolder}"
        }
        else {
          "Unknown"
        }
        copyableLabel(text)
      }

      actionButton(object : DumbAwareAction(AllIcons.Actions.Download) {
        override fun actionPerformed(e: AnActionEvent) {
          downloadVersion(version, e.project)
        }
      })

      if (showHistoryButton) {
        actionButton(object : DumbAwareAction(AllIcons.Vcs.History) {
          override fun actionPerformed(e: AnActionEvent) {
            showHistoryDialog(project, remoteCommunicator, version.filePath, userData?.name ?: "Unknown")
          }
        })
      }

      if (showDeleteButton) {
        button(SettingsSyncBundle.message("troubleshooting.dialog.delete.button")) {
          deleteFile(project, remoteCommunicator, version.filePath)
        }
      }
    }

    private fun downloadVersion(version: Version, project: Project?) {
      val file = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable<File?, Exception> {
        downloadToFile(version.filePath, version.fileVersion, remoteCommunicator)
      }, SettingsSyncBundle.message("troubleshooting.dialog.downloading.settings.from.server.progress.title"), false, project)

      if (file != null) {
        showFileDownloadedMessage(file, SettingsSyncBundle.message("troubleshooting.dialog.successfully.downloaded.message"))
      }
      else {
        if (Messages.OK == Messages.showOkCancelDialog(contentPane,
                                                       SettingsSyncBundle.message("troubleshooting.dialog.error.check.log.file.for.errors"),
                                                       SettingsSyncBundle.message("troubleshooting.dialog.error.download.file.failed"),
                                                       ShowLogAction.getActionName(), CommonBundle.getCancelButtonText(), null)) {
          ShowLogAction.showLog()
        }
      }
    }

    private fun showFileDownloadedMessage(file: File, @Nls message: String) {
      if (Messages.OK == Messages.showOkCancelDialog(contentPane, message, "",
                                                     RevealFileAction.getActionName(), CommonBundle.getCancelButtonText(), null)) {
        RevealFileAction.openFile(file)
      }
    }

    private fun showHistoryDialog(project: Project?,
                                  remoteCommunicator: CloudConfigServerCommunicator,
                                  filePath: String,
                                  loginName: String) {
      val history = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
        remoteCommunicator.fetchHistory(filePath).mapIndexed { index, version ->
          val snapshot = if (index < 10 && filePath.endsWith(".zip")) {
            val stream = remoteCommunicator.downloadSnapshot(filePath, version)!!
            extractSnapshotFromZipStream(stream)
          }
          else null
          Version(filePath, version, snapshot)
        }
      }, SettingsSyncBundle.message("troubleshooting.fetching.history.progress.title"), false, project)

      val dialogBuilder = DialogBuilder(contentPane)
        .title(SettingsSyncBundle.message("troubleshooting.settings.history.dialog.title", filePath))
      val historyPanel = panel {
        for (version in history) {
          versionRow(version, showHistoryButton = false, showDeleteButton = false).layout(RowLayout.PARENT_GRID)
        }
      }.withBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP))

      val button = JButton(SettingsSyncBundle.message("troubleshooting.dialog.download.full.history.button"))
      button.addActionListener {
        downloadFullHistory(project, remoteCommunicator, history, loginName)
      }

      val scrollPanel = JBScrollPane(historyPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
      val mainPanel = JBUI.Panels.simplePanel()
      mainPanel.add(scrollPanel, BorderLayout.CENTER)
      mainPanel.add(button, BorderLayout.SOUTH)

      dialogBuilder.centerPanel(mainPanel)
      dialogBuilder.addCloseButton()
      dialogBuilder.show()
    }

    private fun downloadFullHistory(project: Project?,
                                    remoteCommunicator: CloudConfigServerCommunicator,
                                    history: List<Version>,
                                    loginName: String) {
      val compoundZip = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
        val historyFileName = "settings-server-history-${FileUtil.sanitizeFileName(loginName)}-${formatDate(Date())}.zip"
        val zipFile = FileUtil.createTempFile(historyFileName, null)
        val indicator = ProgressManager.getInstance().progressIndicator
        indicator.isIndeterminate = false
        Compressor.Zip(zipFile).use { zip ->
          for ((step, version) in history.withIndex()) {
            indicator.checkCanceled()
            indicator.fraction = (step.toDouble() / history.size)

            val fileVersion = version.fileVersion
            val stream = remoteCommunicator.downloadSnapshot(version.filePath, fileVersion)
            if (stream != null) {
              val (fileName, extension) = getSnapshotFileNameAndExtension(version.filePath, fileVersion)
              zip.addFile("$fileName$extension", stream)
            }
            else {
              LOG.warn("Couldn't download snapshot for version made on ${fileVersion.modifiedDate}")
            }
          }
        }
        zipFile
      }, SettingsSyncBundle.message("troubleshooting.fetching.history.progress.title"), true, project)

      showFileDownloadedMessage(compoundZip, SettingsSyncBundle.message("troubleshooting.dialog.download.full.history.success.message"))
    }

    private fun deleteFile(project: Project?, remoteCommunicator: CloudConfigServerCommunicator, filePath: String) {
      val choice = Messages.showOkCancelDialog(contentPane,
                                               SettingsSyncBundle.message("troubleshooting.dialog.delete.confirmation.message"),
                                               SettingsSyncBundle.message("troubleshooting.dialog.delete.confirmation.title"),
                                               IdeBundle.message("button.delete"), CommonBundle.getCancelButtonText(), null)
      if (choice == Messages.OK) {
        try {
          ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
            SettingsSyncSettings.Companion.getInstance().syncEnabled = false
            remoteCommunicator.deleteFile(filePath)
          }, SettingsSyncBundle.message("troubleshooting.delete.file.from.server.progress.title"), false, project)
        }
        catch (e: Exception) {
          LOG.warn("Couldn't delete $filePath from server", e)
          Messages.showErrorDialog(contentPane, e.message, SettingsSyncBundle.message("troubleshooting.dialog.delete.confirmation.title"))
        }
      }
    }

    private fun Row.copyableLabel(@NlsSafe text: Any?) = cell(JBLabel(text.toString()).apply { setCopyable(true) })
  }

  companion object {
    val LOG = logger<SettingsSyncTroubleshootingAction>()
  }
}

private fun extractSnapshotFromZipStream(stream: InputStream): SettingsSnapshot? {
  val tempFile = FileUtil.createTempFile(UUID.randomUUID().toString(), null)
  try {
    FileUtil.writeToFile(tempFile, stream.readAllBytes())
    return SettingsSnapshot.extractFromZip(tempFile.toPath())
  }
  finally {
    FileUtil.delete(tempFile)
  }
}

private fun downloadToFile(filePath: String, version: FileVersionInfo, remoteCommunicator: CloudConfigServerCommunicator): File? {
  val stream = remoteCommunicator.downloadSnapshot(filePath, version) ?: return null
  try {
    val (fileName, extension) = getSnapshotFileNameAndExtension(filePath, version)
    val tempFile = FileUtil.createTempFile(fileName, extension)
    FileUtil.writeToFile(tempFile, stream.readAllBytes())
    return tempFile
  }
  catch (e: Throwable) {
    SettingsSyncTroubleshootingAction.LOG.error(e)
    return null
  }
}

private fun getSnapshotFileNameAndExtension(filePath: String, version: FileVersionInfo) : Pair<String, String> {
  val sanitizedName = filePath.replace('/', '-')  // idea/settings-sync-snapshot.zip -> idea-settings-sync-snapshot.zip
  val fileName = "${FileUtilRt.getNameWithoutExtension(sanitizedName)}-${formatDate(version.modifiedDate)}"
  val extension = FileUtilRt.getExtension(sanitizedName).run {
    if (this.isEmpty()) "" else ".$this"
  }
  return fileName to extension
}

private fun formatDate(date: Date) = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(date)