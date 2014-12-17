package org.jetbrains.settingsRepository

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Time
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient

import java.io.File
import java.io.IOException
import java.util.ArrayList
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.AbstractCollection

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
  val readOnlySources = ArrayList<ReadonlySource>()

  fun save() {
    FileUtil.createParentDirs(settingsFile)

    try {
      val serialized = XmlSerializer.serializeIfNotDefault(this, DEFAULT_FILTER)
      if (!JDOMUtil.isEmpty(serialized)) {
        JDOMUtil.writeParent(serialized, settingsFile, "\n")
      }
    }
    catch (e: IOException) {
      LOG.error(e)
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