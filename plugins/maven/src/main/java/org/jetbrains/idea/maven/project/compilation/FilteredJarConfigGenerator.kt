// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.compilation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.text.nullize
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildrenValuesByPath
import org.jetbrains.jps.maven.model.impl.MavenFilteredJarConfiguration
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration

@ApiStatus.Internal
class FilteredJarConfigGenerator(
  private val fileIndex: ProjectFileIndex,
  private val mavenProjectsManager: MavenProjectsManager,
  private val transformer: RemotePathTransformerFactory.Transformer,
  private val config: MavenProjectConfiguration,
  private val mavenProject: MavenProject,
) {
  fun generateAdditionalJars() {
    if (!Registry.`is`("maven.build.additional.jars")) {
      return
    }
    if ("pom".equals(mavenProject.packaging)) return;

    GOALS.forEach { g, _ ->
      mavenProject.getPluginGoalConfiguration("org.apache.maven.plugins", "maven-jar-plugin", g)?.let {
        addConfiguration(g, it)
      }
    }
  }


  private fun addConfiguration(goal: String, element: Element) {
    val includes = findChildrenValuesByPath(element, "includes", "include").toMutableList()
    val excludes = findChildrenValuesByPath(element, "excludes", "exclude").toMutableList()
    val classifier = element.getChildTextTrim("classifier") ?: GOALS[goal] ?: ""
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
    doAddConfiguration(goal == "test-jar", classifier, excludes, includes)

  }

  private fun doAddConfiguration(tests: Boolean, classifier: String, excludes: MutableList<String>, includes: MutableList<String>) {
    val module = mavenProjectsManager.findModule(mavenProject) ?: return
    val configuration = MavenFilteredJarConfiguration()

    configuration.classifier = classifier
    configuration.excludes = excludes.toSet()
    configuration.includes = includes.toSet()
    configuration.moduleName = module.name
    val name = mavenProject.mavenId.toString() + (classifier.nullize(true)?.map { "-$it" } ?: "")
    configuration.isTest = tests
    configuration.originalOutput = if (tests) mavenProject.testOutputDirectory else mavenProject.outputDirectory;
    configuration.jarOutput = configuration.originalOutput + "-jar-" + classifier
    config.jarsConfiguration[name] = configuration
  }


  companion object {
    private val LOG = Logger.getInstance(FilteredJarConfigGenerator::class.java)
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