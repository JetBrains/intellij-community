package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.internal.statistic.local.FileTypeUsageSummaryProvider
import com.intellij.internal.statistic.local.TestFileTypeUsageSummaryProvider
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiFileSystemItem
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId


internal abstract class SearchEverywhereBaseFileFeaturesProviderTest<T : SearchEverywhereElementFeaturesProvider>(providerClass: Class<T>)
  : HeavyFeaturesProviderTestCase<T>(providerClass) {

  protected val testFile: PsiFileSystemItem by lazy {
    val dir = createTempDir("testFileDir")
    createTextFileInDirectory(dir, "testFile.txt").toPsi()
  }

  protected val mockedFileStatsProvider
    get() = project.service<FileTypeUsageSummaryProvider>() as TestFileTypeUsageSummaryProvider

  protected val currentTime: Long
  protected val lastMinute: Long
  protected val lastHour: Long
  protected val lastDay: Long
  protected val lastMonth: Long

  init {
    val localDt = LocalDateTime.of(2020, 12, 31, 23, 59)
    localDt.atZone(ZoneId.systemDefault()).let {
      currentTime = it.toInstant().toEpochMilli()
      lastMinute = it.minusMinutes(1).toInstant().toEpochMilli()
      lastHour = it.minusHours(1).toInstant().toEpochMilli()
      lastDay = it.minusDays(1).toInstant().toEpochMilli()
      lastMonth = it.minusWeeks(4).toInstant().toEpochMilli()
    }
  }

  private fun createTextFileInDirectory(parentDir: File, filename: String): VirtualFile {
    val vfManager = VirtualFileManager.getInstance()

    val file = PsiFileFactory.getInstance(project).createFileFromText(filename, PlainTextFileType.INSTANCE, "")
    val psiDirectory = parentDir.toPsi()
    WriteAction.computeAndWait<Unit, Nothing> {
      psiDirectory.add(file)
    }

    return vfManager.refreshAndFindFileByUrl(psiDirectory.virtualFile.url + "/${file.name}")!!
  }

  override fun tearDown() {
    try {
      EditorHistoryManager.getInstance(project).removeAllFiles()
      mockedFileStatsProvider.clearStats()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}
