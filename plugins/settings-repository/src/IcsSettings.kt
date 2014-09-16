package org.jetbrains.plugins.settingsRepository

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Time
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import org.jdom.Document
import org.jdom.Element
import org.jdom.JDOMException

import java.io.File
import java.io.IOException

class IcsSettings {
  class object {
    private val DEFAULT_COMMIT_DELAY = 10 * Time.MINUTE
    private val DEFAULT_FILTER = SkipDefaultValuesSerializationFilters()
  }

  Tag
  public var shareProjectWorkspace: Boolean = false

  Tag
  public var commitDelay: Int = DEFAULT_COMMIT_DELAY

  public var doNoAskMapProject: Boolean = false

  Transient
  private val settingsFile: File

  {
    settingsFile = File(getPluginSystemDir(), "config.xml")
  }

  public fun save() {
    FileUtil.createParentDirs(settingsFile)

    try {
      val serialized = XmlSerializer.serialize(this, DEFAULT_FILTER)
      if (!serialized.getContent().isEmpty()) {
        JDOMUtil.writeDocument(Document(serialized), settingsFile, "\n")
      }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  public fun load() {
    if (!settingsFile.exists()) {
      return
    }

    XmlSerializer.deserializeInto(this, JDOMUtil.loadDocument(settingsFile).getRootElement())

    if (commitDelay < 0) {
      commitDelay = DEFAULT_COMMIT_DELAY
    }
  }
}