// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.ui

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.util.ui.ListTableModel
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector
import javax.swing.ListSelectionModel
import javax.swing.SortOrder

class ConnectorTable : ListTableWithButtons<MavenServerConnector>() {

  val stop = object : AnAction(MavenConfigurableBundle.message("connector.ui.stop"), null, AllIcons.Debugger.KillProcess) {
    override fun actionPerformed(e: AnActionEvent) {
      MavenActionsUsagesCollector.trigger(e.project, MavenActionsUsagesCollector.KILL_MAVEN_CONNECTOR)
      val connector = tableView.selectedObject ?: return
      MavenServerManager.getInstance().shutdownConnector(connector, true)
      this@ConnectorTable.refreshValues()
    }

    override fun update(e: AnActionEvent) {
      val connector = tableView.selectedObject
      e.presentation.isEnabled = connector?.state == MavenServerConnector.State.RUNNING
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }
  val refresh = object : AnAction(MavenConfigurableBundle.message("connector.ui.refresh"), null, AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
      this@ConnectorTable.tableView.setModelAndUpdateColumns(createListModel())
      this@ConnectorTable.setModified()
    }
  }

  init {
    tableView.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
  }

  override fun createListModel(): ListTableModel<MavenServerConnector> {
    val project = TableColumn(MavenConfigurableBundle.message("connector.ui.project")) { it.project?.name?: "!Indexer" }
    val jdk = TableColumn(MavenConfigurableBundle.message("connector.ui.jdk")) { it.jdk.name }
    val vmopts = TableColumn(MavenConfigurableBundle.message("connector.ui.vmOptions")) { it.vmOptions }
    val dir = TableColumn(MavenConfigurableBundle.message("connector.ui.dir")) { it.multimoduleDirectories.joinToString(separator = ",") }
    val maven = TableColumn(
      MavenConfigurableBundle.message("connector.ui.maven")) { "${it.mavenDistribution.version} ${it.mavenDistribution.mavenHome}" }
    val state = TableColumn(
      MavenConfigurableBundle.message("connector.ui.state")) { it.state.toString() }
    val type = TableColumn(
      MavenConfigurableBundle.message("connector.ui.type")) { it.supportType }
    val columnInfos: Array<TableColumn> = arrayOf(project, dir, type, maven, state)
    return ListTableModel<MavenServerConnector>(columnInfos, MavenServerManager.getInstance().allConnectors.toList(), 3,
                                                SortOrder.DESCENDING)
  }

  override fun createExtraToolbarActions(): Array<AnAction> {
    return arrayOf(refresh, stop)
  }

  private class TableColumn(@NlsContexts.ColumnName name: String,
                            val supplier: (MavenServerConnector) -> String) : ElementsColumnInfoBase<MavenServerConnector>(
    name) {

    override fun getDescription(element: MavenServerConnector?): String? = null

    override fun valueOf(item: MavenServerConnector) = supplier(item)

  }

  override fun createElement(): MavenServerConnector? {
    return null
  }

  override fun isEmpty(element: MavenServerConnector?): Boolean {
    return element == null
  }

  override fun canDeleteElement(selection: MavenServerConnector): Boolean {
    return false
  }

  override fun createRemoveAction(): AnActionButtonRunnable? {
    return null
  }

  override fun createAddAction(): AnActionButtonRunnable? {
    return null
  }

  override fun cloneElement(variable: MavenServerConnector?): MavenServerConnector? {
    return null
  }
}


