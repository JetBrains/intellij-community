// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xml.*
import org.jetbrains.idea.maven.dom.model.*
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenCoordinate
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenResource
import org.jetbrains.idea.maven.plugins.groovy.MavenGroovyPomCompletionContributor
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil.isPomFileName
import java.util.regex.Pattern

object MavenDomUtil {
  private val FILTERED_RESOURCES_ROOTS_KEY = Key.create<Pair<Long?, MutableSet<VirtualFile?>?>?>("MavenDomUtil.FILTERED_RESOURCES_ROOTS")

  // see http://maven.apache.org/settings.html
  private val SUBTAGS_IN_SETTINGS_FILE: MutableSet<String?> = ContainerUtil.newHashSet<String?>("localRepository", "interactiveMode",
                                                                                                "usePluginRegistry", "offline",
                                                                                                "pluginGroups",
                                                                                                "servers", "mirrors", "proxies", "profiles",
                                                                                                "activeProfiles")
  private val XML_TAG_NAME_PATTERN: Pattern = Pattern.compile("(\\S*)\\[(\\d*)\\]\\z")

  @JvmStatic
  fun isMavenFile(file: PsiFile): Boolean {
    return isProjectFile(file) || isProfilesFile(file) || isSettingsFile(file)
  }

  @JvmStatic
  fun isProjectFile(file: PsiFile?): Boolean {
    if (file !is XmlFile) return false

    val rootTag = file.getRootTag()
    if (rootTag == null || "project" != rootTag.getName()) return false

    val xmlns = rootTag.getAttributeValue("xmlns")
    if (xmlns != null && xmlns.startsWith("http://maven.apache.org/POM/")) {
      return true
    }

    return isPomFileName(file.getName())
  }

  @JvmStatic
  fun getXmlProjectModelVersion(file: PsiFile?): @NlsSafe String? {
    if (file !is XmlFile) return null

    val rootTag = file.getRootTag()
    if (rootTag == null || "project" != rootTag.getName()) return null

    return rootTag.getSubTagText("modelVersion")
  }

  @JvmStatic
  fun isProfilesFile(file: PsiFile?): Boolean {
    if (file !is XmlFile) return false

    return MavenConstants.PROFILES_XML == file.getName()
  }

  @JvmStatic
  fun getXmlSettingsNameSpace(file: PsiFile?): @NlsSafe String? {
    if (file !is XmlFile) return null

    val rootTag = file.getRootTag()
    if (rootTag == null || "settings" != rootTag.getName()) return null

    return rootTag.getAttributeValue("xmlns")
  }

  @JvmStatic
  fun isSettingsFile(file: PsiFile?): Boolean {
    if (file !is XmlFile) return false

    val rootTag = file.getRootTag()
    if (rootTag == null || "settings" != rootTag.getName()) return false

    val xmlns = rootTag.getAttributeValue("xmlns")
    if (xmlns != null) {
      return xmlns.contains("maven")
    }

    var hasTag = false

    var e = rootTag.getFirstChild()
    while (e != null) {
      if (e is XmlTag) {
        if (SUBTAGS_IN_SETTINGS_FILE.contains(e.getName())) return true
        hasTag = true
      }
      e = e.getNextSibling()
    }

    return !hasTag
  }

  @JvmStatic
  fun isMavenFile(element: PsiElement): Boolean {
    val file = element.getContainingFile() ?: return false
    return isMavenFile(file)
  }

  fun findContainingMavenizedModule(psiFile: PsiFile): Module? {
    val file = psiFile.getVirtualFile()
    if (file == null) return null

    val project = psiFile.getProject()

    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return null

    val index = ProjectRootManager.getInstance(project).getFileIndex()

    val module = index.getModuleForFile(file)
    if (module == null || !manager.isMavenizedModule(module)) return null
    return module
  }

  @JvmStatic
  fun isMavenProperty(target: PsiElement?): Boolean {
    val tag = PsiTreeUtil.getParentOfType<XmlTag?>(target, XmlTag::class.java, false)
    if (tag == null) return false
    return DomUtil.findDomElement<MavenDomProperties?>(tag, MavenDomProperties::class.java) != null
  }

