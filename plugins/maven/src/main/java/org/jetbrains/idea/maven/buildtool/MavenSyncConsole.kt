// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.SyncViewManager
import com.intellij.build.events.EventResult
import com.intellij.build.events.Failure
import com.intellij.build.events.impl.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil


class MavenSyncConsole(private val myProject: Project) {
  private lateinit var mySyncView: BuildProgressListener
  private lateinit var myTaskId: ExternalSystemTaskId
  private var finished = false
  private var started = false

  private lateinit var myFailuresMap: LinkedHashMap<Any, ArrayList<Failure>>
  private lateinit var myStartedSet: HashSet<String>
  private var tasksRunning: Int = 0


  @Synchronized
  fun startImport() {
    if(started){
      return
    }
    finished = false
    started = true
    myStartedSet = HashSet()
    myFailuresMap = LinkedHashMap()
    tasksRunning = 0


    myTaskId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)
    val descriptor = DefaultBuildDescriptor(myTaskId, "Sync", myProject.basePath!!, System.currentTimeMillis())
    val result = Ref<BuildProgressListener>()
    ApplicationManager.getApplication().invokeAndWait { result.set(ServiceManager.getService(myProject, SyncViewManager::class.java)) }
    mySyncView = result.get()
    mySyncView.onEvent(StartBuildEventImpl(descriptor, "Sync ${myProject.name}"))
    debugLog("maven sync: started importing $myProject")
  }


  @Synchronized
  fun addText(text: String) {
    addText(text, true)
  }

  @Synchronized
  fun addText(text: String, stdout: Boolean) {
    if(!started) return
    if (StringUtil.isEmpty(text) || finished) {
      return
    }
    //println("Maven sync: print $text into $parentId")
    mySyncView.onEvent(OutputBuildEventImpl(myTaskId, "$text\n", stdout))
  }


  @Synchronized
  fun finishImport() {
    debugLog("Maven sync: finishImport")
    finished = true
    tryFinish()
  }

  @Synchronized
  fun startTask(taskName: String) {
    debugLog("Maven sync: start $taskName $tasksRunning")
    if (finished||!started) {
      return
    }
    tasksRunning += 1
    val title = getTitle(taskName)
    val parentId = extractParent(taskName)
    myStartedSet.add(taskName.trim('.'))
    mySyncView.onEvent(StartEventImpl(title, parentId, System.currentTimeMillis(), title))
  }


  @Synchronized
  fun completeTask(taskName: String) {
    debugLog("Maven sync: complete $taskName $tasksRunning")
    if(!started || !myStartedSet.contains(taskName.trim('.'))) return

    val title = getTitle(taskName)
    val parentId = extractParent(taskName)
    val result = eventResult(title)
    mySyncView.onEvent(FinishEventImpl(title, parentId, System.currentTimeMillis(), title, result))
    tasksRunning -= 1
    tryFinish()
  }


  @Synchronized
  fun completeTask(taskName: String, e: Throwable) {
    debugLog("Maven sync: complete $taskName $tasksRunning with $e")
    if(!started || !myStartedSet.contains(taskName.trim('.'))) return
    val title = getTitle(taskName)
    val parentId = extractParent(taskName)
    updateErrorStackAbove(e, taskName)
    mySyncView.onEvent(FinishEventImpl(title, parentId, System.currentTimeMillis(), title, FailureResultImpl(e)))
    tasksRunning -= 1
    tryFinish()
  }

  @Synchronized
  fun addRootError(e: Throwable) {
    if(!started) return
    addRootError(e.message?:"", e)
  }
  @Synchronized
  fun addRootError(message: String, e: Throwable) {
    addText(message, false)
    myFailuresMap.compute("Sync") { _, list -> add(list, FailureImpl(message, e)) }

  }

  private fun updateErrorStackAbove(e: Throwable, taskName: String) {
    taskName.splitToSequence("/").forEach {
      myFailuresMap.compute(it) { _, list -> add(list, FailureImpl(e.message, e)) }
    }
    myFailuresMap.compute("Sync") { _, list -> add(list, FailureImpl(e.message, e)) }
  }

  private fun add(list: ArrayList<Failure>?, failure: Failure): ArrayList<Failure> {
    if (list == null) {
      return arrayListOf(failure)
    }
    else {
      list.add(failure)
      return list
    }
  }

  private fun tryFinish() {
    if (!finished || tasksRunning > 0) {
      return
    }
    val result = eventResult("Sync")
    mySyncView
      .onEvent(FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "Sync Complete", result))
    started = false
  }


  private fun eventResult(title: String): EventResult {
    val failures = myFailuresMap[title]
    val result = if (failures != null) FailureResultImpl(failures) else SuccessResultImpl(false)
    return result
  }

  private fun extractParent(taskName: String): Any {
    val parent = taskName.split("--").map { it.trimEnd('.') }.dropLast(1).joinToString("/")
    return if (parent.isBlank()) myTaskId else parent
  }

  private fun getTitle(taskName: String): String {
    return taskName.split("--").last().trimEnd('.')
  }

  private fun debugLog(text: String) {
    MavenLog.LOG.debug(text)
  }

  @TestOnly
  fun isFinished(): Boolean {
    return finished
  }

  @TestOnly
  fun runningProcesses() : Int{
    return tasksRunning
  }

  @TestOnly
  fun getErrors(): ArrayList<Failure>? {
    return myFailuresMap["Sync"]
  }

  @TestOnly
  fun getErrors(key: String): ArrayList<Failure>? {
    return myFailuresMap[key]
  }

  @TestOnly
  fun started(key: String): Boolean{
    return myStartedSet.contains(key.trim('.'))
  }

  companion object {
    const val PLUGINS_RESOLVE_PREFIX = "Downloading Maven plugins--"
  }


}
