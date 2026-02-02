// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.jetbrains.python.module.PyModuleService;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.run.RunnableScriptFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public abstract class PythonTestLegacyConfigurationProducer<T extends AbstractPythonLegacyTestRunConfiguration<T>>
  extends AbstractPythonTestConfigurationProducer<AbstractPythonLegacyTestRunConfiguration<T>> {

  protected PythonTestLegacyConfigurationProducer() {
    // ExtensionNotApplicableException cannot be thrown here because PythonDocTestConfigurationProducer is applicable regardless of mode
  }

  @Override
  public @NotNull Class<? super AbstractPythonLegacyTestRunConfiguration<T>> getConfigurationClass() {
    return AbstractPythonLegacyTestRunConfiguration.class;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull AbstractPythonLegacyTestRunConfiguration configuration,
                                            @NotNull ConfigurationContext context) {
    final Location location = context.getLocation();
    if (location == null || !isAvailable(location)) return false;
    final PsiElement element = location.getPsiElement();
    final PsiFileSystemItem file = element.getContainingFile();
    if (file == null) return false;
    final VirtualFile virtualFile = element instanceof PsiDirectory ? ((PsiDirectory)element).getVirtualFile()
                                                                    : file.getVirtualFile();
    if (virtualFile == null) return false;
    final PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

    final AbstractPythonLegacyTestRunConfiguration.TestType confType = configuration.getTestType();
    final String workingDirectory = configuration.getWorkingDirectory();

    if (element instanceof PsiDirectory) {
      final String path = ((PsiDirectory)element).getVirtualFile().getPath();
      return confType == AbstractPythonLegacyTestRunConfiguration.TestType.TEST_FOLDER &&
             path.equals(configuration.getFolderName()) ||
             path.equals(new File(workingDirectory, configuration.getFolderName()).getAbsolutePath());
    }

    final String scriptName = configuration.getScriptName();
    final String path = virtualFile.getPath();
    final boolean isTestFileEquals = scriptName.equals(path) || path.equals(new File(workingDirectory, scriptName).getAbsolutePath());

    if (pyFunction != null) {
      final String methodName = configuration.getMethodName();
      if (pyFunction.getContainingClass() == null) {
        return confType == AbstractPythonLegacyTestRunConfiguration.TestType.TEST_FUNCTION &&
               methodName.equals(pyFunction.getName()) && isTestFileEquals;
      }
      else {
        final String className = configuration.getClassName();

        return confType == AbstractPythonLegacyTestRunConfiguration.TestType.TEST_METHOD &&
               methodName.equals(pyFunction.getName()) &&
               pyClass != null && className.equals(pyClass.getName()) && isTestFileEquals;
      }
    }
    if (pyClass != null) {
      final String className = configuration.getClassName();
      return confType == AbstractPythonLegacyTestRunConfiguration.TestType.TEST_CLASS &&
             className.equals(pyClass.getName()) && isTestFileEquals;
    }
    return confType == AbstractPythonLegacyTestRunConfiguration.TestType.TEST_SCRIPT && isTestFileEquals;
  }


  @Override
  protected boolean setupConfigurationFromContext(@NotNull AbstractPythonLegacyTestRunConfiguration<T> configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    final Location location = context.getLocation();
    if (location == null || !isAvailable(location)) return false;
    PsiElement element = location.getPsiElement();
    if (element instanceof PsiWhiteSpace) {
      element = PyUtil.findNonWhitespaceAtOffset(element.getContainingFile(), element.getTextOffset());
    }

    if (RunnableScriptFilter.isIfNameMain(location)) return false;
    final Module module = location.getModule();
    if (module == null) return false;
    if (!isPythonModule(module)) return false;

    if (element instanceof PsiDirectory) {
      return setupConfigurationFromFolder((PsiDirectory)element, configuration);
    }

    final PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    if (pyFunction != null && isTestFunction(pyFunction, configuration)) {
      return setupConfigurationFromFunction(pyFunction, configuration);
    }
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (pyClass != null &&
        isTestClass(pyClass, configuration, TypeEvalContext.userInitiated(pyClass.getProject(), element.getContainingFile()))) {
      return setupConfigurationFromClass(pyClass, configuration);
    }
    if (element == null) return false;
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyFile && isTestFile((PyFile)file)) {
      return setupConfigurationFromFile((PyFile)file, configuration);
    }

    return false;
  }

  private boolean setupConfigurationFromFolder(final @NotNull PsiDirectory element,
                                               final @NotNull AbstractPythonLegacyTestRunConfiguration configuration) {
    final VirtualFile virtualFile = element.getVirtualFile();
    if (!isTestFolder(virtualFile, element.getProject())) return false;
    final String path = virtualFile.getPath();

    configuration.setTestType(AbstractPythonLegacyTestRunConfiguration.TestType.TEST_FOLDER);
    configuration.setFolderName(path);
    configuration.setWorkingDirectory(path);
    configuration.setGeneratedName();
    setModuleSdk(element, configuration);
    return true;
  }

  private static void setModuleSdk(final @NotNull PsiElement element,
                                   final @NotNull AbstractPythonLegacyTestRunConfiguration configuration) {
    configuration.setUseModuleSdk(true);
    configuration.setModule(ModuleUtilCore.findModuleForPsiElement(element));
  }

  protected boolean setupConfigurationFromFunction(final @NotNull PyFunction pyFunction,
                                                   final @NotNull AbstractPythonLegacyTestRunConfiguration configuration) {
    final PyClass containingClass = pyFunction.getContainingClass();
    configuration.setMethodName(pyFunction.getName());

    if (containingClass != null) {
      configuration.setClassName(containingClass.getName());
      configuration.setTestType(AbstractPythonLegacyTestRunConfiguration.TestType.TEST_METHOD);
    }
    else {
      configuration.setTestType(AbstractPythonLegacyTestRunConfiguration.TestType.TEST_FUNCTION);
    }
    return setupConfigurationScript(configuration, pyFunction);
  }

  protected boolean setupConfigurationFromClass(final @NotNull PyClass pyClass,
                                                final @NotNull AbstractPythonLegacyTestRunConfiguration configuration) {
    configuration.setTestType(AbstractPythonLegacyTestRunConfiguration.TestType.TEST_CLASS);
    configuration.setClassName(pyClass.getName());
    return setupConfigurationScript(configuration, pyClass);
  }

  protected boolean setupConfigurationFromFile(final @NotNull PyFile pyFile,
                                               final @NotNull AbstractPythonLegacyTestRunConfiguration configuration) {
    configuration.setTestType(AbstractPythonLegacyTestRunConfiguration.TestType.TEST_SCRIPT);
    return setupConfigurationScript(configuration, pyFile);
  }

  protected static boolean setupConfigurationScript(final @NotNull AbstractPythonLegacyTestRunConfiguration cfg,
                                                    final @NotNull PyElement element) {
    final PyFile containingFile = PyUtil.getContainingPyFile(element);
    if (containingFile == null) return false;
    final VirtualFile vFile = containingFile.getVirtualFile();
    if (vFile == null) return false;
    final VirtualFile parent = vFile.getParent();
    if (parent == null) return false;

    cfg.setScriptName(vFile.getPath());

    if (StringUtil.isEmptyOrSpaces(cfg.getWorkingDirectory())) {
      cfg.setWorkingDirectory(parent.getPath());
    }
    cfg.setGeneratedName();
    setModuleSdk(element, cfg);
    return true;
  }

  protected boolean isTestFolder(final @NotNull VirtualFile virtualFile, final @NotNull Project project) {
    final @NonNls String name = virtualFile.getName();
    final HashSet<VirtualFile> roots = new HashSet<>();
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      roots.addAll(PyUtil.getSourceRoots(module));
    }
    Collections.addAll(roots, ProjectRootManager.getInstance(project).getContentRoots());
    return StringUtil.toLowerCase(name).contains("test") || roots.contains(virtualFile);
  }

  protected boolean isAvailable(final @NotNull Location location) {
    return false;
  }

  protected boolean isTestClass(final @NotNull PyClass pyClass,
                                final @Nullable AbstractPythonLegacyTestRunConfiguration configuration,
                                final @Nullable TypeEvalContext context) {
    return PythonUnitTestDetectorsBasedOnSettings.isTestClass(pyClass, ThreeState.UNSURE, context);
  }

  protected boolean isTestFunction(final @NotNull PyFunction pyFunction,
                                   final @Nullable AbstractPythonLegacyTestRunConfiguration configuration) {
    return PythonUnitTestDetectorsBasedOnSettings.isTestFunction(pyFunction, ThreeState.UNSURE, null);
  }

  protected boolean isTestFile(final @NotNull PyFile file) {
    final List<PyStatement> testCases = getTestCaseClassesFromFile(file);
    if (testCases.isEmpty()) return false;
    return true;
  }

  protected static boolean isPythonModule(@NotNull Module module) {
    return PyModuleService.getInstance().isPythonModule(module);
  }

  protected List<PyStatement> getTestCaseClassesFromFile(final @NotNull PyFile pyFile) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(pyFile.getProject(), pyFile);
    return pyFile.getTopLevelClasses().stream()
      .filter(o -> PythonUnitTestDetectorsBasedOnSettings.isTestClass(o, ThreeState.UNSURE, context))
      .collect(Collectors.toList());
  }
}
