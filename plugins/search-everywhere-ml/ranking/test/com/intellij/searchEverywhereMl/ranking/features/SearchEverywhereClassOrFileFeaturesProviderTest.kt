package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.mock.MockPsiFile
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.ALL_INITIAL_LETTERS_MATCH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.DIRECTORY_DEPTH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USAGE_RATIO_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USED_IN_LAST_DAY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USED_IN_LAST_HOUR_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USED_IN_LAST_MONTH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_OPENED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_SAME_MODULE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.PACKAGE_DISTANCE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.PACKAGE_DISTANCE_NORMALIZED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.RECENT_INDEX_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.TIME_SINCE_LAST_MODIFICATION_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.WAS_MODIFIED_IN_LAST_DAY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY
import com.intellij.util.Time
import java.nio.charset.StandardCharsets


internal class SearchEverywhereClassOrFileFeaturesProviderTest
  : SearchEverywhereBaseFileFeaturesProviderTest<SearchEverywhereClassOrFileFeaturesProvider>(
  SearchEverywhereClassOrFileFeaturesProvider::class.java) {

  fun `test file type usage ratio for the most popular file type`() {
    mockedFileStatsProvider
      .setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(7, lastDay))
      .setStats("Python", FileTypeUsageSummary(2, lastDay))
      .setStats("XML", FileTypeUsageSummary(1, lastMinute))

    val expectedValues = listOf(
      FILETYPE_USAGE_RATIO_DATA_KEY.with(0.7),
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY.with(1.0),
      FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY.with(7.0),
    )

    checkThatFeatures()
      .ofElement(testFile)
      .isEqualTo(expectedValues)
  }

  fun `test file type usage ratio for the least popular file type`() {
    mockedFileStatsProvider
      .setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastDay))
      .setStats("Python", FileTypeUsageSummary(2, lastDay))
      .setStats("XML", FileTypeUsageSummary(7, lastMinute))

    val expectedValues = listOf(
      FILETYPE_USAGE_RATIO_DATA_KEY.with(0.1),
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY.with(roundDouble(1.0 / 7.0)),
      FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY.with(1.0),
    )

    checkThatFeatures()
      .ofElement(testFile)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastMinute() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastMinute))

    val expectedValues = listOf(
      FILETYPE_USAGE_RATIO_DATA_KEY.with(1.0),
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY.with(currentTime - lastMinute),

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY.with(true),
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY.with(true),
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY.with(true),
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastHour() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastHour))

    val expectedValues = listOf(
      FILETYPE_USAGE_RATIO_DATA_KEY.with(1.0),
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY.with(currentTime - lastHour),

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY.with(false),
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY.with(true),
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY.with(true),
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastDay() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastDay))

    val expectedValues = listOf(
      FILETYPE_USAGE_RATIO_DATA_KEY.with(1.0),
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY.with(currentTime - lastDay),

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY.with(false),
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY.with(false),
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY.with(true),
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastMonth() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastMonth))

    val expectedValues = listOf(
      FILETYPE_USAGE_RATIO_DATA_KEY.with(1.0),
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY.with(currentTime - lastMonth),

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY.with(false),
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY.with(false),
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY.with(false),
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeNeverUsed() {
    mockedFileStatsProvider.clearStats()

    // We expect these features will not be reported
    val features = listOf(
      FILETYPE_USAGE_RATIO_DATA_KEY,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withoutFeatures(features)
  }

  fun testFileInDifferentModule() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module("testModuleA") {
      source {
        file("fileA.java") { openedFile = it }
      }
    }

    module("testModuleB") {
      source {
        file("fileB.java") { foundFile = it }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val psiFile = foundFile!!.toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(false)
  }

  fun testFileInTheSameModule() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        file("fileA.java") { openedFile = it }
        file("fileB.java") { foundFile = it }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val psiFile = foundFile!!.toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(true)
  }

  fun `test if same module is reported for directories`() {
    var openedFile: VirtualFile? = null
    var foundDirectory: VirtualFile? = null

    module {
      source {
        file("fileA.java") { openedFile = it }
        directory("directory") { foundDirectory = it }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val psiFile = foundDirectory!!.toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(true)

  }

  fun `test package distance is reported for directories`() {
    var openedFile: VirtualFile? = null
    var foundDirectory: VirtualFile? = null

    module {
      source {
        createPackage("a.b.c") {
          file("fileA.txt") { openedFile = it }
          directory("foundDirectory") { foundDirectory = it }
        }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(1),  // The found directory is considered a subpackage, hence the distance should be 1
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(roundDouble(1 / (3 + 4).toDouble())),
    )

    val psiFile = foundDirectory!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 0 when same package`() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b.c") {
          file("fileA.txt") { openedFile = it }
          file("fileB.txt") { foundFile = it }
        }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(0),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(0.0),
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 1 when in child package`() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b.c.d") {
          file("fileA.txt") { openedFile = it }
          createPackage("e") {
            file("fileB.txt") { foundFile = it }
          }
        }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(1),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(roundDouble(1 / (4 + 5).toDouble())),
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 1 when in parent package`() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b.c") {
          file("fileB.txt") { foundFile = it }
          createPackage("d") {
            file("fileA.txt") { openedFile = it }
          }
        }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(1),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(roundDouble(1 / (4 + 3).toDouble())),
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance on a parent of different group`() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b") {
          createPackage("c.d") {
            file("fileA.txt") { openedFile = it }
          }
          createPackage("x") {
            file("fileB.txt") { foundFile = it }
          }
        }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(3),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(roundDouble(3 / (4 + 3).toDouble())),
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance on a child of different group`() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b") {
          createPackage("c.d") {
            file("fileA.txt") { openedFile = it }
          }
          createPackage("x.y") {
            file("fileB.txt") { foundFile = it }
          }
        }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(4),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(roundDouble(4 / (4 + 4).toDouble()))
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance when root is different`() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b.c.d") {
          file("fileA.txt") { openedFile = it }
        }
        createPackage("x.y") {
          file("fileB.txt") { foundFile = it }
        }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(6),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(roundDouble(6 / (4 + 2).toDouble())),
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 0 when files are not in packages`() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        file("fileA.txt") { openedFile = it }
        file("fileB.txt") { foundFile = it }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(0),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(0.0),
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance when one file is not in a package`() {
    var openedFile: VirtualFile? = null
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b.c.d") {
          file("fileA.txt") { openedFile = it }
        }
        file("fileB.txt") { foundFile = it }
      }
    }

    FileEditorManager.getInstance(project).openFile(openedFile!!, true)

    val expected = listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(4),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(roundDouble(4 / (4 + 0).toDouble())),
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test root distance for file in package`() {
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b.c.d") {
          file("file.txt") { foundFile = it }
        }
      }
    }

    val expected = listOf(
      DIRECTORY_DEPTH_DATA_KEY.with(4)
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test root distance is 0 for root files`() {
    var foundFile: VirtualFile? = null

    module {
      source {
        file("file.txt") { foundFile = it }
      }
    }

    val expected = listOf(
      DIRECTORY_DEPTH_DATA_KEY.with(0)
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test root distance is reported for directories`() {
    var foundFile: VirtualFile? = null

    module {
      source {
        createPackage("a.b.c") {
          directory("dir") { foundFile = it }
        }
      }
    }

    val expected = listOf(
      DIRECTORY_DEPTH_DATA_KEY.with(3)
    )

    val psiFile = foundFile!!.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test modified in last minute`() {
    val file = createFileWithModTimestamp(lastMinute)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - lastMinute),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun `test modified in last hour`() {
    val file = createFileWithModTimestamp(lastHour)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - lastHour),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun `test modified in last day`() {
    val file = createFileWithModTimestamp(lastDay)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - lastDay),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun `test modified in last month`() {
    val file = createFileWithModTimestamp(lastMonth)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - lastMonth),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun `test modified later than month`() {
    val modTime = lastMonth - Time.DAY
    val file = createFileWithModTimestamp(modTime)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - modTime),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(false),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun `test recent index of never opened file is -1`() {
    prepareForRecentIndexTest()

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(testFile)
      .isEqualTo(-1)
  }

  fun `test most recent file index is 1`() {
    val openedFiles = prepareForRecentIndexTest()

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(openedFiles.last()) // Last opened file (i.e. the most recent)
      .isEqualTo(1)
  }

  fun `test oldest file index is equal to index length`() {
    val openedFiles = prepareForRecentIndexTest()
    val expectedIndex = openedFiles.size

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(openedFiles.first()) // First opened file (i.e. the oldest)
      .isEqualTo(expectedIndex)
  }

  fun `test is opened`() {
    checkThatFeature(IS_OPENED_DATA_KEY)
      .ofElement(testFile)
      .changes(false, true)
      .after { FileEditorManager.getInstance(project).openFile(it.virtualFile, false) }
  }

  fun `test all initial letters match is true on PascalCase`() {
    val file = MockPsiFile(MockVirtualFile("PascalCaseFile.kt"), psiManager)
    checkThatFeature(ALL_INITIAL_LETTERS_MATCH_DATA_KEY)
      .ofElement(file)
      .withQuery("PCF")
      .isEqualTo(true)
  }

  fun `test all initial letters match is true on camelCase`() {
    val file = MockPsiFile(MockVirtualFile("camelCaseFile.kt"), psiManager)
    checkThatFeature(ALL_INITIAL_LETTERS_MATCH_DATA_KEY)
      .ofElement(file)
      .withQuery("CCF")
      .isEqualTo(true)
  }

  fun `test all initial letters match is true on snake_case`() {
    val file = MockPsiFile(MockVirtualFile("snake_case_file.py"), psiManager)
    checkThatFeature(ALL_INITIAL_LETTERS_MATCH_DATA_KEY)
      .ofElement(file)
      .withQuery("SCF")
      .isEqualTo(true)
  }

  private fun createFileWithModTimestamp(modificationTimestamp: Long): PsiFileSystemItem {
    val mockVirtualFile = object : MockVirtualFile("file.txt") {
      override fun getTimeStamp(): Long {
        return modificationTimestamp
      }
    }

    return MockPsiFile(mockVirtualFile, psiManager)
  }

  private fun prepareForRecentIndexTest(numberOfFiles: Int = 3): List<PsiFileSystemItem> {
    closeAllOpenedFiles()
    EditorHistoryManager.getInstance(project).removeAllFiles()

    val editor = FileEditorManager.getInstance(project)
    return (1..numberOfFiles).map {
      val file = createTempVirtualFile("file$it.txt", null, "", StandardCharsets.UTF_8)
      MockPsiFile(file, PsiManager.getInstance(project))
    }.onEach { file -> editor.openFile(file.virtualFile, true) }
  }

  private fun closeAllOpenedFiles() {
    val editor = FileEditorManager.getInstance(project)
    editor.openFiles.forEach { editor.closeFile(it) }
  }
}
