// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import java.io.File;
import java.util.Objects;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parent of all python test old-style test runners.
 * For new style see {@link com.jetbrains.python.testing}
 * User: catherine
 */
public abstract class AbstractPythonLegacyTestRunConfiguration<T extends AbstractPythonTestRunConfiguration<T>>
  extends AbstractPythonTestRunConfiguration<T>
  implements AbstractPythonRunConfigurationParams,
             AbstractPythonTestRunConfigurationParams,
             RefactoringListenerProvider {
  protected @NlsSafe String myClassName = "";
  protected @NlsSafe String myScriptName = "";
  protected @NlsSafe String myMethodName = "";
  protected @NlsSafe String myFolderName = "";
  protected TestType myTestType = TestType.TEST_SCRIPT;

  private @NlsSafe String myPattern = ""; // pattern for modules in folder to match against
  private boolean usePattern = false;

  protected AbstractPythonLegacyTestRunConfiguration(Project project, ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @NotNull
  @Override
  public String getWorkingDirectorySafe() {
    final String workingDirectoryFromConfig = getWorkingDirectory();
    if (StringUtil.isNotEmpty(workingDirectoryFromConfig)) {
      return workingDirectoryFromConfig;
    }

    final String folderName = myFolderName;
    if (!StringUtil.isEmptyOrSpaces(folderName)) {
      return folderName;
    }
    final String scriptName = myScriptName;
    if (!StringUtil.isEmptyOrSpaces(scriptName)) {
      final VirtualFile script = LocalFileSystem.getInstance().findFileByPath(scriptName);
      if (script != null) {
        return script.getParent().getPath();
      }
    }
    return super.getWorkingDirectorySafe();
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    myScriptName = JDOMExternalizerUtil.readField(element, "SCRIPT_NAME");
    myClassName = JDOMExternalizerUtil.readField(element, "CLASS_NAME");
    myMethodName = JDOMExternalizerUtil.readField(element, "METHOD_NAME");
    myFolderName = JDOMExternalizerUtil.readField(element, "FOLDER_NAME");

    myPattern = JDOMExternalizerUtil.readField(element, "PATTERN");
    usePattern = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "USE_PATTERN"));

    try {
      final String testType = JDOMExternalizerUtil.readField(element, "TEST_TYPE");
      myTestType = testType != null ? TestType.valueOf(testType) : TestType.TEST_SCRIPT;
    }
    catch (IllegalArgumentException e) {
      myTestType = TestType.TEST_SCRIPT; // safe default
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);

    JDOMExternalizerUtil.writeField(element, "SCRIPT_NAME", myScriptName);
    JDOMExternalizerUtil.writeField(element, "CLASS_NAME", myClassName);
    JDOMExternalizerUtil.writeField(element, "METHOD_NAME", myMethodName);
    JDOMExternalizerUtil.writeField(element, "FOLDER_NAME", myFolderName);
    JDOMExternalizerUtil.writeField(element, "TEST_TYPE", myTestType.toString());
    JDOMExternalizerUtil.writeField(element, "PATTERN", myPattern);
    JDOMExternalizerUtil.writeField(element, "USE_PATTERN", String.valueOf(usePattern));
  }

  @Override
  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  @Override
  public String getClassName() {
    return myClassName;
  }

  @Override
  public void setClassName(String className) {
    myClassName = className;
  }

  @Override
  public String getFolderName() {
    return myFolderName;
  }

  @Override
  public void setFolderName(String folderName) {
    myFolderName = folderName;
  }

  @Override
  public String getScriptName() {
    return myScriptName;
  }

  @Override
  public void setScriptName(@NotNull String scriptName) {
    myScriptName = scriptName;
  }

  @Override
  public String getMethodName() {
    return myMethodName;
  }

  @Override
  public void setMethodName(String methodName) {
    myMethodName = methodName;
  }

  @Override
  public TestType getTestType() {
    return myTestType;
  }

  @Override
  public void setTestType(TestType testType) {
    myTestType = testType;
  }

  @Override
  public String getPattern() {
    return myPattern;
  }

  @Override
  public void setPattern(String pattern) {
    myPattern = pattern;
  }

  @Override
  public boolean usePattern() {
    return usePattern;
  }

  @Override
  public void usePattern(boolean usePattern) {
    this.usePattern = usePattern;
  }

  public enum TestType {
    TEST_FOLDER,
    TEST_SCRIPT,
    TEST_CLASS,
    TEST_METHOD,
    TEST_FUNCTION,
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(myFolderName) && myTestType == TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_folder_name"));
    }

    if (StringUtil.isEmptyOrSpaces(getScriptName()) && myTestType != TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_script_name"));
    }

    if (StringUtil.isEmptyOrSpaces(myClassName) && (myTestType == TestType.TEST_METHOD || myTestType == TestType.TEST_CLASS)) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_class_name"));
    }

    if (StringUtil.isEmptyOrSpaces(myMethodName) && (myTestType == TestType.TEST_METHOD || myTestType == TestType.TEST_FUNCTION)) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_method_name"));
    }
  }

  public boolean compareSettings(AbstractPythonLegacyTestRunConfiguration cfg) {
    if (cfg == null) return false;

    if (getTestType() != cfg.getTestType()) return false;

    switch (getTestType()) {
      case TEST_FOLDER:
        return getFolderName().equals(cfg.getFolderName());
      case TEST_SCRIPT:
        return getScriptName().equals(cfg.getScriptName()) &&
               getWorkingDirectory().equals(cfg.getWorkingDirectory());
      case TEST_CLASS:
        return getScriptName().equals(cfg.getScriptName()) &&
               getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
               getClassName().equals(cfg.getClassName());
      case TEST_METHOD:
        return getScriptName().equals(cfg.getScriptName()) &&
               getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
               getClassName().equals(cfg.getClassName()) &&
               getMethodName().equals(cfg.getMethodName());
      case TEST_FUNCTION:
        return getScriptName().equals(cfg.getScriptName()) &&
               getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
               getMethodName().equals(cfg.getMethodName());
      default:
        throw new IllegalStateException("Unknown test type: " + getTestType());
    }
  }

  public static void copyParams(AbstractPythonTestRunConfigurationParams source, AbstractPythonTestRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setClassName(source.getClassName());
    target.setFolderName(source.getFolderName());
    target.setMethodName(source.getMethodName());
    target.setTestType(source.getTestType());
    target.setPattern(source.getPattern());
    target.usePattern(source.usePattern());
    target.setAddContentRoots(source.shouldAddContentRoots());
    target.setAddSourceRoots(source.shouldAddSourceRoots());
  }

  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return this;
  }

  @Override
  public String suggestedName() {
    switch (myTestType) {
      case TEST_CLASS:
        return PyBundle.message("runcfg.unittest.suggest.name.in.class", getPluralTitle(), myClassName);
      case TEST_METHOD:
        return getTitle() + " " + myClassName + "." + myMethodName;
      case TEST_SCRIPT:
        String name = new File(getScriptName()).getName();
        name = StringUtil.trimEnd(name, ".py");
        return PyBundle.message("runcfg.unittest.suggest.name.in.script", getPluralTitle(), name);
      case TEST_FOLDER:
        String folderName = new File(myFolderName).getName();
        return PyBundle.message("runcfg.unittest.suggest.name.in.folder", getPluralTitle(), folderName);
      case TEST_FUNCTION:
        return getTitle() + " " + myMethodName;
      default:
        throw new IllegalStateException("Unknown test type: " + myTestType);
    }
  }

  @Nullable
  @Override
  public String getActionName() {
    if (TestType.TEST_METHOD.equals(myTestType)) {
      return getTitle() + " " + myMethodName;
    }
    return suggestedName();
  }

  protected abstract @NlsActions.ActionText String getTitle();

  protected abstract @NlsActions.ActionText String getPluralTitle();

  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiDirectory) {
      VirtualFile vFile = ((PsiDirectory)element).getVirtualFile();
      if ((myTestType == TestType.TEST_FOLDER && pathsEqual(vFile, myFolderName)) || pathsEqual(vFile, getWorkingDirectory())) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
            String newPath = FileUtil.toSystemDependentName(((PsiDirectory)newElement).getVirtualFile().getPath());
            setWorkingDirectory(newPath);
            if (myTestType == TestType.TEST_FOLDER) {
              myFolderName = newPath;
            }
          }

          @Override
          public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
            final String systemDependant = FileUtil.toSystemDependentName(oldQualifiedName);
            setWorkingDirectory(systemDependant);
            if (myTestType == TestType.TEST_FOLDER) {
              myFolderName = systemDependant;
            }
          }
        };
      }
      return null;
    }
    if (myTestType == TestType.TEST_FOLDER) {
      return null;
    }
    File scriptFile = new File(myScriptName);
    if (!scriptFile.isAbsolute()) {
      scriptFile = new File(getWorkingDirectory(), myScriptName);
    }
    PsiFile containingFile = element.getContainingFile();
    VirtualFile vFile = containingFile == null ? null : containingFile.getVirtualFile();
    if (vFile != null && Objects.equals(new File(vFile.getPath()).getAbsolutePath(), scriptFile.getAbsolutePath())) {
      if (element instanceof PsiFile) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
            VirtualFile virtualFile = ((PsiFile)newElement).getVirtualFile();
            if (virtualFile != null) {
              myScriptName = FileUtil.toSystemDependentName(virtualFile.getPath());
            }
          }

          @Override
          public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
            myScriptName = FileUtil.toSystemDependentName(oldQualifiedName);
          }
        };
      }
      if (element instanceof PyClass && (myTestType == TestType.TEST_CLASS || myTestType == TestType.TEST_METHOD) &&
          Objects.equals(((PyClass)element).getName(), myClassName)) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
            myClassName = ((PyClass)newElement).getName();
          }

          @Override
          public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
            myClassName = oldQualifiedName;
          }
        };
      }
      if (element instanceof PyFunction &&
          Objects.equals(((PyFunction)element).getName(), myMethodName)) {
        ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(element, ScopeOwner.class);
        if ((myTestType == TestType.TEST_FUNCTION && scopeOwner instanceof PyFile) ||
            (myTestType == TestType.TEST_METHOD && scopeOwner instanceof PyClass && Objects.equals(scopeOwner.getName(), myClassName))) {
          return new RefactoringElementAdapter() {
            @Override
            protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
              myMethodName = ((PyFunction)newElement).getName();
            }

            @Override
            public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
              final int methodIdx = oldQualifiedName.indexOf("#") + 1;
              if (methodIdx > 0 && methodIdx < oldQualifiedName.length()) {
                myMethodName = oldQualifiedName.substring(methodIdx);
              }
            }
          };
        }
      }
    }
    return null;
  }

  private static boolean pathsEqual(VirtualFile vFile, final String folderName) {
    return Objects.equals(new File(vFile.getPath()).getAbsolutePath(), new File(folderName).getAbsolutePath());
  }
}
