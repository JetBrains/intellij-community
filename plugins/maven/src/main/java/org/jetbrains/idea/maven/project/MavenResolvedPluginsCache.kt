// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

class MavenResolvedPluginsCache() {
  @JvmRecord
  data class PluginResolvedResult(val artifacts: Collection<MavenArtifact>, val fromCache: Boolean)

  private val myCache: MutableMap<MavenPlugin, Future<Collection<MavenArtifact>>> = ConcurrentHashMap();


  @Throws(MavenProcessCanceledException::class)
  fun resolveCached(embedder: MavenEmbedderWrapper,
                    plugin: MavenPlugin,
                    nativeMavenProject: NativeMavenProjectHolder): PluginResolvedResult {
    if (!Registry.`is`("maven.plugins.use.cache")) {
      return PluginResolvedResult(
        embedder.resolvePlugin(plugin, nativeMavenProject),
        false

      )
    }
    else {
      val future = CompletableFuture<Collection<MavenArtifact>>();
      val previous = myCache.putIfAbsent(plugin, future);
      if (previous != null) return PluginResolvedResult(previous.get(), true)
      val mavenArtifacts = embedder.resolvePlugin(plugin, nativeMavenProject)
      future.complete(mavenArtifacts)
      return PluginResolvedResult(mavenArtifacts, false)
    }
  }
}
