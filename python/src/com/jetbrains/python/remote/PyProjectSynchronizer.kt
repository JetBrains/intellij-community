/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.remote

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.File
import java.util.function.Consumer

/**
 * ProjectSynchronizer is an engine that synchronize code between local and remote system or between java (which is local)
 * and python (which may be remote).
 * This engine is sdk-specific and used by [com.jetbrains.python.newProject.PythonProjectGenerator] (and friends).
 *
 * When generator creates remote project, it may use python helpers (with aid of tasks) and it may need some way
 * to pull remote files, patch them and push 'em back. The way it does it is skd-specific and this interface encapsulates it.
 *
 * Using this engine makes your generator compatible with remote interpreters.
 *
 * Project synchronizer is also responsible for project configuration for sync: it cooperates with user to make sure remote project is
 * configured correctly.
 * @author Ilya.Kazakevich
 */
interface PyProjectSynchronizer {

  /**
   * Checks if sync is available.
   * It supports several strategies: see concrete instance documentation.
   *
   * @param syncCheckStrategy strategy to check if sync is available.
   * Several strategies are supported: see concrete instance documentation.
   * @return null if sync is available or error message if something prevents project from sync.
   */

  fun checkSynchronizationAvailable(syncCheckStrategy: PySyncCheckStrategy): String?

  /**
   * @return if remote box allows user to configure remote path, this method returns default path
   * that should be shown to user.
   * If returns null, user can't configure remote path and GUI should not provide such ability
   */
  fun getDefaultRemotePath(): String?


  /**
   * Synchronizes project.
   * @param module current module
   * @param syncDirection local-to-remote (aka java-to-python) or opposite. See enum value doc.
   * @param callback code to be called after sync completion. Argument tells if sync was success or not.
   * @param fileNames files to be used as source (local files in case of java-to-python, remote otherwise).
   *                  If no file provided, *all* files are copied. So, use this arg as filter to sync subset of files.
   */
  fun syncProject(module: Module, syncDirection: PySyncDirection,
                  callback: Consumer<Boolean>?, vararg fileNames: String)

  /**
   * Maps file name from one side to another.
   * @param filePath local file name (in case of java-to-python), remote otherwise
   */
  fun mapFilePath(project: Project, direction: PySyncDirection, filePath: String): String?
}

/**
 * Several strategies to be used for [PyProjectSynchronizer.checkSynchronizationAvailable].
 * See concrete impls.
 */
interface PySyncCheckStrategy

/**
 * Checks if specific folder could be synced with remote interpreter.
 * It does not cooperate with user but simply checks folder instead.
 *
 * Strategy should return "false" only if it is technically impossible to sync with this folder what ever user does.
 * If it is possible but requires some aid from user should return true.
 *
 * No remote project creation would be allowed if this strategy returns "false".
 */
class PySyncCheckOnly(val projectBaseDir: File) : PySyncCheckStrategy

/**
 * Checks if project with specific module could be synced with remote server.
 * It may contact user taking one through some wizard steps to configure project to support remote interpreter.
 * So, it does its best to make project synchronizable.*
 *
 * @param remotePath user provided remote path. Should only be provided if [PyProjectSynchronizer.getDefaultRemotePath] is not null.
 * This argument should only be provided first time. On next call always provide null to prevent infinite loop because
 * user will be asked for path only if this argument is null.
 */
class PySyncCheckCreateIfPossible(val module: Module, val remotePath: String?) : PySyncCheckStrategy

/**
 * Local-remote sync direction
 */
enum class PySyncDirection {
  LOCAL_TO_REMOTE,
  REMOTE_TO_LOCAL,
}