// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.util.containers.ContainerUtil
import java.util.*

class MavenParsingContext(private val myTaskId: ExternalSystemTaskId) {

  private val context = ContainerUtil.createConcurrentIntObjectMap<ArrayList<MavenExecutionEntry>>()
  private var lastAddedThreadId: Int = 0

  val lastId: Any
    get() {
      val entries = context.get(lastAddedThreadId)
      return if (entries == null || entries.isEmpty()) {
        myTaskId
      }
      else entries[entries.size - 1].id
    }



  fun getProject(threadId: Int,  id: String?, create: Boolean): ProjectExecutionEntry? {
    var currentProject = search(ProjectExecutionEntry::class.java, context.get(threadId)
    ) { e -> id == null || e.name == id }

    if (currentProject == null && create) {
      currentProject = ProjectExecutionEntry(id ?: "", threadId)
      add(threadId, currentProject)
    }
    return currentProject
  }

  fun getProject(threadId: Int, parameters: Map<String, String>, create: Boolean): ProjectExecutionEntry? {
    return getProject(threadId, parameters["id"], create)
  }


  fun getMojo(threadId: Int, parameters: Map<String, String>, create: Boolean): MojoExecutionEntry? {
    return getMojo(threadId, parameters, parameters["goal"], create)
  }

  fun getMojo(threadId: Int, parameters: Map<String, String>, name: String?, create: Boolean): MojoExecutionEntry? {
    if (name == null) {
      return null
    }
    var mojo = search(MojoExecutionEntry::class.java, context.get(threadId)) { e -> e.name == name }
    if (mojo == null && create) {
      val currentProject = getProject(threadId, parameters, false)
      mojo = MojoExecutionEntry(name, threadId, currentProject)
      add(threadId, mojo)
    }
    return mojo
  }

  fun getNode(threadId: Int, name: String?, create: Boolean): NodeExecutionEntry? {
    if (name == null) {
      return null
    }
    var node = search(NodeExecutionEntry::class.java, context.get(threadId)) { e -> e.name == name }

    if (node == null && create) {
      val parent = getNodeParent(threadId)
      node = NodeExecutionEntry(name, threadId, parent)
      add(threadId, node)
    }
    return node
  }

  private fun getNodeParent(threadId: Int): MavenExecutionEntry? {
    val mojo = search(MojoExecutionEntry::class.java, context.get(threadId))
    if (mojo == null) {
      return search(ProjectExecutionEntry::class.java, context.get(threadId)) { true }
    }
    return mojo
  }

  private fun add(id: Int, entry: MavenExecutionEntry) {
    var entries: ArrayList<MavenExecutionEntry>? = context.get(id)
    if (entries == null) {
      entries = ArrayList()
      context.put(id, entries)
    }
    lastAddedThreadId = id
    entries.add(entry)
  }


  inner class ProjectExecutionEntry internal constructor(name: String, threadId: Int) : MavenExecutionEntry(name, threadId) {

    override val parentId: Any
      get() = this@MavenParsingContext.myTaskId
  }

  inner class MojoExecutionEntry internal constructor(name: String,
                                                      threadId: Int,
                                                      private val myProject: ProjectExecutionEntry?) : MavenExecutionEntry(name, threadId) {

    override val parentId: Any
      get() = myProject?.id ?: this@MavenParsingContext.myTaskId
  }

  inner class NodeExecutionEntry internal constructor(name: String,
                                                      threadId: Int,
                                                      private val parent: MavenExecutionEntry?) : MavenExecutionEntry(name, threadId) {
    override val parentId: Any
      get() = parent?.id ?: this@MavenParsingContext.myTaskId
  }


  private fun <T : MavenExecutionEntry> search(klass: Class<T>,
                                               entries: ArrayList<MavenExecutionEntry>?): T? {
    return search(klass, entries) { true }
  }

  private fun <T : MavenExecutionEntry> search(klass: Class<T>,
                                               entries: List<MavenExecutionEntry>?,
                                               filter: (T) -> Boolean): T? {
    if (entries == null) {
      return null
    }
    for (j in entries.indices.reversed()) {
      val entry = entries[j]
      if (klass.isAssignableFrom(entry.javaClass)) {
        @Suppress("UNCHECKED_CAST")
        if (filter.invoke(entry as T)) {
          return entry
        }
      }
    }
    return null
  }

  abstract inner class MavenExecutionEntry(val name: String, private val myThreadId: Int) {
    val id = Any()

    abstract val parentId: Any

    fun complete() {
      val entries = this@MavenParsingContext.context.get(myThreadId)
      entries?.remove(this)
    }
  }
}
