// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.setupPy

import com.intellij.ide.IdeView
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.SystemProperties
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackageSnapshot
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import java.util.function.Supplier

class CreateSetupPyAction : CreateFromTemplateAction(
  SETUP_SCRIPT_TEMPLATE_NAME,
  PythonFileType.INSTANCE.icon,
  Supplier { FileTemplateManager.getDefaultInstance().getInternalTemplate(SETUP_SCRIPT_TEMPLATE_NAME) }
) {
  init {
    getTemplatePresentation().setText(PyBundle.message("python.packaging.create.setup.py"))
  }

  override fun update(e: AnActionEvent) {
    val module = e.getData<Module?>(PlatformCoreDataKeys.MODULE)
    e.presentation.setEnabled(module != null && !PyPackageUtil.hasSetupPy(module))
  }

  public override fun getAttributesDefaults(dataContext: DataContext): AttributesDefaults {
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    val defaults = AttributesDefaults("setup.py").withFixedName(true)
    if (project != null) {
      defaults.addPredefined("Import", getSetupImport(dataContext))
      defaults.add("Package_name", project.getName())
      val properties = PropertiesComponent.getInstance()
      defaults.add("Author", properties.getValue(AUTHOR_PROPERTY, SystemProperties.getUserName()))
      defaults.add("Author_email", properties.getValue(EMAIL_PROPERTY, ""))
      defaults.addPredefined("PackageList", getPackageList(dataContext))
      defaults.addPredefined("PackageDirs", getPackageDirs(dataContext))
    }
    defaults.attributeVisibleNames = visibleNames
    return defaults
  }

  override fun getTargetDirectory(dataContext: DataContext, view: IdeView?): PsiDirectory? {
    val module = PlatformCoreDataKeys.MODULE.getData(dataContext)
    if (module != null) {
      val sourceRoots = PyUtil.getSourceRoots(module)
      if (!sourceRoots.isEmpty()) {
        return PsiManager.getInstance(module.getProject()).findDirectory(sourceRoots.iterator().next()!!)
      }
    }
    return super.getTargetDirectory(dataContext, view)
  }

  override fun elementCreated(dialog: CreateFromTemplateDialog, createdElement: PsiElement?) {
    val propertiesComponent = PropertiesComponent.getInstance()
    val properties = dialog.enteredProperties
    val author = properties.getProperty("Author")
    if (author != null) {
      propertiesComponent.setValue(AUTHOR_PROPERTY, author)
    }
    val authorEmail = properties.getProperty("Author_email")
    if (authorEmail != null) {
      propertiesComponent.setValue(EMAIL_PROPERTY, authorEmail)
    }
  }

  companion object {
    private const val AUTHOR_PROPERTY = "python.packaging.author"
    private const val EMAIL_PROPERTY = "python.packaging.author.email"
    const val SETUP_SCRIPT_TEMPLATE_NAME: String = "Setup Script"

    private val visibleNames: MutableMap<String?, String?>
      get() {
        val attributeToName = HashMap<String?, String?>()
        attributeToName.put("Package_name", PyBundle.message("python.packaging.create.setup.package.name"))
        attributeToName.put("Version", PyBundle.message("python.packaging.create.setup.version"))
        attributeToName.put("URL", PyBundle.message("python.packaging.create.setup.url"))
        attributeToName.put("License", PyBundle.message("python.packaging.create.setup.license"))
        attributeToName.put("Author", PyBundle.message("python.packaging.create.setup.author"))
        attributeToName.put("Author_Email", PyBundle.message("python.packaging.create.setup.author.email"))
        attributeToName.put("Description", PyBundle.message("python.packaging.create.setup.description"))
        return attributeToName
      }

    private fun getSetupImport(dataContext: DataContext): String {
      val module = PlatformCoreDataKeys.MODULE.getData(dataContext)
      return if (hasSetuptoolsPackage(module)) "from setuptools import setup" else "from distutils.core import setup"
    }

    private fun hasSetuptoolsPackage(module: Module?): Boolean {
      val sdk = PythonSdkUtil.findPythonSdk(module)
      if (sdk == null) return false

      val project = module?.project ?: return false
      val packageManager = PythonPackageManager.forSdk(project, sdk)

      return packageManager.hasInstalledPackageSnapshot("setuptools")
    }

    private fun getPackageList(dataContext: DataContext): String {
      val module = PlatformCoreDataKeys.MODULE.getData(dataContext)
      if (module != null) {
        return "['" + StringUtil.join(PyPackageUtil.getPackageNames(module), "', '") + "']"
      }
      return "[]"
    }

    private fun getPackageDirs(dataContext: DataContext): String {
      val module = PlatformCoreDataKeys.MODULE.getData(dataContext)
      if (module != null) {
        val sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots()
        for (sourceRoot in sourceRoots) {
          // TODO notify if we have multiple source roots and can't build mapping automatically
          val contentRoot = ProjectFileIndex.getInstance(module.getProject()).getContentRootForFile(sourceRoot)
          if (contentRoot != null && !Comparing.equal<VirtualFile?>(contentRoot, sourceRoot)) {
            val relativePath: String = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '/')!!
            return "\n    package_dir={'': '" + relativePath + "'},"
          }
        }
      }
      return ""
    }
  }
}