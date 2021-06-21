// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.idea.maven.server.MavenServerManager

class MavenRunConfigurationSettings : Cloneable {
  var mavenHome: String = MavenServerManager.BUNDLED_MAVEN_3
  var commandLine: String = ""
  var workingDirectory: String = ""
  var environment: Map<String, String> = HashMap()
  var isPassParentEnvs: Boolean = true
  var vmOptions: String = ""
  var jreName: String? = null
  var profiles: Map<String, Boolean> = HashMap()

  public override fun clone(): MavenRunConfigurationSettings {
    val clone = super.clone() as MavenRunConfigurationSettings
    clone.setSettings(this)
    return clone
  }

  private fun setSettings(settings: MavenRunConfigurationSettings) {
    mavenHome = settings.mavenHome
    jreName = settings.jreName
    vmOptions = settings.vmOptions
    environment = settings.environment
    isPassParentEnvs = settings.isPassParentEnvs
    commandLine = settings.commandLine
    workingDirectory = settings.workingDirectory
    profiles = settings.profiles
  }

  fun readExternal(element: Element) {
    val settingsElement = element.getChild(MavenRunConfigurationSettings::class.simpleName) ?: return
    setSettings(XmlSerializer.deserialize(settingsElement, MavenRunConfigurationSettings::class.java))
  }

  fun writeExternal(element: Element) {
    element.addContent(XmlSerializer.serialize(this))
  }
}