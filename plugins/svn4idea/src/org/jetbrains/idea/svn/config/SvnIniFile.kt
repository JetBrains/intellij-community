// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil.union
import org.apache.oro.text.GlobCompiler
import org.apache.oro.text.regex.MalformedPatternException
import org.apache.oro.text.regex.Perl5Matcher
import org.ini4j.Config
import org.ini4j.Ini
import org.ini4j.spi.IniBuilder
import org.ini4j.spi.IniFormatter
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import org.jetbrains.idea.svn.config.ServersFileKeys.GLOBAL_SERVER_GROUP
import org.jetbrains.idea.svn.config.ServersFileKeys.SERVER_GROUPS_SECTION
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private val LOG = logger<SvnIniFile>()

@NonNls private val TRUE_VALUES: Set<String> = setOf("true", "yes", "on", "1")

class SvnIniFile(private val myPath: Path) {

  private val myPatternsMap = mutableMapOf<String, String?>()
  private var myLatestUpdate = -1L
  private val myDefaultProperties = mutableMapOf<String, String?>()
  private val _configFile = MyIni().apply {
    config.isTree = false
    config.isEmptySection = true
  }
  private val configFile: Ini
    get() {
      updateGroups()
      return _configFile
    }

  val allGroups: Map<String, ProxyGroup>
    get() {
      val result = HashMap<String, ProxyGroup>(myPatternsMap.size)
      for ((groupName, value) in myPatternsMap) {
        result.put(groupName, ProxyGroup(groupName, value, getValues(groupName)))
      }
      return result
    }

  val defaultGroup: DefaultProxyGroup get() = DefaultProxyGroup(myDefaultProperties)

  @JvmOverloads
  fun updateGroups(force: Boolean = false) {
    val lastModified = try {
      Files.getLastModifiedTime(myPath).toMillis()
    }
    catch (e: IOException) { 0 }

    if (force || myLatestUpdate != lastModified) {
      _configFile.clear()
      try {
        _configFile.load(myPath.toFile())
      }
      catch (e: IOException) {
        LOG.info("Could not load $myPath", e)
      }
      myLatestUpdate = lastModified

      myPatternsMap.clear()
      myPatternsMap.putAll(getValues(SERVER_GROUPS_SECTION))

      myDefaultProperties.clear()
      myDefaultProperties.putAll(getValues(GLOBAL_SERVER_GROUP))
    }
  }

  fun getValue(@NonNls groupName: String, propertyName: String): String? = configFile[groupName, propertyName]
  fun getValues(@NonNls groupName: String): Map<String, String?> = configFile[groupName] ?: emptyMap()
  fun setValue(@NonNls groupName: String, propertyName: String, value: String?) {
    configFile.put(groupName, propertyName, value)
  }

  fun deleteGroup(name: String) {
    if (GLOBAL_SERVER_GROUP == name) {
      myDefaultProperties.clear()
    }
    // remove group from groups
    setValue(SERVER_GROUPS_SECTION, name, null)
    configFile.remove(name)
  }

  fun addGroup(name: String, patterns: String?, properties: Map<String, String?>) {
    setValue(SERVER_GROUPS_SECTION, name, patterns)
    addProperties(name, properties)
  }

  private fun addProperties(groupName: String, properties: Map<String, String?>) {
    for ((key, value) in properties) {
      setValue(groupName, key, value)
    }
  }

  fun modifyGroup(name: String, patterns: String?, delete: Collection<String>, addOrModify: Map<String, String?>, isDefault: Boolean) {
    if (!isDefault) {
      setValue(SERVER_GROUPS_SECTION, name, patterns)
    }
    val deletedPrepared = HashMap<String, String?>(delete.size)
    for (property in delete) {
      deletedPrepared.put(property, null)
    }
    addProperties(name, deletedPrepared)
    addProperties(name, addOrModify)
  }

  fun save(): Unit = try {
    _configFile.store(myPath.toFile())
    updateGroups(true)
  }
  catch (e: IOException) {
    LOG.info("Could not save $myPath", e)
  }

