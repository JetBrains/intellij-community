// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices.arhetype

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenWslUtil
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import java.io.File
import java.net.URL
import java.nio.file.Path

sealed interface MavenCatalog {

  val name: @NlsSafe String

  val location: @NlsSafe String

  sealed interface System : MavenCatalog {
    object Internal : System {
      override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.internal.name")
      override val location: String = ""
    }

    data class DefaultLocal(private val project: Project) : System {
      val file: File
        get() {
          val generalSettings = MavenProjectsManager.getInstance(project).generalSettings
          val mavenConfig = generalSettings.mavenConfig
          val mavenHome = generalSettings.mavenHome
          val userSettings = MavenWslUtil.getUserSettings(project, "", mavenConfig).path
          return MavenWslUtil.getLocalRepo(project, "", mavenHome, userSettings, mavenConfig)
        }

      override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.default.local.name")
      override val location: String get() = asLocal().location

      fun asLocal() = Local(name, file.toPath())
    }

    object MavenCentral : System {
      val url = URL("https://repo.maven.apache.org/maven2")

      override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.central.name")
      override val location: String = asRemote().location

      fun asRemote() = Remote(name, url)
    }
  }

  data class Local(override val name: String, val path: Path) : MavenCatalog {
    override val location: String = path.systemIndependentPath
  }

  data class Remote(override val name: String, val url: URL) : MavenCatalog {
    override val location: String = url.toExternalForm()
  }
}