  @JvmStatic
  fun calcRelativePath(parent: VirtualFile, child: VirtualFile): String {
    var result = FileUtil.getRelativePath(parent.getPath(), child.getPath(), '/')
    if (result == null) {
      MavenLog.LOG.warn("cannot calculate relative path for\nparent: $parent\nchild: $child")
      result = child.getPath()
    }
    return FileUtil.toSystemIndependentName(result)
  }

  @JvmStatic
  fun updateMavenParent(mavenModel: MavenDomProjectModel, parentProject: MavenProject): MavenDomParent {
    val result = mavenModel.getMavenParent()

    val pomFile = DomUtil.getFile(mavenModel).getVirtualFile()
    val project = mavenModel.getManager().getProject()

    val parentId = parentProject.mavenId
    result.getGroupId().setStringValue(parentId.groupId)
    result.getArtifactId().setStringValue(parentId.artifactId)
    result.getVersion().setStringValue(parentId.version)

    if (!Comparing.equal<VirtualFile?>(pomFile.getParent().getParent(), parentProject.directoryFile)
        || !FileUtil.namesEqual(MavenConstants.POM_XML, parentProject.file.getName())
    ) {
      result.getRelativePath().setValue(PsiManager.getInstance(project).findFile(parentProject.file))
    }

    return result
  }

  @JvmStatic
  fun getVirtualFile(element: DomElement): VirtualFile? {
    val psiFile: PsiFile = DomUtil.getFile(element)
    return doGetVirtualFile(psiFile)
  }

  @JvmStatic
  fun getVirtualFile(element: PsiElement): VirtualFile? {
    val psiFile = element.getContainingFile()
    return doGetVirtualFile(psiFile)
  }

  private fun doGetVirtualFile(psiFile: PsiFile?): VirtualFile? {
    var psiFile = psiFile
    if (psiFile == null) return null
    psiFile = psiFile.getOriginalFile()
    var virtualFile = psiFile.getVirtualFile()
    if (virtualFile is LightVirtualFile) {
      virtualFile = ObjectUtils.chooseNotNull<VirtualFile>(
        psiFile.getUserData<VirtualFile?>(MavenGroovyPomCompletionContributor.ORIGINAL_POM_FILE), virtualFile)
    }
    return virtualFile
  }

  @JvmStatic
  fun findProject(projectDom: MavenDomProjectModel): MavenProject? {
    val element = projectDom.getXmlElement()
    if (element == null) return null

    val file = getVirtualFile(element)
    if (file == null) return null
    val manager = MavenProjectsManager.getInstance(element.getProject())
    return manager.findProject(file)
  }

  @JvmStatic
  fun findContainingProject(element: DomElement): MavenProject? {
    val psi: PsiElement? = element.getXmlElement()
    return if (psi == null) null else findContainingProject(psi)
  }

  @JvmStatic
  fun findContainingProject(element: PsiElement): MavenProject? {
    val file = getVirtualFile(element)
    if (file == null) return null
    val manager = MavenProjectsManager.getInstance(element.getProject())
    return manager.findContainingProject(file)
  }

  @JvmStatic
  fun getMavenDomProjectModel(project: Project, file: VirtualFile): MavenDomProjectModel? {
    return getMavenDomModel(project, file, MavenDomProjectModel::class.java)
  }

  @JvmStatic
  fun getMavenDomProjectModel(file: PsiFile): MavenDomProjectModel? {
    return getMavenDomModel(file, MavenDomProjectModel::class.java)
  }

  @JvmStatic
  fun getMavenDomProfilesModel(project: Project, file: VirtualFile): MavenDomProfiles? {
    val model = getMavenDomModel<MavenDomProfilesModel>(project, file, MavenDomProfilesModel::class.java)
    if (model != null) return model.getProfiles()
    return getMavenDomModel<MavenDomProfiles>(project, file, MavenDomProfiles::class.java) // try an old-style model
  }

