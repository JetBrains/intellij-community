package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.util.application
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service(Service.Level.APP)
@State(name = "MarkdownSettings", storages = [(Storage("markdown.xml"))])
internal class MarkdownPreviewSettings: SimplePersistentStateComponent<MarkdownPreviewSettings.State>(State()) {
  class State: BaseState() {
    var fontSize by property(defaultFontSize)
  }

  fun update(block: (MarkdownPreviewSettings) -> Unit) {
    block(this)
    application.messageBus.syncPublisher(ChangeListener.TOPIC).settingsChanged(this)
  }

  fun interface ChangeListener {
    fun settingsChanged(settings: MarkdownPreviewSettings)

    companion object {
      @JvmField
      @Topic.AppLevel
      val TOPIC = Topic("MarkdownPreviewSettingsChanged", ChangeListener::class.java)
    }
  }

  companion object {
    internal val defaultFontSize: Int
      get() = (checkNotNull(AppEditorFontOptions.getInstance().state).FONT_SIZE + 0.5).toInt()
  }
}
