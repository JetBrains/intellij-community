// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.javaee

import com.intellij.application.options.PathMacrosImpl
import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.javaee.ExternalResourceManagerEx.XMLSchemaVersion
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ArrayUtilRt
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.URLUtil
import com.intellij.xml.Html5SchemaProvider
import com.intellij.xml.XmlSchemaProvider
import com.intellij.xml.index.XmlNamespaceIndex
import com.intellij.xml.util.XmlUtil
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedList
import java.util.TreeSet

private val LOG = logger<ExternalResourceManagerExImpl>()
private const val DEFAULT_VERSION = ""

private const val CATALOG_PROPERTIES_ELEMENT = "CATALOG_PROPERTIES"
private val XSD_1_1 = ExternalResource(
  file = "/standardSchemas/XMLSchema-1_1/XMLSchema.xsd",
  aClass = ExternalResourceManagerExImpl::class.java,
  classLoader = null,
).getResourceUrl()

private const val HTML5_DOCTYPE_ELEMENT = "HTML5"

private const val URL_ATTR: @NonNls String = "url"
private const val RESOURCE_ELEMENT: @NonNls String = "resource"
private const val LOCATION_ATTR: @NonNls String = "location"
private const val IGNORED_RESOURCE_ELEMENT: @NonNls String = "ignored-resource"
private const val HTML_DEFAULT_DOCTYPE_ELEMENT: @NonNls String = "default-html-doctype"
private const val XML_SCHEMA_VERSION: @NonNls String = "xml-schema-version"

@State(name = "ExternalResourceManagerImpl", storages = [Storage("javaeeExternalResources.xml")], category = SettingsCategory.CODE)
open class ExternalResourceManagerExImpl : ExternalResourceManagerEx(), PersistentStateComponent<Element?> {
  private val resources = HashMap<String, MutableMap<String, String>>()
  private val resourceLocations = HashSet<String>()

  private val ignoredResources = Collections.synchronizedSet<String>(TreeSet<String>())
  private val standardIgnoredResources = Collections.synchronizedSet<String>(TreeSet<String>())

  private val standardResources = SynchronizedClearableLazy { computeStdResources() }

  private val urlByNamespaceProvider = CachedValueProvider {
    val result = MultiMap<String, String>()
    for (map in standardResources.value.values) {
      for (entry in map.entries) {
        val url = entry.value.getResourceUrl() ?: continue
        val file = VfsUtilCore.findRelativeFile(url, null) ?: continue
        val namespace = XmlNamespaceIndex.computeNamespace(file) ?: continue
        result.putValue(namespace, entry.key)
      }
    }
    CachedValueProvider.Result.create(result, this)
  }

  private var defaultHtmlDoctype: String? = HTML5_DOCTYPE_ELEMENT
  private var xmlSchemaVersion = XMLSchemaVersion.XMLSchema_1_0

  private var myCatalogPropertiesFile: String? = null
  private var myCatalogManager: XMLCatalogManager? = null

  init {
    StandardResourceProvider.EP_NAME.addChangeListener(::dropCache, null)
    StandardResourceEP.EP_NAME.addChangeListener(::dropCache, null)
  }

  companion object {
    const val J2EE_1_3: @NonNls String = "http://java.sun.com/dtd/"
    const val J2EE_1_2: @NonNls String = "http://java.sun.com/j2ee/dtds/"
    const val J2EE_NS: @NonNls String = "http://java.sun.com/xml/ns/j2ee/"
    const val JAVAEE_NS: @NonNls String = "http://java.sun.com/xml/ns/javaee/"
    const val JCP_NS: @NonNls String = "http://xmlns.jcp.org/xml/ns/javaee/"
    const val JAKARTA_NS: @NonNls String = "https://jakarta.ee/xml/ns/jakartaee/"

    internal fun <T> getOrCreateMap(resources: MutableMap<String, MutableMap<String, T>>, version: String?): MutableMap<String, T> {
      return resources.computeIfAbsent(version ?: DEFAULT_VERSION) { HashMap() }
    }

    @TestOnly
    @JvmStatic
    fun registerResourceTemporarily(url: String, location: String, disposable: Disposable) {
      val app = ApplicationManager.getApplication()
      app.runWriteAction(Runnable { getInstance().addResource(url, location) })
      Disposer.register(disposable, Disposable { app.runWriteAction(Runnable { getInstance().removeResource(url) }) })
    }
  }