  @JvmStatic
  fun <T : MavenDomElement?> getMavenDomModel(
    project: Project,
    file: VirtualFile,
    clazz: Class<T>,
  ): T? {
    if (!file.isValid()) return null
    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile == null) return null
    return getMavenDomModel<T>(psiFile, clazz)
  }

  @JvmStatic
  fun <T : MavenDomElement?> getMavenDomModel(file: PsiFile, clazz: Class<T>): T? {
    val fileElement = getMavenDomFile<T>(file, clazz)
    return fileElement?.getRootElement()
  }

  private fun <T : MavenDomElement?> getMavenDomFile(file: PsiFile, clazz: Class<T>): DomFileElement<T?>? {
    if (file !is XmlFile) return null
    return DomManager.getDomManager(file.getProject()).getFileElement<T?>(file, clazz)
  }

  @JvmStatic
  fun findTag(domElement: DomElement, path: String): XmlTag? {
    val elements = StringUtil.split(path, ".")
    if (elements.isEmpty()) return null

    var nameAndIndex = translateTagName(elements[0])
    var name = nameAndIndex!!.first
    var index = nameAndIndex.second

    var result = domElement.getXmlTag()
    if (result == null || name != result.getName()) return null
    result = getIndexedTag(result, index)

    for (each in elements.subList(1, elements.size)) {
      nameAndIndex = translateTagName(each)
      name = nameAndIndex!!.first
      index = nameAndIndex.second

      result = result!!.findFirstSubTag(name)
      if (result == null) return null
      result = getIndexedTag(result, index)
    }
    return result
  }

  private fun translateTagName(text: String): Pair<String, Int?>? {
    var tagName = text.trim { it <= ' ' }
    var index: Int? = null

    val matcher = XML_TAG_NAME_PATTERN.matcher(tagName)
    if (matcher.find()) {
      tagName = matcher.group(1)
      try {
        index = matcher.group(2).toInt()
      }
      catch (_: NumberFormatException) {
        return null
      }
    }

    return tagName to index
  }

  private fun getIndexedTag(parent: XmlTag, index: Int?): XmlTag? {
    if (index == null) return parent

    val children = parent.getSubTags()
    if (index < 0 || index >= children.size) return null
    return children[index]
  }

  @JvmStatic
  fun getPropertiesFile(project: Project, file: VirtualFile): PropertiesFile? {
    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile !is PropertiesFile) return null
    return psiFile as PropertiesFile
  }

  @JvmStatic
  fun findProperty(project: Project, file: VirtualFile, propName: String): IProperty? {
    val propertiesFile = getPropertiesFile(project, file)
    return propertiesFile?.findPropertyByKey(propName)
  }

  fun findPropertyValue(project: Project, file: VirtualFile, propName: String): PsiElement? {
    val prop = findProperty(project, file, propName)
    return prop?.getPsiElement()?.getFirstChild()?.getNextSibling()?.getNextSibling()
  }

  private fun getFilteredResourcesRoots(mavenProject: MavenProject): MutableSet<VirtualFile?>? {
    var cachedValue = mavenProject.getCachedValue<Pair<Long?, MutableSet<VirtualFile?>?>?>(FILTERED_RESOURCES_ROOTS_KEY)

    if (cachedValue == null || cachedValue.first != VirtualFileManager.getInstance().getModificationCount()) {
      var set: MutableSet<VirtualFile?>? = null

      for (resource in ContainerUtil.concat<MavenResource>(mavenProject.resources, mavenProject.testResources)) {
        if (!resource.isFiltered) continue

        val resourceDirectory = resource.directory
        val resourceDir = LocalFileSystem.getInstance().findFileByPath(resourceDirectory)
        if (resourceDir == null) continue

        if (set == null) {
          set = HashSet<VirtualFile?>()
        }

        set.add(resourceDir)
      }

      if (set == null) {
        set = mutableSetOf<VirtualFile?>()
      }

      cachedValue = VirtualFileManager.getInstance().getModificationCount() to set
      mavenProject.putCachedValue<Pair<Long?, MutableSet<VirtualFile?>?>?>(FILTERED_RESOURCES_ROOTS_KEY, cachedValue)
    }

    return cachedValue.second
  }

  @JvmStatic
  fun isFilteredResourceFile(element: PsiElement): Boolean {
    val psiFile = element.getContainingFile()
    val file = doGetVirtualFile(psiFile)
    if (file == null) return false

    val manager = MavenProjectsManager.getInstance(psiFile!!.getProject())
    val mavenProject = manager.findContainingProject(file)
    if (mavenProject == null) return false

    val filteredRoots = getFilteredResourcesRoots(mavenProject)

    if (!filteredRoots!!.isEmpty()) {
      var f = file.getParent()
      while (f != null) {
        if (filteredRoots.contains(f)) {
          return true
        }
        f = f.getParent()
      }
    }

    return false
  }

  @JvmStatic
  fun collectProjectModels(p: Project): MutableList<DomFileElement<MavenDomProjectModel?>?> {
    return DomService.getInstance().getFileElements<MavenDomProjectModel?>(MavenDomProjectModel::class.java, p,
                                                                           GlobalSearchScope.projectScope(p))
  }

  @JvmStatic
  fun describe(psiFile: PsiFile): MavenId {
    val model = getMavenDomModel<MavenDomProjectModel>(psiFile, MavenDomProjectModel::class.java)

    if (model == null) {
      return MavenId(null, null, null)
    }

    var groupId = model.getGroupId().getStringValue()
    val artifactId = model.getArtifactId().getStringValue()
    var version = model.getVersion().getStringValue()

    if (groupId == null) {
      groupId = model.getMavenParent().getGroupId().getStringValue()
    }

    if (version == null) {
      version = model.getMavenParent().getVersion().getStringValue()
    }

    return MavenId(groupId, artifactId, version)
  }

  @JvmStatic
  fun createDomDependency(
    model: MavenDomProjectModel,
    editor: Editor?,
    id: MavenId,
  ): MavenDomDependency {
    return createDomDependency(model.getDependencies(), editor, id)
  }


  @JvmStatic
  fun createDomDependency(
    dependencies: MavenDomDependencies,
    editor: Editor?,
    id: MavenCoordinate,
  ): MavenDomDependency {
    val dep = createDomDependency(dependencies, editor)

    dep.getGroupId().setStringValue(id.getGroupId())
    dep.getArtifactId().setStringValue(id.getArtifactId())
    dep.getVersion().setStringValue(id.getVersion())

    return dep
  }

  @JvmStatic
  fun createDomDependency(model: MavenDomProjectModel, editor: Editor?): MavenDomDependency {
    return createDomDependency(model.getDependencies(), editor)
  }

  @JvmStatic
  fun createDomDependency(dependencies: MavenDomDependencies, editor: Editor?): MavenDomDependency {
    val index = getCollectionIndex(dependencies, editor)
    if (index >= 0) {
      val childDescription = dependencies.getGenericInfo().getCollectionChildDescription("dependency")
      if (childDescription != null) {
        val element = childDescription.addValue(dependencies, index)
        if (element is MavenDomDependency) {
          return element
        }
      }
    }
    return dependencies.addDependency()
  }


  fun getCollectionIndex(dependencies: MavenDomDependencies, editor: Editor?): Int {
    if (editor != null) {
      val offset = editor.getCaretModel().offset

      val dependencyList = dependencies.getDependencies()

      for (i in dependencyList.indices) {
        val dependency = dependencyList[i]
        val xmlElement = dependency.getXmlElement()

        if (xmlElement != null && xmlElement.getTextRange().startOffset >= offset) {
          return i
        }
      }
    }
    return -1
  }

  @JvmStatic
  fun getProjectName(model: MavenDomProjectModel): String {
    val mavenProject = findProject(model)
    if (mavenProject != null) {
      return mavenProject.displayName
    }
    else {
      val name = model.getName().getStringValue()
      if (!name.isNullOrBlank()) {
        return name
      }
      else {
        return "pom.xml" // ?
      }
    }
  }

  @JvmStatic
  fun getMavenVersion(file: VirtualFile?, project: Project): String? {
    val directory = file?.getParent()
    val distribution: MavenDistribution?
    if (directory == null) {
      distribution = MavenDistributionsCache.getInstance(project).getSettingsDistribution()
    }
    else {
      distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(directory.getPath())
    }
    return distribution.version
  }

  @JvmStatic
  fun isAtLeastMaven4(file: VirtualFile?, project: Project): Boolean {
    return StringUtil.compareVersionNumbers(getMavenVersion(file, project), "4") >= 0
  }
}
