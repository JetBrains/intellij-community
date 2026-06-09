// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.jcef

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.Language
import com.intellij.mermaid.MermaidPlugin
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.application
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider

internal class AdditionalMermaidInfoStringValuesContributor: CodeFenceLanguageProvider {
  override fun getLanguageByInfoString(infoString: String): Language? {
    return when {
      isCustomMermaidInfoString(infoString) -> MermaidLanguage
      else -> null
    }
  }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): MutableList<LookupElement> {
    return mutableListOf()
  }
}

private const val PatternSettingId = "mermaid.additional.code.fence.pattern.regex"

internal fun obtainAdditionalVariantsRegex(): Regex? {
  return service<AdditionalMermaidInfoStringPatternManager>().value
}

internal fun isCustomMermaidInfoString(string: String): Boolean {
  val pattern = obtainAdditionalVariantsRegex() ?: return false
  return pattern.matches(string)
}

@Service(Service.Level.APP)
internal class AdditionalMermaidInfoStringPatternManager: Disposable {
  var value = obtainRegex(AdvancedSettings.getString(PatternSettingId))
    private set

  init {
    val connection = application.messageBus.connect(this)
    connection.subscribe(AdvancedSettingsChangeListener.TOPIC, object: AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id != PatternSettingId) {
          return
        }
        check(newValue is String)
        val regex = obtainRegex(newValue)
        if (regex != value) {
          value = obtainRegex(newValue)
        }
        for (project in obtainOpenedProjects()) {
          val service = project.serviceOrNull<MermaidPlugin>() ?: continue
          service.coroutineScope().launch {
            writeAction {
              DaemonCodeAnalyzer.getInstance(project).restart()
            }
          }
        }
      }
    })
  }

  private fun obtainOpenedProjects(): Sequence<Project> {
    return sequence {
      val projects = ProjectManager.getInstanceIfCreated()?.openProjects ?: return@sequence
      for (project in projects) {
        if (project.isDisposed || !project.isInitialized) {
          continue
        }
        yield(project)
      }
    }
  }

  override fun dispose() = Unit

  private fun obtainRegex(pattern: String): Regex? {
    if (pattern.isEmpty()) {
      return null
    }
    val regex = runCatching { Regex(pattern) }
    return regex.getOrNull()
  }
}
