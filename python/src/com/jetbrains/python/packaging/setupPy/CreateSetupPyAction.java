// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.setupPy;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.SystemProperties;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class CreateSetupPyAction extends CreateFromTemplateAction {
  private static final String AUTHOR_PROPERTY = "python.packaging.author";
  private static final String EMAIL_PROPERTY = "python.packaging.author.email";
  static final String SETUP_SCRIPT_TEMPLATE_NAME = "Setup Script";

  public CreateSetupPyAction() {
    super(
      SETUP_SCRIPT_TEMPLATE_NAME, 
      PythonFileType.INSTANCE.getIcon(), 
      () -> FileTemplateManager.getDefaultInstance().getInternalTemplate(SETUP_SCRIPT_TEMPLATE_NAME)
    );
    getTemplatePresentation().setText(PyBundle.message("python.packaging.create.setup.py"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Module module = e.getData(PlatformCoreDataKeys.MODULE);
    e.getPresentation().setEnabled(module != null && !PyPackageUtil.hasSetupPy(module));
  }

  @Override
  public AttributesDefaults getAttributesDefaults(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final AttributesDefaults defaults = new AttributesDefaults("setup.py").withFixedName(true);
    if (project != null) {
      defaults.addPredefined("Import", getSetupImport(dataContext));
      defaults.add("Package_name", project.getName());
      final PropertiesComponent properties = PropertiesComponent.getInstance();
      defaults.add("Author", properties.getValue(AUTHOR_PROPERTY, SystemProperties.getUserName()));
      defaults.add("Author_email", properties.getValue(EMAIL_PROPERTY, ""));
      defaults.addPredefined("PackageList", getPackageList(dataContext));
      defaults.addPredefined("PackageDirs", getPackageDirs(dataContext));
    }
    defaults.setAttributeVisibleNames(getVisibleNames());
    return defaults;
  }

  private static Map<String, String> getVisibleNames() {
    HashMap<String, String> attributeToName = new HashMap<>();
    attributeToName.put("Package_name", PyBundle.message("python.packaging.create.setup.package.name"));
    attributeToName.put("Version", PyBundle.message("python.packaging.create.setup.version"));
    attributeToName.put("URL", PyBundle.message("python.packaging.create.setup.url"));
    attributeToName.put("License", PyBundle.message("python.packaging.create.setup.license"));
    attributeToName.put("Author", PyBundle.message("python.packaging.create.setup.author"));
    attributeToName.put("Author_Email", PyBundle.message("python.packaging.create.setup.author.email"));
    attributeToName.put("Description", PyBundle.message("python.packaging.create.setup.description"));
    return attributeToName;
  }

  @NotNull
  private static String getSetupImport(@NotNull DataContext dataContext) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    return hasSetuptoolsPackage(module) ? "from setuptools import setup" : "from distutils.core import setup";
  }

  private static boolean hasSetuptoolsPackage(@Nullable Module module) {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) return false;

    final List<PyPackage> packages = PyPackageManager.getInstance(sdk).getPackages();
    return packages != null && PyPsiPackageUtil.findPackage(packages, "setuptools") != null;
  }

  private static String getPackageList(DataContext dataContext) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (module != null) {
      return "['" + StringUtil.join(PyPackageUtil.getPackageNames(module), "', '") + "']";
    }
    return "[]";
  }

  private static String getPackageDirs(DataContext dataContext) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (module != null) {
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      for (VirtualFile sourceRoot : sourceRoots) {
        // TODO notify if we have multiple source roots and can't build mapping automatically
        final VirtualFile contentRoot = ProjectFileIndex.getInstance(module.getProject()).getContentRootForFile(sourceRoot);
        if (contentRoot != null && !Comparing.equal(contentRoot, sourceRoot)) {
          final String relativePath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '/');
          return "\n    package_dir={'': '" + relativePath + "'},";
        }
      }
    }
    return "";
  }

  @Override
  protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (module != null) {
      final Collection<VirtualFile> sourceRoots = PyUtil.getSourceRoots(module);
      if (sourceRoots.size() > 0) {
        return PsiManager.getInstance(module.getProject()).findDirectory(sourceRoots.iterator().next());
      }
    }
    return super.getTargetDirectory(dataContext, view);
  }

  @Override
  protected void elementCreated(CreateFromTemplateDialog dialog, PsiElement createdElement) {
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final Properties properties = dialog.getEnteredProperties();
    final String author = properties.getProperty("Author");
    if (author != null) {
      propertiesComponent.setValue(AUTHOR_PROPERTY, author);
    }
    final String authorEmail = properties.getProperty("Author_email");
    if (authorEmail != null) {
      propertiesComponent.setValue(EMAIL_PROPERTY, authorEmail);
    }
  }
}