/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.stats.completion

import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.stats.events.completion.LogEvent
import com.intellij.stats.events.completion.LogEventSerializer
import java.util.*

interface InstallationIdProvider {
  fun installationId(): String
}

class PermanentInstallationIdProvider: InstallationIdProvider {
  override fun installationId() = PermanentInstallationID.get()
}

class CompletionFileLoggerProvider(
        filePathProvider: FilePathProvider,
        private val installationIdProvider: InstallationIdProvider
) : ApplicationComponent, CompletionLoggerProvider() {

  private val logFileManager = LogFileManager(filePathProvider)

  override fun disposeComponent() {
    logFileManager.dispose()
  }

  private fun String.shortedUUID(): String {
    val start = this.lastIndexOf('-')
    if (start > 0 && start + 1 < this.length) {
      return this.substring(start + 1)
    }
    return this
  }

  override fun newCompletionLogger(): CompletionLogger {
    val installationUID = installationIdProvider.installationId()
    val completionUID = UUID.randomUUID().toString()
    val eventLogger = FileEventLogger(logFileManager)
    return CompletionFileLogger(installationUID.shortedUUID(), completionUID.shortedUUID(), eventLogger)
  }
}

class FileEventLogger(private val logFileManager: LogFileManager): CompletionEventLogger {
  override fun log(event: LogEvent) {
    val line = LogEventSerializer.toString(event)
    logFileManager.println(line)
  }
}