  internal open fun computeStdResources(): Map<String, MutableMap<String, ExternalResource>> {
    val registrar = ResourceRegistrarImpl()
    for (provider in StandardResourceProvider.EP_NAME.lazySequence()) {
      provider.registerResources(registrar)
    }
    for (item in StandardResourceEP.EP_NAME.filterableLazySequence()) {
      val extension = item.instance ?: continue
      registrar.addStdResource(
        extension.url,
        extension.version,
        extension.resourcePath,
        null,
        item.pluginDescriptor.getPluginClassLoader(),
      )
    }

    standardIgnoredResources.clear()
    standardIgnoredResources.addAll(registrar.getIgnoredResources())
    return registrar.getResources()
  }

  private fun dropCache() {
    standardResources.drop()
    incModificationCount()
  }

  override fun isStandardResource(file: VirtualFile): Boolean {
    val parent = file.getParent()
    return parent != null && parent.getName() == "standardSchemas"
  }

  override fun isUserResource(file: VirtualFile): Boolean = resourceLocations.contains(file.url)

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getResourceLocation(url: String): String = getResourceLocation(url, DEFAULT_VERSION)

  override fun getResourceLocation(url: @NonNls String, version: String?): String {
    var result = getUserResource(url, version ?: DEFAULT_VERSION)
    if (result == null) {
      val manager = getCatalogManager()
      if (manager != null) {
        result = manager.resolve(url)
      }

      if (result == null) {
        result = getStdResource(url, version)
        if (result == null) {
          return url
        }
      }
    }
    return result
  }

  override fun getUserResource(project: Project, url: String, version: String?): String? {
    return getProjectResources(project).getUserResource(url, version) ?: getUserResource(url, version)
  }

  override fun getStdResource(url: String, version: String?): String? {
    return getMap(standardResources.value, version)?.get(url)?.getResourceUrl()
  }

  private fun getUserResource(url: String, version: String?): String? = getMap<String>(resources, version)?.get(url)

  override fun getResourceLocation(url: @NonNls String, project: Project): String? {
    return getResourceLocation(url = url, version = null, project = project)
  }

  private fun getResourceLocation(url: @NonNls String, version: String?, project: Project): String? {
    val projectResources = getProjectResources(project)
    val location = projectResources.getResourceLocation(url, version)
    if (location != url) {
      return location
    }

    if (projectResources.xmlSchemaVersion == XMLSchemaVersion.XMLSchema_1_1) {
      if (XmlUtil.XML_SCHEMA_URI == url) {
        return XSD_1_1
      }
      if ("${XmlUtil.XML_SCHEMA_URI}.xsd" == url) {
        return XSD_1_1
      }
    }
    return getResourceLocation(url, version)
  }

  override fun getResourceLocation(url: @NonNls String, baseFile: PsiFile, version: String?): PsiFile? {
    XmlSchemaProvider.findSchema(url, baseFile)?.let {
      return it
    }

    val location = getResourceLocation(url, version, baseFile.getProject())!!
    return XmlUtil.findXmlFile(baseFile, location)
  }

  override fun getResourceUrls(fileType: FileType?, includeStandard: Boolean): Array<String?> {
    return getResourceUrls(fileType, DEFAULT_VERSION, includeStandard)
  }

  override fun getResourceUrls(fileType: FileType?, version: @NonNls String?, includeStandard: Boolean): Array<String?> {
    val result = LinkedList<String>()
    addResourcesFromMap(result = result, version = version, resourcesMap = resources)

    if (includeStandard) {
      addResourcesFromMap(result = result, version = version, resourcesMap = standardResources.value)
    }

    return ArrayUtilRt.toStringArray(result)
  }

  override fun addResource(url: String, location: String) {
    addResource(url = url, version = DEFAULT_VERSION, location = location)
  }

