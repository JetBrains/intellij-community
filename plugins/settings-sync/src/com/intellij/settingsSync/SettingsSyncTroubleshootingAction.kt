package com.intellij.settingsSync

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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.ui.JBAccountInfoService
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.io.Compressor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.cloudconfig.FileVersionInfo
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

internal class SettingsSyncTroubleshootingAction : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isSettingsSyncEnabledByKey()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val remoteCommunicator = SettingsSyncMain.getInstance().getRemoteCommunicator()
    if (remoteCommunicator !is CloudConfigServerCommunicator) {
      Messages.showErrorDialog(e.project,
                               SettingsSyncBundle.message("troubleshooting.dialog.error.wrong.configuration", remoteCommunicator::class),
                               SettingsSyncBundle.message("troubleshooting.dialog.title"))
      return
    }

    try {
      val version =
        ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
          val fileVersion = remoteCommunicator.getLatestVersion()
          if (fileVersion == null) {
            null
          }
          else {
            val zip = downloadToZip(fileVersion, remoteCommunicator)
            Version(fileVersion, SettingsSnapshotZipSerializer.extractFromZip(zip!!.toPath()))
          }
        }, SettingsSyncBundle.message("troubleshooting.loading.info.progress.dialog.title"), false, e.project)
      TroubleshootingDialog(e.project, remoteCommunicator, version).show()
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

  private data class Version(val fileVersion: FileVersionInfo, val snapshot: SettingsSnapshot?)

  private class TroubleshootingDialog(val project: Project?,
                                      val remoteCommunicator: CloudConfigServerCommunicator,
                                      val latestVersion: Version?) : DialogWrapper(project, true) {

    val userData = SettingsSyncAuthService.getInstance().getUserData()

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

        if (latestVersion == null) {
          row {
            label(SettingsSyncBundle.message("troubleshooting.dialog.no.file.on.server", SETTINGS_SYNC_SNAPSHOT_ZIP))
          }
        }
        else {
          row { label(SettingsSyncBundle.message("troubleshooting.dialog.latest.version.label", SETTINGS_SYNC_SNAPSHOT_ZIP)).bold() }
          versionRow(latestVersion)

          row {
            button(SettingsSyncBundle.message("troubleshooting.dialog.show.history.button")) {
              showHistoryDialog(project, remoteCommunicator, userData?.loginName!!)
            }
            button(SettingsSyncBundle.message("troubleshooting.dialog.delete.button")) {
              deleteFile(project, remoteCommunicator)
            }
          }
        }
      }
    }

    private fun Panel.statusRow() =
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.local.status.label"))
        label(if (SettingsSyncSettings.getInstance().syncEnabled) IdeBundle.message("plugins.configurable.enabled")
              else IdeBundle.message("plugins.configurable.disabled"))
      }.layout(RowLayout.PARENT_GRID)

    private fun Panel.serverUrlRow() =
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.server.url.label"))
        copyableLabel(CloudConfigServerCommunicator.url)
      }.layout(RowLayout.PARENT_GRID)

    private fun Panel.loginNameRow(userData: JBAccountInfoService.JBAData?) =
      row {
        label(SettingsSyncBundle.message("troubleshooting.dialog.login.label"))
        copyableLabel(userData?.loginName)
      }.layout(RowLayout.PARENT_GRID)

    private fun Panel.emailRow(userData: JBAccountInfoService.JBAData?) =
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

    private fun String.shorten() = StringUtil.shortenTextWithEllipsis(this, 12, 0, true)

    private fun Panel.versionRow(version: Version) = row {
      label(SettingsSyncBundle.message("troubleshooting.dialog.version.date.label"))
      copyableLabel(formatDate(version.fileVersion.modifiedDate))

      label(SettingsSyncBundle.message("troubleshooting.dialog.version.id.label"))
      copyableLabel(version.fileVersion.versionId.shorten())

      val snapshot = version.snapshot
      if (snapshot != null) {
        label(SettingsSyncBundle.message("troubleshooting.dialog.machineInfo.label"))
        val appInfo = snapshot.metaInfo.appInfo
        val text = if (appInfo != null) {
          val appId = appInfo.applicationId
          val thisOrThat = if (appId == SettingsSyncLocalSettings.getInstance().applicationId) "[this]  " else "[other]"
          "$thisOrThat ${appId.toString().shorten()} - ${appInfo.userName} - ${appInfo.hostName} - ${appInfo.configFolder}"
        }
        else {
          "Unknown"
        }
        copyableLabel(text)
      }

      actionButton(object : DumbAwareAction(AllIcons.Actions.Download) {
        override fun actionPerformed(e: AnActionEvent) {
          downloadVersion(version.fileVersion, e.project)
        }
      })
    }

    private fun downloadVersion(version: FileVersionInfo, project: Project?) {
      val zipFile = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable<File?, Exception> {
        downloadToZip(version, remoteCommunicator)
      }, SettingsSyncBundle.message("troubleshooting.dialog.downloading.settings.from.server.progress.title"), false, project)

      if (zipFile != null) {
        showFileDownloadedMessage(zipFile, SettingsSyncBundle.message("troubleshooting.dialog.successfully.downloaded.message"))
      }
      else {
        if (Messages.OK == Messages.showOkCancelDialog(contentPane,
                                                       SettingsSyncBundle.message("troubleshooting.dialog.error.check.log.file.for.errors"),
                                                       SettingsSyncBundle.message("troubleshooting.dialog.error.download.zip.file.failed"),
                                                       ShowLogAction.getActionName(), CommonBundle.getCancelButtonText(), null)) {
          ShowLogAction.showLog()
        }
      }
    }

    private fun showFileDownloadedMessage(zipFile: File, @Nls message: String) {
      if (Messages.OK == Messages.showOkCancelDialog(contentPane, message, "",
                                                     RevealFileAction.getActionName(), CommonBundle.getCancelButtonText(), null)) {
        RevealFileAction.openFile(zipFile)
      }
    }

    private fun showHistoryDialog(project: Project?,
                                  remoteCommunicator: CloudConfigServerCommunicator,
                                  loginName: String) {
      val history = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
        remoteCommunicator.fetchHistory().mapIndexed { index, fileVersion ->
          val snapshot = if (index < 10) {
            val zip = downloadToZip(fileVersion, remoteCommunicator)
            SettingsSnapshotZipSerializer.extractFromZip(zip!!.toPath())
          }
          else null
          Version(fileVersion, snapshot)
        }
      }, SettingsSyncBundle.message("troubleshooting.fetching.history.progress.title"), false, project)

      val dialogBuilder = DialogBuilder(contentPane).title(SettingsSyncBundle.message("troubleshooting.settings.history.dialog.title"))
      val historyPanel = panel {
        for (version in history) {
          versionRow(version).layout(RowLayout.PARENT_GRID)
        }
      }.withBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP))

      val button = JButton(SettingsSyncBundle.message("troubleshooting.dialog.download.full.history.button"))
      button.addActionListener {
        downloadFullHistory(project, remoteCommunicator, history, loginName)
      }

      val scrollPanel = JBScrollPane(historyPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED)
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
        val fileName = "settings-server-history-${FileUtil.sanitizeFileName(loginName)}-${formatDate(Date())}.zip"
        val zipFile = FileUtil.createTempFile(fileName, null)
        val indicator = ProgressManager.getInstance().progressIndicator
        indicator.isIndeterminate = false
        Compressor.Zip(zipFile).use { zip ->
          for ((step, version) in history.withIndex()) {
            indicator.checkCanceled()
            indicator.fraction = (step.toDouble() / history.size)

            val fileVersion = version.fileVersion
            val stream = remoteCommunicator.downloadSnapshot(fileVersion)
            if (stream != null) {
              zip.addFile(getSnapshotFileName(fileVersion), stream)
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

    private fun deleteFile(project: Project?, remoteCommunicator: CloudConfigServerCommunicator) {
      val choice = Messages.showOkCancelDialog(contentPane,
                                               SettingsSyncBundle.message("troubleshooting.dialog.delete.confirmation.message"),
                                               SettingsSyncBundle.message("troubleshooting.dialog.delete.confirmation.title"),
                                               IdeBundle.message("button.delete"), CommonBundle.getCancelButtonText(), null)
      if (choice == Messages.OK) {
        try {
          ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
            SettingsSyncSettings.getInstance().syncEnabled = false
            remoteCommunicator.delete()
          }, SettingsSyncBundle.message("troubleshooting.delete.file.from.server.progress.title"), false, project)
        }
        catch (e: Exception) {
          LOG.warn("Couldn't delete $SETTINGS_SYNC_SNAPSHOT_ZIP from server", e)
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

private fun downloadToZip(version: FileVersionInfo, remoteCommunicator: CloudConfigServerCommunicator): File? {
  val stream = remoteCommunicator.downloadSnapshot(version) ?: return null
  try {
    val tempFile = FileUtil.createTempFile(getSnapshotFileName(version), null)
    FileUtil.writeToFile(tempFile, stream.readAllBytes())
    return tempFile
  }
  catch (e: Throwable) {
    SettingsSyncTroubleshootingAction.LOG.error(e)
    return null
  }
}

private fun getSnapshotFileName(version: FileVersionInfo) = "settings-sync-snapshot-${formatDate(version.modifiedDate)}.zip"

private fun formatDate(date: Date) = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(date)
