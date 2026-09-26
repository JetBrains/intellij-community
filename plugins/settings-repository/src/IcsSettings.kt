// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.Time
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import tools.jackson.core.JsonGenerator
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_COMMIT_DELAY = 10 * Time.MINUTE

class MyPrettyPrinter : DefaultPrettyPrinter() {
  init {
    _arrayIndenter = NopIndenter.instance()
  }

  override fun createInstance() = MyPrettyPrinter()

  override fun writeObjectNameValueSeparator(jg: JsonGenerator) {
    jg.writeRaw(": ")
  }

  override fun writeEndObject(jg: JsonGenerator, nrOfEntries: Int) {
    if (!_objectIndenter.isInline) {
      --_nesting
    }
    if (nrOfEntries > 0) {
      _objectIndenter.writeIndentation(jg, _nesting)
    }
    jg.writeRaw('}')
  }

  override fun writeEndArray(jg: JsonGenerator, nrOfValues: Int) {
    if (!_arrayIndenter.isInline) {
      --_nesting
    }
    jg.writeRaw(']')
  }
}

fun saveSettings(settings: IcsSettings, settingsFile: Path) {
  val serialized = JsonMapper.builder().build().writer().with(MyPrettyPrinter()).writeValueAsBytes(settings)
  if (serialized.size <= 2) {
    Files.delete(settingsFile)
  }
  else {
    settingsFile.write(serialized)
  }
}

fun loadSettings(settingsFile: Path): IcsSettings {
  if (!Files.exists(settingsFile)) {
    return IcsSettings()
  }

  val settings = JsonMapper.builder().build().readValue(settingsFile, IcsSettings::class.java)
  if (settings.commitDelay <= 0) {
    settings.commitDelay = DEFAULT_COMMIT_DELAY
  }
  return settings
}

@JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
class IcsSettings {
  var commitDelay = DEFAULT_COMMIT_DELAY
  var readOnlySources: List<ReadonlySource> = SmartList()

  var autoSync = true
  var includeHostIntoCommitMessage = true
}

@JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
class ReadonlySource(var url: String? = null, var active: Boolean = true) {
  val path: String?
    @JsonIgnore
    get() {
      var fileName = PathUtilRt.getFileName(url ?: return null)
      val suffix = ".git"
      if (fileName.endsWith(suffix)) {
        fileName = fileName.substring(0, fileName.length - suffix.length)
      }
      // the convention is that the .git extension should be used for bare repositories
      return "${sanitizeFileName(fileName)}.${Integer.toHexString(url!!.hashCode())}.git"
    }
}