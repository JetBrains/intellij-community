// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.ui

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnActionButton
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.util.ui.ListTableModel
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector
import javax.swing.ListSelectionModel
import javax.swing.SortOrder

class ConnectorTable : ListTableWithButtons<MavenServerConnector>() {

  val stop = object : AnActionButton(MavenConfigurableBundle.message("connector.ui.stop"), AllIcons.Debugger.KillProcess) {
    override fun actionPerformed(e: AnActionEvent) {
      MavenActionsUsagesCollector.trigger(e.project, MavenActionsUsagesCollector.ActionID.KillMavenConnector)
      val connector = tableView.selectedObject ?: return;
      connector.shutdown(true)
      this@ConnectorTable.refreshValues()
    }

    override fun updateButton(e: AnActionEvent) {
      val connector = tableView.selectedObject
      isEnabled = connector?.state == MavenServerConnector.State.RUNNING
    }
  }
  val refresh = object : AnActionButton(MavenConfigurableBundle.message("connector.ui.refresh"), AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
      this@ConnectorTable.tableView.setModelAndUpdateColumns(createListModel())
      this@ConnectorTable.setModified()
    }
  }

  init {
    tableView.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    tableView.selectionModel.addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      val connector = tableView.selectedObject
      stop.isEnabled = connector?.state == MavenServerConnector.State.RUNNING
    }
  }

  override fun createListModel(): ListTableModel<MavenServerConnector> {
    val project = TableColumn(MavenConfigurableBundle.message("connector.ui.project")) { it.project.name }
    val jdk = TableColumn(MavenConfigurableBundle.message("connector.ui.jdk")) { it.jdk.name }
    val vmopts = TableColumn(MavenConfigurableBundle.message("connector.ui.vmOptions")) { it.vmOptions }
    val dir = TableColumn(MavenConfigurableBundle.message("connector.ui.dir")) { it.multimoduleDirectory }
    val maven = TableColumn(
      MavenConfigurableBundle.message("connector.ui.maven")) { "${it.mavenDistribution.version} ${it.mavenDistribution.mavenHome}" }
    val state = TableColumn(
      MavenConfigurableBundle.message("connector.ui.state")) { it.state.toString() }
    val type = TableColumn(
      MavenConfigurableBundle.message("connector.ui.type")) { it.supportType }
    val columnInfos = arrayOf<TableColumn>(project, dir, type, maven, state)
    return ListTableModel<MavenServerConnector>(columnInfos, MavenServerManager.getInstance().allConnectors.toList(), 3,
                                                SortOrder.DESCENDING);
  }

  override fun createExtraActions(): Array<AnActionButton> {
    return arrayOf(refresh, stop)
  }

  private class TableColumn(name: String, val supplier: (MavenServerConnector) -> String) : ElementsColumnInfoBase<MavenServerConnector>(
    name) {

    override fun getDescription(element: MavenServerConnector?): String? = null;

    override fun valueOf(item: MavenServerConnector) = supplier(item)

  }

  override fun createElement(): MavenServerConnector? {
    return null;
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


