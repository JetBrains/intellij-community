package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.testFramework.HeavyPlatformTestCase
import java.io.File
import java.io.FileNotFoundException

internal abstract class HeavyFeaturesProviderTestCase<T : SearchEverywhereElementFeaturesProvider>(providerClass: Class<T>)
  : HeavyPlatformTestCase(), FeaturesProviderTestCase {
  override val provider: SearchEverywhereElementFeaturesProvider by lazy {
    SearchEverywhereElementFeaturesProvider.EP_NAME.findExtensionOrFail(providerClass)
  }

  override val testProject: Project
    get() = project


  inner class ModuleBuilder(moduleName: String) {
    private val defaultSourceName = "src"
    private val defaultTestSourceName = "test"
    private val defaultExcludedName = "excluded"

    private val moduleDirectory = createTempDir(moduleName)
    private val module = createModuleAt(moduleName, project, moduleType, moduleDirectory.toPath())

    fun source(name: String = defaultSourceName, init: Content.() -> Unit) {
      val content = Content(name)
      content.init()

      ModuleRootModificationUtil.updateModel(module) {
        val srcDirectoryUrl = VfsUtilCore.pathToUrl(content.contentDirectory.path)
        val contentEntry = it.addContentEntry(srcDirectoryUrl)
        contentEntry.addSourceFolder(srcDirectoryUrl, false)
      }
    }

    fun test(name: String = defaultTestSourceName, init: Content.() -> Unit) {
      val content = Content(name)
      content.init()

      ModuleRootModificationUtil.updateModel(module) {
        val srcDirectoryUrl = VfsUtilCore.pathToUrl(content.contentDirectory.path)
        val contentEntry = it.addContentEntry(srcDirectoryUrl)
        contentEntry.addSourceFolder(srcDirectoryUrl, true)
      }
    }

    fun excluded(name: String = defaultExcludedName, init: Content.() -> Unit) {
      val content = Content(name)
      content.init()

      ModuleRootModificationUtil.updateModel(module) {
        val excludedDirectoryUrl = VfsUtilCore.pathToUrl(content.contentDirectory.path)
        val contentEntry = it.addContentEntry(excludedDirectoryUrl)
        contentEntry.addExcludeFolder(excludedDirectoryUrl)
      }
    }

    private fun get(filename: String, packageName: String = "", contentRoot: String): VirtualFile {
      val packageDirectory = packageName.replace('.', File.separatorChar)
      val pathInsideModule = listOf(contentRoot, packageDirectory, filename).joinToString(File.separator)
      val file = File(moduleDirectory, pathInsideModule)
      if (!file.exists()) {
        throw FileNotFoundException("File not found: ${file.path}")
      }
      else {
        return file.toVirtualFile()
      }
    }

    fun getFromSource(filename: String, packageName: String = "", contentRoot: String = defaultSourceName) = get(filename,
                                                                                                                 packageName,
                                                                                                                 contentRoot)

    fun getFromTestSource(filename: String, packageName: String = "", contentRoot: String = defaultTestSourceName) = get(filename,
                                                                                                                         packageName,
                                                                                                                         contentRoot)

    fun getFromExcluded(filename: String, packageName: String = "", contentRoot: String = defaultExcludedName) = get(filename,
                                                                                                                     packageName,
                                                                                                                     contentRoot)

    inner class Content(name: String) {
      val contentDirectory = File(moduleDirectory, name).apply { mkdir() }

      fun createPackage(packageName: String, init: Package.() -> Unit) {
        val pack = Package(packageName)
        pack.init()
      }

      fun file(filename: String, afterCreate: ((file: VirtualFile) -> Unit)? = null) {
        File(contentDirectory, filename).apply { createNewFile() }
          .run { this.toVirtualFile() }
          .also { file -> afterCreate?.let { callback -> callback(file) } }
      }

      fun directory(filename: String, afterCreate: ((file: VirtualFile) -> Unit)?) {
        File(contentDirectory, filename).apply { mkdir() }
          .run { this.toVirtualFile() }
          .also { file -> afterCreate?.let { callback -> callback(file) } }
      }

      inner class Package(private val packageName: String) {
        private val packageDirectory = File(contentDirectory, packageName.replace('.', File.separatorChar)).apply { mkdirs() }

        fun file(filename: String, afterCreate: ((file: VirtualFile) -> Unit)? = null) {
          File(packageDirectory, filename).apply { createNewFile() }
            .run { this.toVirtualFile() }
            .also { file -> afterCreate?.let { callback -> callback(file) } }
        }

        fun directory(filename: String, afterCreate: ((file: VirtualFile) -> Unit)?) {
          File(packageDirectory, filename).apply { mkdir() }
            .run { this.toVirtualFile() }
            .also { file -> afterCreate?.let { callback -> callback(file) } }
        }

        fun createPackage(packageName: String, init: Package.() -> Unit) {
          Package("${this.packageName}.$packageName").init()
        }
      }
    }
  }

  fun module(name: String = "testModule", init: ModuleBuilder.() -> Unit): ModuleBuilder {
    return ModuleBuilder(name).apply(init)
  }


  protected fun File.toVirtualFile() = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(this.toPath())!!

  protected fun File.toPsi() = this.toVirtualFile().toPsi()

  protected fun VirtualFile.toPsi(): PsiFileSystemItem {
    if (this.isDirectory) {
      return psiManager.findDirectory(this)!!
    }
    else {
      return psiManager.findFile(this)!!
    }
  }
}