package com.intellij.settingsSync.git.table

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.settingsSync.git.record.ChangeRecord

internal sealed class SettingsHistoryTableRow(val record: ChangeRecord)

internal class TitleRow(record: ChangeRecord) : SettingsHistoryTableRow(record)
internal class SubtitleRow(record: ChangeRecord) : SettingsHistoryTableRow(record)
internal class FileRow(val virtualFile: VirtualFile, val change: Change, record: ChangeRecord) : SettingsHistoryTableRow(record)
internal class SeparatorRow(record: ChangeRecord) : SettingsHistoryTableRow(record)