  override fun addResource(url: @NonNls String, version: @NonNls String?, location: @NonNls String) {
    ThreadingAssertions.assertWriteAccess()
    addSilently(url, version, location)
    fireExternalResourceChanged()
  }

  private fun addSilently(url: String, version: String?, location: String) {
    getOrCreateMap(resources, version).put(url, location)
    resourceLocations.add(location)
    incModificationCount()
  }

  override fun removeResource(url: String) {
    removeResource(url, DEFAULT_VERSION)
  }

  override fun removeResource(url: String, version: String?) {
    ThreadingAssertions.assertWriteAccess()
    val map = getMap(resources, version) ?: return
    val location = map.remove(url)
    if (location != null) {
      resourceLocations.remove(location)
    }
    incModificationCount()
    fireExternalResourceChanged()
  }

  override fun removeResource(url: String, project: Project) {
    getProjectResources(project).removeResource(url)
  }

  override fun addResource(url: @NonNls String, location: @NonNls String, project: Project) {
    getProjectResources(project).addResource(url, location)
  }

  override fun getAvailableUrls(): Array<String?> {
    val urls: MutableSet<String?> = HashSet<String?>()
    for (map in resources.values) {
      urls.addAll(map.keys)
    }
    return ArrayUtilRt.toStringArray(urls)
  }

  override fun getAvailableUrls(project: Project): Array<String?> {
    return getProjectResources(project).getAvailableUrls()
  }

  override fun clearAllResources() {
    resources.clear()
    ignoredResources.clear()
  }

  override fun clearAllResources(project: Project) {
    ThreadingAssertions.assertWriteAccess()
    clearAllResources()
    getProjectResources(project).clearAllResources()
    incModificationCount()
    fireExternalResourceChanged()
  }

  override fun addIgnoredResources(urls: MutableList<String>, disposable: Disposable?) {
    val app = ApplicationManager.getApplication()
    if (app.isWriteAccessAllowed()) {
      doAddIgnoredResources(urls, disposable)
    }
    else {
      app.runWriteAction(Runnable { doAddIgnoredResources(urls, disposable) })
    }
  }

  private fun doAddIgnoredResources(urls: MutableList<String>, disposable: Disposable?) {
    val modificationCount = getModificationCount()
    for (url in urls) {
      addIgnoredSilently(url)
    }

    if (modificationCount != getModificationCount()) {
      if (disposable != null) {
        Disposer.register(disposable, Disposable {
          ApplicationManager.getApplication().runWriteAction(Runnable {
            var isChanged = false
            for (url in urls) {
              if (ignoredResources.remove(url)) {
                isChanged = true
              }
            }
            if (isChanged) {
              fireExternalResourceChanged()
            }
          })
        })
      }

      fireExternalResourceChanged()
    }
  }

  private fun addIgnoredSilently(url: String) {
    if (standardIgnoredResources.contains(url)) {
      return
    }

    if (ignoredResources.add(url)) {
      incModificationCount()
    }
  }

  override fun isIgnoredResource(url: String): Boolean {
    if (ignoredResources.contains(url)) {
      return true
    }

    // ensure ignored resources are loaded
    standardResources.value
    return standardIgnoredResources.contains(url) || isImplicitNamespaceDescriptor(url)
  }

  override fun getIgnoredResources(): Array<String?> {
    // ensure ignored resources are loaded
    standardResources.value

    if (ignoredResources.isEmpty()) {
      return ArrayUtilRt.toStringArray(standardIgnoredResources)
    }

    val set: MutableSet<String?> = HashSet<String?>(ignoredResources.size + standardIgnoredResources.size)
    set.addAll(ignoredResources)
    set.addAll(standardIgnoredResources)
    return ArrayUtilRt.toStringArray(set)
  }

  override fun getModificationCount(project: Project): Long = getProjectResources(project).modificationCount

