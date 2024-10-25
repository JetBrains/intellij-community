// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices.archetype

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.staticOrBundled
import org.jetbrains.idea.maven.utils.MavenEelUtil
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

sealed interface MavenCatalog {

  val name: @NlsSafe String

  val location: @NlsSafe String

  sealed interface System : MavenCatalog {
    object Internal : System {
      override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.internal.name")
      override val location: String = ""
    }

    data class DefaultLocal(private val project: Project) : System {
      val file: Path
        get() {
          val generalSettings = MavenProjectsManager.getInstance(project).generalSettings
          val mavenConfig = generalSettings.mavenConfig
          val mavenHome = generalSettings.mavenHomeType.staticOrBundled()

          return runWithModalProgressBlocking(project, MavenConfigurableBundle.message("maven.progress.title.computing.repository.location")) {
            val settings = MavenEelUtil.getUserSettingsAsync(project, "", mavenConfig).toString()
            MavenEelUtil.getLocalRepoAsync(project, "", mavenHome, settings, mavenConfig)
          }
        }

      override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.default.local.name")
      override val location: String get() = file.invariantSeparatorsPathString
    }

    object MavenCentral : System {
      val url = URL("https://repo.maven.apache.org/maven2")

      override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.central.name")
      override val location: String = url.toExternalForm()
    }
  }

  data class Local(override val name: String, val path: Path) : MavenCatalog {
    override val location: String = path.invariantSeparatorsPathString
  }

  data class Remote(override val name: String, val url: URL) : MavenCatalog {
    override val location: String = url.toExternalForm()
  }
}