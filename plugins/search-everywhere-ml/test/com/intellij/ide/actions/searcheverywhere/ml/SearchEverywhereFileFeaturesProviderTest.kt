package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_DAY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_HOUR_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_MONTH_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_DIRECTORY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_EXACT_MATCH_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_EXCLUDED_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_FAVORITE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_IN_SOURCE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_IN_TEST_SOURCES_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_SAME_MODULE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.PACKAGE_DISTANCE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.PACKAGE_DISTANCE_NORMALIZED_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.PRIORITY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.RECENT_INDEX_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.TIME_SINCE_LAST_MODIFICATION_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_DAY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummaryProvider
import com.intellij.internal.statistic.local.TestFileTypeUsageSummaryProvider
import com.intellij.mock.MockPsiFile
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.util.Time
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId


internal class SearchEverywhereFileFeaturesProviderTest
  : HeavyFeaturesProviderTestCase<SearchEverywhereFileFeaturesProvider>(SearchEverywhereFileFeaturesProvider::class.java) {

  private val testFile: PsiFileSystemItem by lazy {
    val dir = createTempDir("testFileDir")
    File(dir, "testFile.txt").apply { createNewFile() }.toPsi()
  }

  private val mockedFileStatsProvider
    get() = project.service<FileTypeUsageSummaryProvider>() as TestFileTypeUsageSummaryProvider

  private val currentTime: Long
  private val lastMinute: Long
  private val lastHour: Long
  private val lastDay: Long
  private val lastMonth: Long

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

  fun testIsDirectory() {
    val directory = createTempDirectory().toPsi()

    checkThatFeature(IS_DIRECTORY_DATA_KEY)
      .ofElement(directory)
      .isEqualTo(true)
  }

  fun testFileIsNotDirectory() {
    checkThatFeature(IS_DIRECTORY_DATA_KEY)
      .ofElement(testFile)
      .isEqualTo(false)
  }

  fun testFileType() {
    checkThatFeature(FILETYPE_DATA_KEY)
      .ofElement(testFile)
      .isEqualTo(testFile.virtualFile.fileType.name)
  }

  fun testIsInFavorites() {
    val addFileToFavorites = { file: PsiFileSystemItem ->
      FavoritesManager.getInstance(project).also {
        it.createNewList("xxx")
        if (!it.addRoots("xxx", module, file.virtualFile)) {
          fail("Failed to add the file to the favorites list")
        }
      }
    }

    checkThatFeature(IS_FAVORITE_DATA_KEY)
      .ofElement(testFile)
      .changes(false, true)
      .after { addFileToFavorites(it) }
  }

  fun testIsOpened() {
    checkThatFeature(SearchEverywhereFileFeaturesProvider.IS_OPENED_DATA_KEY)
      .ofElement(testFile)
      .changes(false, true)
      .after { FileEditorManager.getInstance(project).openFile(it.virtualFile, false) }
  }

  fun testRecentIndexOfNeverOpenedFile() {
    prepareForRecentIndexTest()

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(testFile)
      .isEqualTo(-1)
  }

  fun testMostRecentFileIndex() {
    val openedFiles = prepareForRecentIndexTest()

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(openedFiles.last()) // Last opened file (i.e. the most recent)
      .isEqualTo(1)
  }

  fun testOldestFileIndex() {
    val openedFiles = prepareForRecentIndexTest()
    val expectedIndex = openedFiles.size

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(openedFiles.first()) // First opened file (i.e. the oldest)
      .isEqualTo(expectedIndex)
  }

  fun testPriority() {
    val priority = 10101

    checkThatFeature(PRIORITY_DATA_KEY)
      .ofElement(testFile)
      .withPriority(priority)
      .isEqualTo(priority)
  }

  fun testIsExactMatch() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE)
      .isEqualTo(true)
  }

  fun testIsNotExactMatch() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY)
      .ofElement(testFile)
      .withPriority(10101)
      .isEqualTo(false)
  }

  fun testIsFromSources() {
    val module = module {
      source {
        file("testFile.txt")
      }
    }

    val file = module.getFromSource("testFile.txt").toPsi()

    checkThatFeatures()
      .ofElement(file)
      .isEqualTo(mapOf(
        IS_IN_SOURCE_DATA_KEY to true,
        IS_IN_TEST_SOURCES_DATA_KEY to false,
        IS_EXCLUDED_DATA_KEY to false,
      ))
  }

  fun testIsFromTestSources() {
    val module = module {
      test {
        file("testFile.txt")
      }
    }

    val file = module.getFromTestSource("testFile.txt").toPsi()

    checkThatFeatures()
      .ofElement(file)
      .isEqualTo(mapOf(
        IS_IN_SOURCE_DATA_KEY to true,  // Test source is also a source content
        IS_IN_TEST_SOURCES_DATA_KEY to true,
        IS_EXCLUDED_DATA_KEY to false,
      ))
  }

  fun testIsFromExcluded() {
    val module = module {
      excluded {
        file("testFile.txt")
      }
    }

    val file = module.getFromExcluded("testFile.txt").toPsi()

    checkThatFeatures()
      .ofElement(file)
      .isEqualTo(mapOf(
        IS_IN_SOURCE_DATA_KEY to false,
        IS_IN_TEST_SOURCES_DATA_KEY to false,
        IS_EXCLUDED_DATA_KEY to true,
      ))
  }

  fun `test file type usage ratio for the most popular file type`() {
    mockedFileStatsProvider
      .setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(7, lastDay))
      .setStats("Python", FileTypeUsageSummary(2, lastDay))
      .setStats("XML", FileTypeUsageSummary(1, lastMinute))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 0.7,
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY to 1.0,
      FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY to 7.0,
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

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 0.1,
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY to roundDouble(1.0 / 7.0),
      FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY to 1.0,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastMinute() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastMinute))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to currentTime - lastMinute,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastHour() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastHour))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to currentTime - lastHour,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastDay() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastDay))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to currentTime - lastDay,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastMonth() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastMonth))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to currentTime - lastMonth,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeNeverUsed() {
    mockedFileStatsProvider.clearStats()

    // We expect these features to be null, i.e. not reported
    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to null,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to null,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to null,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to null,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to null,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to null,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastMinute() {
    val file = createFileWithModTimestamp(lastMinute)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - lastMinute,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastHour() {
    val file = createFileWithModTimestamp(lastHour)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - lastHour,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastDay() {
    val file = createFileWithModTimestamp(lastDay)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - lastDay,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastMonth() {
    val file = createFileWithModTimestamp(lastMonth)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - lastMonth,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedLaterThanMonth() {
    val modTime = lastMonth - Time.DAY
    val file = createFileWithModTimestamp(modTime)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - modTime,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to false,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileInDifferentModule() {
    val moduleA = module("testModuleA") {
      source {
        file("fileA.txt")
      }
    }

    val moduleB = module("testModuleB") {
      source {
        file("fileB.txt")
      }
    }

    val moduleAFile = moduleA.getFromSource("fileA.txt")
    val moduleBFile = moduleB.getFromSource("fileB.txt")
    FileEditorManager.getInstance(project).openFile(moduleAFile, true)

    val psiFile = moduleBFile.toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(false)
  }

  fun testFileInTheSameModule() {
    val module = module {
      source {
        file("fileA.txt")
        file("fileB.txt")
      }
    }

    val openedFile = module.getFromSource("fileA.txt")
    val foundFile = module.getFromSource("fileB.txt")
    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val psiFile = foundFile.toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(true)
  }

  fun `test if same module is reported for directories`() {
    val module = module {
      source {
        file("fileA.txt")
        directory("directory")
      }
    }

    val file = module.getFromSource("fileA.txt")
    val foundDirectory = module.getFromSource("directory")
    FileEditorManager.getInstance(project).openFile(file, true)

    val psiFile = foundDirectory.toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(true)

  }

  fun `test package distance is reported for directories`() {
    val module = module {
      source {
        createPackage("a.b.c") {
          file("fileA.txt")
          directory("directory")
        }
      }
    }

    val openedFile = module.getFromSource("fileA.txt", "a.b.c")
    val foundDirectory = module.getFromSource("directory", "a.b.c")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 1,  // The found directory is considered a subpackage, hence the distance should be 1
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(1 / (3 + 4).toDouble()),
    )

    val psiFile = foundDirectory.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 0 when same package`() {
    val module = module {
      source {
        createPackage("a.b.c.d") {
          file("fileA.txt")
          file("fileB.txt")
        }
      }
    }

    val openedFile = module.getFromSource("fileA.txt", "a.b.c.d")
    val foundFile = module.getFromSource("fileB.txt", "a.b.c.d")
    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 0,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to 0.0,
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 1 when in child package`() {
    val module = module {
      source {
        createPackage("a.b.c.d") {
          file("fileA.txt")
          createPackage("e") {
            file("fileB.txt")
          }
        }
      }
    }

    val openedFile = module.getFromSource("fileA.txt", "a.b.c.d")
    val foundFile = module.getFromSource("fileB.txt", "a.b.c.d.e")
    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 1,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(1 / (4 + 5).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 1 when in parent package`() {
    val module = module {
      source {
        createPackage("a.b.c") {
          createPackage("d") {
            file("fileA.txt")
          }
          file("fileB.txt")
        }
      }
    }

    val openedFile = module.getFromSource("fileA.txt", "a.b.c.d")
    val foundFile = module.getFromSource("fileB.txt", "a.b.c")
    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 1,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(1 / (4 + 3).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance on a parent of different group`() {
    val module = module {
      source {
        createPackage("a.b") {
          createPackage("c.d") {
            file("fileA.txt")
          }
          createPackage("x") {
            file("fileB.txt")
          }
        }
      }
    }

    val openedFile = module.getFromSource("fileA.txt", "a.b.c.d")
    val foundFile = module.getFromSource("fileB.txt", "a.b.x")
    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 3,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(3 / (4 + 3).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance on a child of different group`() {
    val module = module {
      source {
        createPackage("a.b") {
          createPackage("c.d") {
            file("fileA.txt")
          }
          createPackage("x.y") {
            file("fileB.txt")
          }
        }
      }
    }

    val openedFile = module.getFromSource("fileA.txt", "a.b.c.d")
    val foundFile = module.getFromSource("fileB.txt", "a.b.x.y")
    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 4,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(4 / (4 + 4).toDouble())
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance when root is different`() {
    val module = module {
      source {
        createPackage("a.b.c.d") {
          file("fileA.txt")
        }
        createPackage("x.y") {
          file("fileB.txt")
        }
      }
    }

    val openedFile = module.getFromSource("fileA.txt", "a.b.c.d")
    val foundFile = module.getFromSource("fileB.txt", "x.y")
    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 6,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(6 / (4 + 2).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 0 when files are not in packages`() {
    val module = module {
      source {
        file("fileA.txt")
        file("fileB.txt")
      }
    }

    val openedFile = module.getFromSource("fileA.txt")
    val foundFile = module.getFromSource("fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 0,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to 0.0,
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance when one file is not in a package`() {
    val module = module {
      source {
        createPackage("a.b.c.d") {
          file("fileA.txt")
        }
        file("fileB.txt")
      }
    }

    val openedFile = module.getFromSource("fileA.txt", "a.b.c.d")
    val foundFile = module.getFromSource("fileB.txt")
    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 4,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(4 / (4 + 0).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test prefix exact match when query contains only filename`() {
    checkThatFeature("prefixExact")
      .ofElement(testFile)
      .withQuery(testFile.virtualFile.nameWithoutExtension)
      .isEqualTo(true)
  }

  fun `test prefix exact match when query contains filename with extension`() {
    checkThatFeature("prefixExact")
      .ofElement(testFile)
      .withQuery(testFile.virtualFile.name)
      .isEqualTo(true)
  }

  fun `test prefix exact match when query contains directory and filename`() {
    val query = "${testFile.virtualFile.parent.name}${File.separatorChar}${testFile.virtualFile.nameWithoutExtension}"
    checkThatFeature("prefixExact")
      .ofElement(testFile)
      .withQuery(query)
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

  override fun tearDown() {
    EditorHistoryManager.getInstance(project).removeAllFiles()
    mockedFileStatsProvider.clearStats()
    super.tearDown()
  }
}
