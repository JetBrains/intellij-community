package com.intellij.ui.webview.demo

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.webview.demo.acp.AcpChatPanel
import com.intellij.ui.webview.markdown.linkgraph.MarkdownLinkGraphPanel
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
internal class WebViewDemoProjectService(
  private val project: Project,
  val coroutineScope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): WebViewDemoProjectService = project.service()
  }

  fun createSamplePanelContent(): WebViewDemoContent = createContent("WebViewDemoSamplePanel(${project.name})") { scope ->
    val panel = WebViewDemoPanel(scope)
    WebViewDemoContentPanel(panel.component, panel::dispose)
  }

  fun createControlsShowcaseContent(): WebViewDemoContent = createContent("WebViewDemoControlsShowcase(${project.name})") { scope ->
    val panel = WebViewControlsShowcasePanel(project, scope)
    WebViewDemoContentPanel(panel.component, panel::dispose)
  }

  fun createReactControlsShowcaseContent(): WebViewDemoContent = createContent("WebViewDemoReactControlsShowcase(${project.name})") { scope ->
    val panel = WebViewReactControlsShowcasePanel(scope)
    WebViewDemoContentPanel(panel.component, panel::dispose)
  }

  fun createUiDslShowcaseContent(): WebViewDemoContent = createContent("WebViewDemoUiDslShowcase(${project.name})") { scope ->
    val panel = WebViewUiDslShowcasePanel(project, scope)
    WebViewDemoContentPanel(panel.component, panel::dispose)
  }

  fun createMarkdownLinkGraphContent(): WebViewDemoContent = createContent("WebViewDemoMarkdownLinkGraph(${project.name})") { scope ->
    val panel = MarkdownLinkGraphPanel(project, scope)
    WebViewDemoContentPanel(panel.component, panel::dispose)
  }

  fun createAcpChatContent(): WebViewDemoContent = createContent("WebViewDemoAcpChat(${project.name})") { scope ->
    val panel = AcpChatPanel(project, scope)
    WebViewDemoContentPanel(panel.component, panel::dispose)
  }

  private fun createContent(
    scopeName: String,
    createPanel: (CoroutineScope) -> WebViewDemoContentPanel,
  ): WebViewDemoContent {
    val contentScope = coroutineScope.childScope(scopeName)
    val panel = createPanel(contentScope)
    val disposer = Disposer.newDisposable("WebViewDemoContent").also {
      Disposer.register(it, contentScope.asDisposable())
      Disposer.register(it, Disposable { contentScope.cancel() })
      // Registered last, disposed first (LIFO): release WebView while contentScope is still alive.
      Disposer.register(it, Disposable { panel.disposePanel() })
    }
    return WebViewDemoContent(
      component = panel.component,
      disposer = disposer,
    )
  }
}

internal data class WebViewDemoContent(
  val component: JComponent,
  val disposer: Disposable,
)

private data class WebViewDemoContentPanel(
  val component: JComponent,
  val disposePanel: () -> Unit,
)
