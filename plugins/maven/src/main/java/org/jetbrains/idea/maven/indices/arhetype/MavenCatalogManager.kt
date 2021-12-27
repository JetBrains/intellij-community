package org.jetbrains.idea.maven.indices.arhetype

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.io.systemIndependentPath
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.Path

@State(name = "MavenCatalogManager", storages = [Storage(value = "maven-catalogs.xml")])
class MavenCatalogManager : PersistentStateComponent<MavenCatalogManager.State> {

  private var localCatalogs = CopyOnWriteArrayList<MavenCatalog.Local>()
  private var remoteCatalogs = CopyOnWriteArrayList<MavenCatalog.Remote>()

  fun getCatalogs(): List<MavenCatalog> {
    return localCatalogs + remoteCatalogs
  }

  fun addCatalog(catalog: MavenCatalog.Local) {
    localCatalogs.add(catalog)
  }

  fun addCatalog(catalog: MavenCatalog.Remote) {
    remoteCatalogs.add(catalog)
  }

  override fun getState(): State {
    return State(localCatalogs.map { it.asState() }, remoteCatalogs.map { it.asState() })
  }

  override fun loadState(state: State) {
    localCatalogs = state.local.mapNotNullTo(CopyOnWriteArrayList()) { it.asModel() }
    remoteCatalogs = state.remote.mapNotNullTo(CopyOnWriteArrayList()) { it.asModel() }
  }

  private fun MavenCatalog.Local.asState(): State.Local {
    return State.Local(name, path.systemIndependentPath)
  }

  private fun MavenCatalog.Remote.asState(): State.Remote {
    return State.Remote(name, url.toExternalForm())
  }

  private fun State.Local.asModel(): MavenCatalog.Local? {
    val rawPath = path ?: return null
    val url = runCatching { Path(rawPath) }.getOrNull() ?: return null
    return MavenCatalog.Local(name, url)
  }

  private fun State.Remote.asModel(): MavenCatalog.Remote? {
    val rawUrl = url ?: return null
    val url = runCatching { URL(rawUrl) }.getOrNull() ?: return null
    return MavenCatalog.Remote(name, url)
  }

  data class State(var local: List<Local> = emptyList(), var remote: List<Remote> = emptyList()) {
    data class Local(var name: String = "", var path: String? = null)
    data class Remote(var name: String = "", var url: String? = null)
  }

  companion object {
    @JvmStatic
    fun getInstance() = service<MavenCatalogManager>()
  }
}