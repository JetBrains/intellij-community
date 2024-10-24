// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import org.jdom.Element
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildrenValuesByPath
import org.jetbrains.jps.maven.model.impl.MavenFilteredJarConfiguration

class MavenFilteredJarUtils {

  companion object {
    @JvmStatic
    fun getAllFilteredConfigurations(mavenProjectsManager: MavenProjectsManager, mavenProject: MavenProject): List<MavenFilteredJarConfiguration> {
      if ("pom".equals(mavenProject.packaging)) return emptyList()

      val result = HashMap<String, MavenFilteredJarConfiguration>()
      val plugin = mavenProject.findPlugin("org.apache.maven.plugins", "maven-jar-plugin");
      if (plugin == null) return emptyList()

      plugin.executions.forEach { exec ->
        exec.goals.forEach { g ->
          val configuration = exec.configurationElement
          if (configuration != null) {
            loadConfiguration(mavenProjectsManager, mavenProject, configuration, g)?.also {
              result[it.name] = it
            }
          }

        }
      }
      return result.values.toList()
    }

    private fun loadConfiguration(mavenProjectsManager: MavenProjectsManager, mavenProject: MavenProject, element: Element, goal: String): MavenFilteredJarConfiguration? {
      val includes = findChildrenValuesByPath(element, "includes", "include").toMutableList()
      val excludes = findChildrenValuesByPath(element, "excludes", "exclude").toMutableList()
      if (excludes.isEmpty() && includes.isEmpty()) return null //no configurations if jar is not filtered
      val classifier = element.getChildTextTrim("classifier") ?: GOALS[goal] ?: ""
      if (classifier.isEmpty()) return null // skip for default classifier

      val excludeDefaults: Boolean
      if ("false".equals(element.getChildTextTrim("addDefaultExcludes"), true)) {
        excludeDefaults = false
      }
      else {
        excludeDefaults = true
      }

      if (excludeDefaults) {
        DEFAULTEXCLUDES.forEach { _, v -> v.forEach(excludes::add) }
      }

      val module = mavenProjectsManager.findModule(mavenProject) ?: return null
      val configuration = MavenFilteredJarConfiguration()
      val tests = goal == "test-jar"

      configuration.classifier = classifier
      configuration.excludes = excludes.toSet()
      configuration.includes = includes.toSet()
      configuration.moduleName = module.name
      configuration.isTest = tests
      configuration.originalOutput = if (tests) mavenProject.testOutputDirectory else mavenProject.outputDirectory;
      configuration.jarOutput = configuration.originalOutput + "-jar-" + classifier
      configuration.name = mavenProject.mavenId.toString() + "-" + classifier
      return configuration
    }

    private val GOALS = mapOf("jar" to "", "test-jar" to "tests")

    //https://codehaus-plexus.github.io/plexus-utils/apidocs/org/codehaus/plexus/util/AbstractScanner.html#DEFAULTEXCLUDES
    private val DEFAULTEXCLUDES = mapOf<String, Array<String>>(
      "Misc" to arrayOf(" **/*~", "**/#*#", "**/.#*", "**/%*%", "*/._*"),
      "CVS" to arrayOf("*/CVS", "**/CVS/**", "**/.cvsignore"),
      "RCS" to arrayOf("**/RCS", "**/RCS/**"),
      "SCCS" to arrayOf("**/SCCS", "**/SCCS/**"),
      "VSSercer" to arrayOf(" **/vssver.scc"),
      "MKS" to arrayOf("**/project.pj"),
      "SVN" to arrayOf("**/.svn", "**/.svn/**"),
      "GNU" to arrayOf("**/.arch-ids", "**/.arch-ids/**"),
      "Bazaar" to arrayOf("**/.bzr", "**/.bzr/**"),
      "SurroundSCM" to arrayOf("**/.MySCMServerInfo"),
      "Mac" to arrayOf("**/.DS_Store"),
      "Serena Dimension" to arrayOf("**/.metadata", "**/.metadata/**"),
      "Mercurial" to arrayOf("**/.hg", "**/.hg/**"),
      "Git" to arrayOf("**/.git", "**/.git/**", "**/.gitignore"),
      "Bitkeeper" to arrayOf("**/BitKeeper", "**/BitKeeper/**", "**/ChangeSet", "**/ChangeSet/**"),
      "Darcs" to arrayOf("**/_darcs", "**/_darcs/**", "**/.darcsrepo", "**/.darcsrepo/****/-darcs-backup*", "**/.darcs-temp-mail"),
    )
  }


}