  companion object {
    @NonNls const val SERVERS_FILE_NAME: String = "servers"
    @NonNls const val CONFIG_FILE_NAME: String = "config"

    @JvmStatic
    fun getNewGroupName(host: String, configFile: SvnIniFile): String {
      var groupName = host
      val groups = configFile.allGroups
      while (StringUtil.isEmptyOrSpaces(groupName) || groups.containsKey(groupName)) {
        groupName += "1"
      }
      return groupName
    }

    @JvmStatic
    fun getPropertyIdea(host: String, serversFile: Couple<SvnIniFile>, name: String): String? {
      val groupName = getGroupName(getValues(serversFile, SERVER_GROUPS_SECTION), host)
      if (groupName != null) {
        val hostProps = getValues(serversFile, groupName)
        val value = hostProps[name]
        if (value != null) {
          return value
        }
      }
      return getValues(serversFile, GLOBAL_SERVER_GROUP)[name]
    }

    @JvmStatic
    fun checkHostGroup(url: String, patterns: String?, exceptions: String?): Boolean {
      val svnurl: Url
      try {
        svnurl = createUrl(url)
      }
      catch (e: SvnBindException) {
        return false
      }

      val host = svnurl.host
      return matches(patterns, host) && !matches(exceptions, host)
    }

    private fun matches(patterns: String?, host: String) = patterns.orEmpty().split(',').any { matchesPattern(it.trim(), host) }
    private fun matchesPattern(pattern: String, host: String) = try {
      Perl5Matcher().matches(host, GlobCompiler().compile(pattern))
    }
    catch (e: MalformedPatternException) {
      LOG.debug("Could not compile pattern $pattern", e)
      false
    }

    @JvmStatic
    fun getGroupForHost(host: String, serversFile: SvnIniFile): String? {
      val groups = serversFile.allGroups
      for ((key, value) in groups) {
        if (matches(value.patterns, host)) return key
      }
      return null
    }

    private fun getGroupName(groups: Map<String, String?>, host: String): String? {
      for ((key, value) in groups) {
        if (matches(value, host)) return key
      }
      return null
    }

    @JvmStatic
    fun isTurned(value: String?, nullValue: Boolean): Boolean =
      if (value == null) nullValue else TRUE_VALUES.any { it.equals(value, true) }

    @JvmStatic
    fun getValue(files: Couple<SvnIniFile>, @NonNls groupName: String, propertyName: String): String? =
      files.second.getValue(groupName, propertyName) ?: files.first.getValue(groupName, propertyName)

    @JvmStatic
    fun getValues(files: Couple<SvnIniFile>, groupName: String): Map<String, String?> =
      union(files.first.getValues(groupName), files.second.getValues(groupName))
  }
}

private class MyIni : Ini() {
  override fun newBuilder() = MyIniBuilder(this)
  override fun store(output: Writer) = store(MyIniFormatter(this, config, output))
}

private class MyIniBuilder(ini: Ini) : IniBuilder() {
  init {
    setIni(ini)
  }

  private var isAfterComment = false

  override fun startSection(sectionName: String) {
    super.startSection(sectionName)
    isAfterComment = true
  }

  override fun endSection() {
    handleAfterComment()
    isAfterComment = false
    super.endSection()
  }

  override fun handleComment(comment: String) {
    handleHeaderComment()
    handleAfterComment()

    lastComment = (if (!lastComment.isNullOrEmpty()) lastComment + config.lineSeparator else "") + comment
  }

  override fun handleOption(name: String, value: String?) {
    handleAfterComment()
    super.handleOption(name, value)
  }

  private fun handleHeaderComment() {
    if (isHeader && lastComment != null) {
      profile.comment = lastComment
      lastComment = null
      isHeader = false
    }
  }

  private fun handleAfterComment() {
    if (isAfterComment && lastComment != null) {
      (profile as Ini).putMeta(AFTER_COMMENT_CATEGORY, currentSection.name, lastComment)
      lastComment = null
      isAfterComment = false
    }
  }
}

private class MyIniFormatter(private val ini: Ini, config: Config, output: Writer) : IniFormatter() {
  init {
    this.config = config
    this.output = output as? PrintWriter ?: PrintWriter(output)
  }

  override fun startSection(sectionName: String) {
    super.startSection(sectionName)
    handleComment(ini.getMeta(AFTER_COMMENT_CATEGORY, sectionName) as String?)
  }
}

@NonNls private const val AFTER_COMMENT_CATEGORY: String = "after-comment"
