package org.jetbrains.settingsRepository

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.Time
import java.io.File

private val settingsFile = File(getPluginSystemDir(), "config.json")
private val DEFAULT_COMMIT_DELAY = 10 * Time.MINUTE

class MyPrettyPrinter : DefaultPrettyPrinter() {
  init {
    _arrayIndenter = DefaultPrettyPrinter.NopIndenter.instance
  }

  override fun createInstance() = MyPrettyPrinter()

  override fun writeObjectFieldValueSeparator(jg: JsonGenerator) {
    jg.writeRaw(": ")
  }

  override fun writeEndObject(jg: JsonGenerator, nrOfEntries: Int) {
    if (!_objectIndenter.isInline()) {
      --_nesting
    }
    if (nrOfEntries > 0) {
      _objectIndenter.writeIndentation(jg, _nesting)
    }
    jg.writeRaw('}')
  }

  override fun writeEndArray(jg: JsonGenerator, nrOfValues: Int) {
    if (!_arrayIndenter.isInline()) {
      --_nesting
    }
    jg.writeRaw(']')
  }
}

fun saveSettings(settings: IcsSettings) {
  val serialized = ObjectMapper().writer<ObjectWriter>(MyPrettyPrinter()).writeValueAsBytes(settings)
  if (serialized.size() <= 2) {
    FileUtil.delete(settingsFile)
  }
  else {
    FileUtil.writeToFile(settingsFile, serialized)
  }
}

fun loadSettings(): IcsSettings {
  if (!settingsFile.exists()) {
    return IcsSettings()
  }

  val settings = ObjectMapper().readValue(settingsFile, javaClass<IcsSettings>())
  if (settings.commitDelay <= 0) {
    settings.commitDelay = DEFAULT_COMMIT_DELAY
  }
  return settings
}

JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
class IcsSettings {
  var shareProjectWorkspace = false
  var commitDelay = DEFAULT_COMMIT_DELAY
  var doNoAskMapProject = false
  var readOnlySources: List<ReadonlySource> = SmartList()
}

JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
class ReadonlySource(var url: String? = null, var active: Boolean = true) {
  JsonIgnore
  val path: String?
    get() {
      if (url == null) {
        return null
      }
      else {
        var fileName = PathUtilRt.getFileName(url!!)
        val suffix = ".git"
        if (fileName.endsWith(suffix)) {
          fileName = fileName.substring(0, fileName.length() - suffix.length())
        }
        // the convention is that the .git extension should be used for bare repositories
        return "${FileUtil.sanitizeName(fileName)}.${Integer.toHexString(url!!.hashCode())}.git"
      }
    }
}