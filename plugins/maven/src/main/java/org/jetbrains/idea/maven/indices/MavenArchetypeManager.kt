// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager.Companion.getInstance
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog.System.DefaultLocal
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenEmbedderWrappersManager
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil.getArtifactPath
import org.jetbrains.idea.maven.utils.MavenUtil.getBaseDir
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class MavenArchetypeManager(private val myProject: Project) {
  fun getArchetypes(catalog: MavenCatalog): Collection<MavenArchetype> {
    if (catalog is MavenCatalog.System.Internal) {
      return this.innerArchetypes
    }
    if (catalog is DefaultLocal) {
      return this.localArchetypes
    }
    if (catalog is MavenCatalog.System.MavenCentral) {
      return getRemoteArchetypes(catalog.url)
    }
    if (catalog is MavenCatalog.Local) {
      return getInnerArchetypes(catalog.path)
    }
    if (catalog is MavenCatalog.Remote) {
      return getRemoteArchetypes(catalog.url)
    }
    return listOf()
  }

  fun getArchetypes(): MutableSet<MavenArchetype> {
      val indicesManager = MavenIndicesManager.getInstance(myProject)
      val result: MutableSet<MavenArchetype> = HashSet<MavenArchetype>(this.innerArchetypes)
      result.addAll(loadUserArchetypes(userArchetypesFile))
      if (!indicesManager.isInit) {
        indicesManager.updateIndicesListSync()
      }

      for (each in MavenArchetypesProvider.EP_NAME.extensionList) {
        result.addAll(each.getArchetypes())
      }
      return result
    }

  val localArchetypes: Collection<MavenArchetype>
    get() {
      return listOf()
    }

  val innerArchetypes: Collection<MavenArchetype>
    get() = listOf(
      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-archetype",
                     "1.0", null,
                     "An archetype which contains a sample archetype."),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-j2ee-simple",
                     "1.0", null,
                     "An archetype which contains a simplified sample J2EE application."),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-plugin",
                     "1.2", null,
                     "An archetype which contains a sample Maven plugin."),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-plugin-site",
                     "1.1", null,
                     "An archetype which contains a sample Maven plugin site. " +
                     "This archetype can be layered upon an existing Maven plugin project."),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-portlet",
                     "1.0.1", null,
                     "An archetype which contains a sample JSR-268 Portlet."),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-profiles",
                     "1.0-alpha-4", null, ""),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-quickstart",
                     "1.1", null,
                     "An archetype which contains a sample Maven project."),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-site",
                     "1.1", null,
                     "An archetype which contains a sample Maven site which demonstrates some of the supported document types" +
                     " like APT, XDoc, and FML and demonstrates how to i18n your site. " +
                     "This archetype can be layered upon an existing Maven project."),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-site-simple",
                     "1.1", null,
                     "An archetype which contains a sample Maven site."),

      MavenArchetype("org.apache.maven.archetypes",
                     "maven-archetype-webapp",
                     "1.0", null,
                     "An archetype which contains a sample Maven Webapp project.")
    )

  fun getInnerArchetypes(path: Path): Collection<MavenArchetype> {
    return executeWithMavenEmbedderWrapper {
      it.getInnerArchetypes(path)
    }
  }

  fun getRemoteArchetypes(url: URL): Collection<MavenArchetype> {
    return getRemoteArchetypes(url.toExternalForm())
  }

  fun getRemoteArchetypes(url: String): Collection<MavenArchetype> {
    return executeWithMavenEmbedderWrapper {
      it.getRemoteArchetypes(url)
    }
  }

  /**
   * Get archetype descriptor.
   *
   * @return null if archetype not resolved, else descriptor map.
   */
  fun resolveAndGetArchetypeDescriptor(
    groupId: String, artifactId: String,
    version: String, url: String?,
  ): Map<String, String>? {
    val map: Map<String, String>? = executeWithMavenEmbedderWrapper {
      it.resolveAndGetArchetypeDescriptor(groupId, artifactId, version, mutableListOf(), url)
    }
    if (map != null) addToLocalIndex(groupId, artifactId, version)
    return map
  }

  private fun addToLocalIndex(groupId: String, artifactId: String, version: String) {
    val mavenId = MavenId(groupId, artifactId, version)
    val localRepo = MavenIndexUtils.getLocalRepository(myProject)
    if (localRepo == null) return
    val artifactPath = getArtifactPath(Path.of(localRepo.url), mavenId, "jar", null)
    if (artifactPath != null && Files.exists(artifactPath)) {
      MavenIndicesManager.getInstance(myProject).scheduleArtifactIndexing(mavenId, artifactPath, localRepo.url)
    }
  }

  private fun <R> executeWithMavenEmbedderWrapper(function: (MavenEmbedderWrapper) -> R): R {
    val projectsManager = MavenProjectsManager.getInstance(myProject)
    var baseDir = ""
    val projects = projectsManager.rootProjects
    if (!projects.isEmpty()) {
      baseDir = getBaseDir(projects[0]!!.directoryFile).toString()
    }
    val mavenEmbedderWrappers = myProject.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    mavenEmbedderWrappers.use {
      val mavenEmbedderWrapper = runBlockingMaybeCancellable {
        mavenEmbedderWrappers.getEmbedder(baseDir)
      }
      return function(mavenEmbedderWrapper)
    }
  }

  companion object {
    private const val ELEMENT_ARCHETYPES = "archetypes"
    private const val ELEMENT_ARCHETYPE = "archetype"
    private const val ELEMENT_GROUP_ID = "groupId"
    private const val ELEMENT_ARTIFACT_ID = "artifactId"
    private const val ELEMENT_VERSION = "version"
    private const val ELEMENT_REPOSITORY = "repository"
    private const val ELEMENT_DESCRIPTION = "description"

    @JvmStatic
    fun getInstance(project: Project): MavenArchetypeManager {
      return project.getService(MavenArchetypeManager::class.java)
    }

    fun loadUserArchetypes(userArchetypesPath: Path): List<MavenArchetype> {
      try {
        if (!Files.exists(userArchetypesPath)) {
          return listOf()
        }

        // Store artifact to set to remove duplicate created by old IDEA (https://youtrack.jetbrains.com/issue/IDEA-72105)
        val result: MutableCollection<MavenArchetype> = LinkedHashSet()

        val children = JDOMUtil.load(userArchetypesPath).getChildren(ELEMENT_ARCHETYPE)
        for (i in children.indices.reversed()) {
          val each = children[i]

          val groupId = each.getAttributeValue(ELEMENT_GROUP_ID)
          val artifactId = each.getAttributeValue(ELEMENT_ARTIFACT_ID)
          val version = each.getAttributeValue(ELEMENT_VERSION)
          val repository = each.getAttributeValue(ELEMENT_REPOSITORY)
          val description = each.getAttributeValue(ELEMENT_DESCRIPTION)

          if (groupId.isNullOrBlank()
              || artifactId.isNullOrBlank()
              || version.isNullOrBlank()
          ) {
            continue
          }

          result.add(MavenArchetype(groupId, artifactId, version, repository, description))
        }

        val listResult = result.toMutableList()
        listResult.reverse()

        return listResult
      }
      catch (e: IOException) {
        MavenLog.LOG.warn(e)
        return mutableListOf()
      }
      catch (e: JDOMException) {
        MavenLog.LOG.warn(e)
        return mutableListOf()
      }
    }

    fun addArchetype(archetype: MavenArchetype, userArchetypesPath: Path) {
      val archetypes: MutableList<MavenArchetype> = ArrayList(loadUserArchetypes(userArchetypesPath))
      val idx = archetypes.indexOf(archetype)
      if (idx >= 0) {
        archetypes[idx] = archetype
      }
      else {
        archetypes.add(archetype)
      }

      saveUserArchetypes(archetypes, userArchetypesPath)
    }

    private val userArchetypesFile: Path
      get() = getInstance().getIndicesDir().resolve("UserArchetypes.xml")

    private fun saveUserArchetypes(userArchetypes: MutableList<MavenArchetype>, userArchetypesPath: Path) {
      val root = Element(ELEMENT_ARCHETYPES)
      for (each in userArchetypes) {
        val childElement = Element(ELEMENT_ARCHETYPE)
        childElement.setAttribute(ELEMENT_GROUP_ID, each.groupId)
        childElement.setAttribute(ELEMENT_ARTIFACT_ID, each.artifactId)
        childElement.setAttribute(ELEMENT_VERSION, each.version)
        if (each.repository != null) {
          childElement.setAttribute(ELEMENT_REPOSITORY, each.repository)
        }
        if (each.description != null) {
          childElement.setAttribute(ELEMENT_DESCRIPTION, each.description)
        }
        root.addContent(childElement)
      }
      try {
        JDOMUtil.write(root, userArchetypesPath)
      }
      catch (e: IOException) {
        MavenLog.LOG.warn(e)
      }
    }
  }
}
