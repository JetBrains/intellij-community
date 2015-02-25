package org.jetbrains.settingsRepository

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SmartList
import com.intellij.util.Time
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.AbstractCollection
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import java.io.File

class IcsSettings {
  class object {
    private val DEFAULT_COMMIT_DELAY = 10 * Time.MINUTE
    private val DEFAULT_FILTER = SkipDefaultValuesSerializationFilters()
  }

  Tag
  var shareProjectWorkspace = false

  Tag
  var commitDelay = DEFAULT_COMMIT_DELAY

  var doNoAskMapProject = false

  Transient
  private val settingsFile = File(getPluginSystemDir(), "config.xml")

  Tag
  Property(surroundWithTag = false)
  AbstractCollection(surroundWithTag = false)
  var readOnlySources: List<ReadonlySource> = SmartList()

  fun save() {
    val serialized = XmlSerializer.serializeIfNotDefault(this, DEFAULT_FILTER)
    if (serialized == null || JDOMUtil.isEmpty(serialized)) {
      FileUtil.delete(settingsFile)
    }
    else {
      FileUtil.createParentDirs(settingsFile)
      JDOMUtil.writeParent(serialized, settingsFile, "\n")
    }
  }

  fun load() {
    if (!settingsFile.exists()) {
      return
    }

    XmlSerializer.deserializeInto(this, JDOMUtil.load(settingsFile))

    if (commitDelay <= 0) {
      commitDelay = DEFAULT_COMMIT_DELAY
    }
  }
}

Tag("source")
class ReadonlySource(var active: Boolean = false, var url: String? = null)