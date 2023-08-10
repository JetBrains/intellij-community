// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.naturalSorted
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.util.LocalTimeCounter
import com.intellij.util.text.nullize
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.indices.MavenArchetypeManager
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog
import org.jetbrains.idea.maven.indices.archetype.MavenCatalogManager
import javax.swing.JComponent

internal class MavenArchetypeNewProjectWizardBackend(private val project: Project, private val parentDisposable: Disposable) {

  private val cache = LinkedHashMap<MavenCatalog, Promise<Map<ArchetypeItem, List<String>>>>()

  fun getCatalogs(): List<MavenCatalog> {
    cache.clear()
    return MavenCatalogManager.getInstance()
      .getCatalogs(project)
  }

  fun collectArchetypeIds(component: JComponent, catalog: MavenCatalog, callback: (List<ArchetypeItem>) -> Unit) {
    collectArchetypes(component, catalog) {
      callback(it.keys.toList())
    }
  }

  private fun collectArchetypes(component: JComponent, catalog: MavenCatalog, callback: (Map<ArchetypeItem, List<String>>) -> Unit) {
    val promise = cache.getOrPut(catalog) {
      executeBackgroundTask(component) {
        resolveArchetypes(catalog)
      }
    }
    promise.callIfNotObsolete(component, callback)
  }

  private fun resolveArchetypes(catalog: MavenCatalog): Map<ArchetypeItem, List<String>> {
    return MavenArchetypeManager.getInstance(project)
      .getArchetypes(catalog)
      .map { ArchetypeItem(it.groupId, it.artifactId) to it.version }
      .naturalSorted()
      .groupBy { it.first }
      .mapValues { (_, archetypes) ->
        archetypes.map { it.second }
          .naturalSorted()
          .reversed()
      }
  }

  fun collectArchetypeVersions(component: JComponent, catalog: MavenCatalog, archetype: ArchetypeItem, callback: (List<String>) -> Unit) {
    collectArchetypes(component, catalog) {
      callback(it[archetype] ?: emptyList())
    }
  }

  fun collectArchetypeDescriptor(
    component: JComponent,
    catalog: MavenCatalog,
    archetype: ArchetypeItem,
    version: String,
    callback: (Map<String, String>) -> Unit
  ) {
    val promise = executeBackgroundTask(component) {
      resolveArchetypeDescriptor(catalog, archetype, version)
    }
    promise.callIfNotObsolete(component, callback)
  }

  private fun resolveArchetypeDescriptor(
    catalog: MavenCatalog,
    archetype: ArchetypeItem,
    version: String
  ): Map<String, String> {
    return resolveArchetypeDescriptor(
      catalog.location,
      archetype.groupId.nullize() ?: return emptyMap(),
      archetype.artifactId.nullize() ?: return emptyMap(),
      version.nullize() ?: return emptyMap()
    )
  }

  private fun resolveArchetypeDescriptor(
    catalog: String,
    groupId: String,
    artifactId: String,
    version: String
  ): Map<String, String> {
    val manager = MavenArchetypeManager.getInstance(project)
    val descriptor = manager.resolveAndGetArchetypeDescriptor(groupId, artifactId, version, catalog) ?: return emptyMap()
    return descriptor.toMutableMap()
      .apply { remove("groupId") }
      .apply { remove("artifactId") }
      .apply { remove("version") }
      .apply { remove("archetypeGroupId") }
      .apply { remove("archetypeArtifactId") }
      .apply { remove("archetypeVersion") }
      .apply { remove("archetypeRepository") }
  }

  private fun <R> executeBackgroundTask(component: JComponent, action: () -> R): Promise<R> {
    val promise = AsyncPromise<R>()
    BackgroundTaskUtil.submitTask(parentDisposable) {
      val result = action()
      invokeLater(ModalityState.stateForComponent(component)) {
        promise.setResult(result)
      }
    }
    return promise
  }

  private fun <R> Promise<R>.callIfNotObsolete(component: JComponent, callback: (R) -> Unit) {
    val stamp = LocalTimeCounter.currentTime()
    component.putUserData(STAMP, stamp)
    onSuccess {
      if (stamp == component.getUserData(STAMP)) {
        callback(it)
      }
    }
  }

  data class ArchetypeItem(val groupId: String, val artifactId: String) {
    companion object {
      val NONE = ArchetypeItem("", "")
    }
  }

  companion object {
    private val STAMP = Key.create<Long>("MavenArchetypeNewProjectWizardBackend.Stamp")
  }
}