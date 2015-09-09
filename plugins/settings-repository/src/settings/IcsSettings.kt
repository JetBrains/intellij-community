/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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

fun saveSettings(settings: IcsSettings, settingsFile: File) {
  val serialized = ObjectMapper().writer<ObjectWriter>(MyPrettyPrinter()).writeValueAsBytes(settings)
  if (serialized.size() <= 2) {
    FileUtil.delete(settingsFile)
  }
  else {
    FileUtil.writeToFile(settingsFile, serialized)
  }
}

fun loadSettings(settingsFile: File): IcsSettings {
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

  var autoSync = true
}

JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
JsonIgnoreProperties(ignoreUnknown = true)
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
        return "${FileUtil.sanitizeFileName(fileName, false)}.${Integer.toHexString(url!!.hashCode())}.git"
      }
    }
}