  override fun getState(): Element? {
    val element = Element("state")

    val urls = TreeSet<String>()
    for (map in resources.values) {
      urls.addAll(map.keys)
    }

    for (url in urls) {
      val location = getResourceLocation(url)
      val e = Element(RESOURCE_ELEMENT)
      e.setAttribute(URL_ATTR, url)
      e.setAttribute(LOCATION_ATTR, location.replace(File.separatorChar, '/'))
      element.addContent(e)
    }

    ignoredResources.removeAll(standardIgnoredResources)
    for (ignoredResource in ignoredResources) {
      val e = Element(IGNORED_RESOURCE_ELEMENT)
      e.setAttribute(URL_ATTR, ignoredResource)
      element.addContent(e)
    }

    if (defaultHtmlDoctype != null && HTML5_DOCTYPE_ELEMENT != defaultHtmlDoctype) {
      val e = Element(HTML_DEFAULT_DOCTYPE_ELEMENT)
      e.setText(defaultHtmlDoctype)
      element.addContent(e)
    }
    if (xmlSchemaVersion != XMLSchemaVersion.XMLSchema_1_0) {
      val e = Element(XML_SCHEMA_VERSION)
      e.setText(xmlSchemaVersion.toString())
      element.addContent(e)
    }
    if (myCatalogPropertiesFile != null) {
      val properties = Element(CATALOG_PROPERTIES_ELEMENT)
      properties.setText(myCatalogPropertiesFile)
      element.addContent(properties)
    }

    val macroReplacements = ReplacePathToMacroMap()
    PathMacrosImpl.getInstanceEx().addMacroReplacements(macroReplacements)
    macroReplacements.substitute(element, SystemInfo.isFileSystemCaseSensitive)
    return element
  }

  override fun loadState(state: Element) {
    val macroExpands = ExpandMacroToPathMap()
    PathMacrosImpl.getInstanceEx().addMacroExpands(macroExpands)
    macroExpands.substitute(state, SystemInfo.isFileSystemCaseSensitive)

    incModificationCount()
    for (element in state.getChildren(RESOURCE_ELEMENT)) {
      val url = element.getAttributeValue(URL_ATTR) ?: continue
      if (url.isNotEmpty()) {
        addSilently(
          url = url,
          version = DEFAULT_VERSION,
          location = element.getAttributeValue(LOCATION_ATTR)!!.replace('/', File.separatorChar),
        )
      }
    }

    ignoredResources.clear()
    for (element in state.getChildren(IGNORED_RESOURCE_ELEMENT)) {
      addIgnoredSilently(element.getAttributeValue(URL_ATTR))
    }

    val child = state.getChild(HTML_DEFAULT_DOCTYPE_ELEMENT)
    if (child != null) {
      var text = child.getText()
      if (FileUtil.toSystemIndependentName(text).endsWith(".jar!/resources/html5-schema/html5.rnc")) {
        text = HTML5_DOCTYPE_ELEMENT
      }
      defaultHtmlDoctype = text
    }
    val schemaElement = state.getChild(XML_SCHEMA_VERSION)
    if (schemaElement != null) {
      val text = schemaElement.getText()
      xmlSchemaVersion = if (XMLSchemaVersion.XMLSchema_1_1.toString() == text) XMLSchemaVersion.XMLSchema_1_1 else XMLSchemaVersion.XMLSchema_1_0
    }
    val catalogElement = state.getChild(CATALOG_PROPERTIES_ELEMENT)
    if (catalogElement != null) {
      myCatalogPropertiesFile = catalogElement.textTrim
    }
  }

