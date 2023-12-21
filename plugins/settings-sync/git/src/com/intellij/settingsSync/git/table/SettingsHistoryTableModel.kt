package com.intellij.settingsSync.git.table

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.settingsSync.git.record.ChangeRecord
import com.intellij.settingsSync.git.record.HistoryRecord
import com.intellij.settingsSync.git.record.RecordService
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.VisiblePackChangeListener
import com.intellij.vcs.log.visible.VisiblePackRefresher
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

internal class SettingsHistoryTableModel(val logData: VcsLogData, refresher: VisiblePackRefresher) : AbstractTableModel() {
  companion object {
    private val logger = logger<SettingsHistoryTableModel>()
  }

  private val recordService = RecordService()

  lateinit var table: SettingsHistoryTable
  private var rows = listOf<SettingsHistoryTableRow>()
  val expandedRows = mutableSetOf<Hash>()

  private val filter = ExpandedRowFilter()
  private val sorter = object : TableRowSorter<SettingsHistoryTableModel>(this) {}.apply { rowFilter = filter }

  private val commitDetailsGetter = logData.commitDetailsGetter

  init {
    refresher.addVisiblePackChangeListener(VisiblePackChangeListener { visiblePack ->
      val historyRecords = getHistoryRecords(visiblePack)
      runInEdt {
        rows = historyRecords.mapNotNull { buildRows(it) }.flatten()
        sorter.sort()
      }
    })
  }

  fun bindTable(table: SettingsHistoryTable) {
    this.table = table
    table.rowSorter = sorter
  }

  private fun getHistoryRecords(visiblePack: VisiblePack): List<HistoryRecord> {
    val commitDetails = getAllCommits(visiblePack)
    val historyRecords = commitDetails.withIndex()
      .map { recordService.readRecord(it.value, it.index == commitDetails.lastIndex, it.index == 0, commitDetails) }
      .toMutableList()
    return historyRecords
  }

  fun toggleRowExpanding(row: SettingsHistoryTableRow) {
    if (row is TitleRow) {
      val recordId = row.record.id
      if (expandedRows.contains(recordId)) {
        expandedRows.remove(recordId)
      }
      else {
        expandedRows.add(recordId)
      }
      sorter.sort()
    }
  }

  private fun buildRows(record: HistoryRecord): List<SettingsHistoryTableRow>? {
    record as ChangeRecord // TODO support mergeRecords in future
    val fileRows = buildFileRowsForRecord(record)
    if (fileRows.isEmpty()) return null

    val result = mutableListOf<SettingsHistoryTableRow>(TitleRow(record))
    if (record.build.isNotBlank() && record.host.isNotBlank()) result.add(SubtitleRow(record))
    result.addAll(fileRows)
    if (record.position == HistoryRecord.RecordPosition.TOP || record.position == HistoryRecord.RecordPosition.MIDDLE) {
      result.add(SeparatorRow(record))
    }
    return result
  }

  private fun buildFileRowsForRecord(record: ChangeRecord): List<FileRow> {
    return record.changes
      .mapNotNull { change ->
        change.virtualFile?.let { file -> FileRow(file, change, record) }
      }
  }

  private fun getAllCommits(visiblePack: VisiblePack): List<VcsFullCommitDetails> {
    val commits = mutableListOf<VcsFullCommitDetails>()
    val rowsTotal = visiblePack.visibleGraph.visibleCommitCount

    val commitIndexes = (0 until rowsTotal).mapNotNull { visiblePack.visibleGraph.getRowInfo(it).getCommit() }
    try {
      commitDetailsGetter.loadCommitsDataSynchronously(commitIndexes, ProgressManager.getGlobalProgressIndicator()
                                                                      ?: EmptyProgressIndicator()) { _, commitDetails ->
        commits.add(commitDetails)
      }
    }
    catch (e: VcsException) {
      logger.error("Failed to load commit data", e)
    }

    return commits
  }

  override fun getRowCount(): Int {
    return rows.count()
  }

  override fun getColumnCount(): Int {
    return 3
  }

  override fun getColumnName(columnIndex: Int): String {
    return "" // no column name should be visible
  }

  override fun getColumnClass(columnIndex: Int): Class<*> {
    return ChangeRecord::class.java
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
    return false
  }

  override fun getValueAt(rowIndex: Int, columnIndex: Int): SettingsHistoryTableRow {
    return rows[rowIndex]
  }

  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
  }
}