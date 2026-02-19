// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.storage

import java.io.File

interface FilePathProvider {
  /**
   * Provides unique file where data should be temporarily stored, until it is send to log service
   */
  fun getUniqueFile(): File

  /**
   * Returns all files with data to send
   */
  fun getDataFiles(): List<File>

  /**
   * Returns root directory where files with logs are stored
   */
  fun getStatsDataDirectory(): File


  /**
   * If user was offline for a long time we don't want to store all 1000 files,
   * instead we will store only last 2Mb of data
   */
  fun cleanupOldFiles()
}