/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.settingsRepository.git

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.URIish
import org.jetbrains.keychain.Credentials
import org.jetbrains.settingsRepository.LOG

private var canUseGitExe = true

// https://www.kernel.org/pub/software/scm/git/docs/git-credential.html
fun getCredentialsUsingGit(uri: URIish, repository: Repository): Credentials? {
  if (!canUseGitExe || repository.config.getSubsections("credential").isEmpty()) {
    return null
  }

  val commandLine = GeneralCommandLine("git", "credential", "fill")
  val process: Process
  try {
    process = commandLine.createProcess()
  }
  catch (e: ProcessNotCreatedException) {
    canUseGitExe = false
    return null
  }

  val writer = process.outputStream.writer()
  writer.write("url=")
  writer.write(uri.toPrivateString())
  writer.write("\n\n")
  writer.close();

  val reader = process.inputStream.reader().buffered()
  var username: String? = null
  var password: String? = null
  while (true) {
    val line = reader.readLine()?.trim()
    if (line == null || line.isEmpty()) {
      break
    }

    fun readValue() = line.substring(line.indexOf('=') + 1).trim()

    if (line.startsWith("username=")) {
      username = readValue()
    }
    else if (line.startsWith("password=")) {
      password = readValue()
    }
  }
  reader.close()

  val errorText = process.errorStream.reader().readText()
  if (!StringUtil.isEmpty(errorText)) {
    LOG.warn(errorText)
  }
  return if (username == null && password == null) null else Credentials(username, password)
}
