package com.jetbrains.performancePlugin.remotedriver

import com.intellij.driver.model.RefDelegate
import com.intellij.driver.model.TextDataList
import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextParser
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextToKeyCache
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathSearcher
import java.awt.Component

@Suppress("unused")
@Service(Service.Level.APP)
internal class SearchService {
  private val xpathSearcher: XpathSearcher = XpathSearcher()

  fun findAll(xpath: String): List<RefDelegate<Component>> {
    return xpathSearcher.findComponents(xpath, null)
  }

  fun findAll(xpath: String, component: Component): List<RefDelegate<Component>> {
    return xpathSearcher.findComponents(xpath, component)
  }

  fun findAllText(component: Component): TextDataList {
    return TextParser.parseComponent(component, TextToKeyCache).let { TextDataList().apply { addAll(it) } }
  }
}