  private fun fireExternalResourceChanged() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher<ExternalResourceListener>(
      ExternalResourceListener.TOPIC).externalResourceChanged()
    incModificationCount()
  }

  internal fun getStandardResources(): Collection<MutableMap<String, ExternalResource>> = standardResources.value.values

  override fun getDefaultHtmlDoctype(project: Project): String {
    val doctype = getProjectResources(project).defaultHtmlDoctype
    return when {
      XmlUtil.XHTML_URI == doctype -> XmlUtil.XHTML4_SCHEMA_LOCATION
      HTML5_DOCTYPE_ELEMENT == doctype -> Html5SchemaProvider.getHtml5SchemaLocation()
      else -> doctype!!
    }
  }

  override fun setDefaultHtmlDoctype(defaultHtmlDoctype: String, project: Project) {
    getProjectResources(project).setDefaultHtmlDoctype(defaultHtmlDoctype)
  }

  override fun getXmlSchemaVersion(project: Project): XMLSchemaVersion {
    return getProjectResources(project).xmlSchemaVersion
  }

  override fun setXmlSchemaVersion(version: XMLSchemaVersion, project: Project) {
    getProjectResources(project).xmlSchemaVersion = version
    fireExternalResourceChanged()
  }

  override fun getCatalogPropertiesFile(): String? = myCatalogPropertiesFile

  override fun setCatalogPropertiesFile(filePath: String?) {
    myCatalogManager = null
    myCatalogPropertiesFile = filePath
    incModificationCount()
  }

  override fun getUrlsByNamespace(project: Project): MultiMap<String, String>? {
    return CachedValuesManager.getManager(project).getCachedValue(project, urlByNamespaceProvider)
  }

  private fun getCatalogManager(): XMLCatalogManager? {
    if (myCatalogManager == null && myCatalogPropertiesFile != null) {
      myCatalogManager = XMLCatalogManager(myCatalogPropertiesFile!!)
    }
    return myCatalogManager
  }

  private fun setDefaultHtmlDoctype(defaultHtmlDoctype: String?) {
    incModificationCount()

    if (Html5SchemaProvider.getHtml5SchemaLocation() == defaultHtmlDoctype) {
      this.defaultHtmlDoctype = HTML5_DOCTYPE_ELEMENT
    }
    else {
      this.defaultHtmlDoctype = defaultHtmlDoctype
    }
    fireExternalResourceChanged()
  }
}

internal class ExternalResource(file: String, aClass: Class<*>?, classLoader: ClassLoader?) {
  private val file = file
  private val classLoader = classLoader
  private val aClass: Class<*>? = aClass

  @Volatile
  private var resolvedResourcePath: String? = null

  constructor(file: String, baseResource: ExternalResource) : this(file = file, aClass = baseResource.aClass, classLoader = baseResource.classLoader)

  fun directoryName(): String {
    val i = file.lastIndexOf('/')
    return if (i > 0) file.substring(0, i) else file
  }

  @Suppress("LoggingSimilarMessage")
  fun getResourceUrl(): String? {
    resolvedResourcePath?.let {
      return it
    }

    val resource = if (aClass == null) classLoader!!.getResource(file) else aClass.getResource(file)
    if (resource == null) {
      val message = "Cannot find standard resource. filename:${this@ExternalResource.file} class=$aClass, classLoader:$classLoader"
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.warn(message)
      }
      else {
        LOG.warn(message)
      }

      resolvedResourcePath = null
      return null
    }

    val path = URLUtil.unescapePercentSequences(resource.toString().replace('\\', '/'))
    resolvedResourcePath = path
    return path
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val resource = o as ExternalResource
    return classLoader == resource.classLoader && aClass == resource.aClass && file == resource.file
  }

  override fun hashCode(): Int = this@ExternalResource.file.hashCode()

  override fun toString(): String = "${this@ExternalResource.file} for $classLoader"
}

private fun <T> getMap(resources: Map<String, MutableMap<String, T>>, version: String?): MutableMap<String, T>? {
  var version = version ?: DEFAULT_VERSION
  val map = resources.get(version)
  return if (map == null && version != DEFAULT_VERSION) resources.get(DEFAULT_VERSION) else map
}

private fun <T> addResourcesFromMap(
  result: MutableList<String>,
  version: String?,
  resourcesMap: Map<String, MutableMap<String, T>>,
) {
  getMap(resourcesMap, version)?.let {
    result.addAll(it.keys)
  }
}

private fun isImplicitNamespaceDescriptor(url: String): Boolean {
  for (provider in ImplicitNamespaceDescriptorProvider.EP_NAME.extensionList) {
    if (provider.getNamespaceDescriptor(null, url, null) != null) {
      return true
    }
  }
  return false
}

private fun getProjectResources(project: Project): ExternalResourceManagerExImpl = project.service<ExternalResourceManagerExImpl>()