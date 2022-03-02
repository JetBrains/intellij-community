package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@State(name = "MarkdownDocumentLinksSafeState", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
internal class DocumentLinksSafeState: SimplePersistentStateComponent<DocumentLinksSafeState.State>(State()) {
  @ApiStatus.Internal
  class State: BaseState() {
    @get:XCollection(propertyElementName = "allowed-protocols", elementName = "protocol", valueAttributeName = "value")
    val allowedProtocols by stringSet()

    fun addProtocol(protocol: String) {
      allowedProtocols.add(protocol)
      incrementModificationCount()
    }
  }

  fun isProtocolAllowed(protocol: String): Boolean {
    return isHttpScheme(protocol) || isFileProtocol(protocol) || protocol.lowercase() in state.allowedProtocols
  }

  fun allowProtocol(protocol: String) {
    state.addProtocol(protocol.lowercase())
  }

  companion object {
    private val httpSchemes = setOf("http", "https")

    fun isHttpScheme(scheme: String): Boolean {
      return scheme.lowercase() in httpSchemes
    }

    fun isFileProtocol(scheme: String): Boolean {
      return scheme.lowercase() == "file"
    }

    @JvmStatic
    fun getInstance(project: Project): DocumentLinksSafeState {
      return project.service()
    }
  }
}
