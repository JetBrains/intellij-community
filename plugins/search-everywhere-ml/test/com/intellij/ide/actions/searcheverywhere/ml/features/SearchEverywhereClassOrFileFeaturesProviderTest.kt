package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_DAY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_HOUR_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_MONTH_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.IS_SAME_MODULE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.PACKAGE_DISTANCE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.PACKAGE_DISTANCE_NORMALIZED_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY
import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile


internal class SearchEverywhereClassOrFileFeaturesProviderTest
  : SearchEverywhereBaseFileFeaturesProviderTest<SearchEverywhereClassOrFileFeaturesProvider>(SearchEverywhereClassOrFileFeaturesProvider::class.java) {

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
    checkThatFeature(IS_SAME_MODULE_DATA_KEY.name)
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
    checkThatFeature(IS_SAME_MODULE_DATA_KEY.name)
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
    checkThatFeature(IS_SAME_MODULE_DATA_KEY.name)
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
}
