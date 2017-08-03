/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging.setupPy;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class SetupTaskIntrospector {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.packaging.setupPy.SetupTaskIntrospector");

  private static final Map<String, List<SetupTask>> ourDistutilsTaskCache = new HashMap<>();
  private static final Map<String, List<SetupTask>> ourSetuptoolsTaskCache = new HashMap<>();

  public static boolean usesSetuptools(@NotNull PyFile file) {
    final List<PyFromImportStatement> imports = file.getFromImports();
    for (PyFromImportStatement anImport : imports) {
      final QualifiedName qName = anImport.getImportSourceQName();
      if (qName != null && qName.matches("setuptools")) {
        return true;
      }
    }

    final List<PyImportElement> importElements = file.getImportTargets();
    for (PyImportElement element : importElements) {
      final QualifiedName qName = element.getImportedQName();
      if (qName != null && qName.matches("setuptools")) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static List<SetupTask.Option> getSetupTaskOptions(Module module, String taskName) {
    for (SetupTask task : getTaskList(module)) {
      if (task.getName().equals(taskName)) {
        return task.getOptions();
      }
    }
    return null;
  }

  public static List<SetupTask> getTaskList(Module module) {
    final PyFile setupPy = PyPackageUtil.findSetupPy(module);
    return getTaskList(module, setupPy != null && usesSetuptools(setupPy));
  }

  @NotNull
  private static List<SetupTask> getTaskList(@NotNull Module module, boolean setuptools) {
    final QualifiedName name = QualifiedName.fromDottedString((setuptools ? "setuptools" : "distutils") + ".command.install.install");
    final PsiElement install = PyResolveImportUtil.resolveTopLevelMember(name, PyResolveImportUtil.fromModule(module));

    if (install instanceof PyClass) {
      final PsiDirectory commandDir = install.getContainingFile().getParent();
      if (commandDir != null) {
        final Map<String, List<SetupTask>> cache = setuptools ? ourSetuptoolsTaskCache : ourDistutilsTaskCache;
        final String path = commandDir.getVirtualFile().getPath();

        List<SetupTask> tasks = cache.get(path);
        if (tasks == null) {
          tasks = collectTasks(module, commandDir, setuptools);
          cache.put(path, tasks);
        }
        return tasks;
      }
    }

    return Collections.emptyList();
  }

  private static final Set<String> SKIP_NAMES = ImmutableSet.of(PyNames.INIT_DOT_PY, "alias.py", "setopt.py", "savecfg.py");

  @NotNull
  private static List<SetupTask> collectTasks(@NotNull Module module, @NotNull PsiDirectory commandDir, boolean setuptools) {
    final List<SetupTask> result = new ArrayList<>();
    for (PsiFile commandFile : commandDir.getFiles()) {
      if (commandFile instanceof PyFile && !SKIP_NAMES.contains(commandFile.getName())) {
        final String taskName = FileUtil.getNameWithoutExtension(commandFile.getName());
        result.add(createTaskFromFile((PyFile)commandFile, taskName, setuptools));
      }
    }

    if (setuptools) {
      final QualifiedName name = QualifiedName.fromComponents("wheel", "bdist_wheel", "bdist_wheel");
      final PsiElement bdistWheel = PyResolveImportUtil.resolveTopLevelMember(name, PyResolveImportUtil.fromModule(module));

      if (bdistWheel instanceof PyClass) {
        final PsiFile file = bdistWheel.getContainingFile();
        if (file instanceof PyFile) {
          result.add(createTaskFromFile((PyFile)file, "bdist_wheel", true));
        }
      }
    }

    return result;
  }

  private static SetupTask createTaskFromFile(PyFile file, String name, boolean setuptools) {
    SetupTask task = new SetupTask(name);
    // setuptools wraps the build_ext command class in a way that we cannot understand; use the distutils class which it delegates to
    final PyClass taskClass = (name.equals("build_ext") && setuptools)
                              ? PyClassNameIndex.findClass("distutils.command.build_ext.build_ext", file.getProject())
                              : file.findTopLevelClass(name);
    if (taskClass != null) {
      final PyTargetExpression description = taskClass.findClassAttribute("description", true, null);
      if (description != null) {
        final String descriptionText = PyPsiUtils.strValue(PyPsiUtils.flattenParens(description.findAssignedValue()));
        if (descriptionText != null) {
          task.setDescription(descriptionText);
        }
      }

      final List<PyExpression> booleanOptions = resolveSequenceValue(taskClass, "boolean_options");
      final List<String> booleanOptionsList = new ArrayList<>();
      for (PyExpression option : booleanOptions) {
        final String s = PyPsiUtils.strValue(option);
        if (s != null) {
          booleanOptionsList.add(s);
        }
      }

      final PyTargetExpression negativeOpt = taskClass.findClassAttribute("negative_opt", true, null);
      final Map<String, String> negativeOptMap = negativeOpt == null
                                                 ? Collections.emptyMap()
                                                 : parseNegativeOpt(negativeOpt.findAssignedValue());


      final List<PyExpression> userOptions = resolveSequenceValue(taskClass, "user_options");
      for (PyExpression element : userOptions) {
        final SetupTask.Option option = createOptionFromTuple(element, booleanOptionsList, negativeOptMap);
        if (option != null) {
          task.addOption(option);
        }
      }
    }
    return task;
  }

  private static List<PyExpression> resolveSequenceValue(PyClass aClass, String name) {
    List<PyExpression> result = new ArrayList<>();
    collectSequenceElements(aClass.findClassAttribute(name, true, null), result);
    return result;
  }

  private static void collectSequenceElements(PsiElement value, List<PyExpression> result) {
    if (value instanceof PySequenceExpression) {
      Collections.addAll(result, ((PySequenceExpression)value).getElements());
    }
    else if (value instanceof PyBinaryExpression) {
      final PyBinaryExpression binaryExpression = (PyBinaryExpression)value;
      if (binaryExpression.isOperator("+")) {
        collectSequenceElements(binaryExpression.getLeftExpression(), result);
        collectSequenceElements(binaryExpression.getRightExpression(), result);
      }
    }
    else if (value instanceof PyReferenceExpression) {
      final PsiElement resolveResult = ((PyReferenceExpression)value).getReference(PyResolveContext.noImplicits()).resolve();
      collectSequenceElements(resolveResult, result);
    }
    else if (value instanceof PyTargetExpression) {
      collectSequenceElements(((PyTargetExpression)value).findAssignedValue(), result);
    }
  }

  private static Map<String, String> parseNegativeOpt(PyExpression dict) {
    Map<String, String> result = new HashMap<>();
    dict = PyPsiUtils.flattenParens(dict);
    if (dict instanceof PyDictLiteralExpression) {
      final PyKeyValueExpression[] elements = ((PyDictLiteralExpression)dict).getElements();
      for (PyKeyValueExpression element : elements) {
        String key = PyPsiUtils.strValue(PyPsiUtils.flattenParens(element.getKey()));
        String value = PyPsiUtils.strValue(PyPsiUtils.flattenParens(element.getValue()));
        if (key != null && value != null) {
          result.put(key, value);
        }
      }
    }
    return result;
  }

  @Nullable
  private static SetupTask.Option createOptionFromTuple(PyExpression tuple, List<String> booleanOptions, Map<String, String> negativeOptMap) {
    tuple = PyPsiUtils.flattenParens(tuple);
    if (tuple instanceof PyTupleExpression) {
      final PyExpression[] elements = ((PyTupleExpression)tuple).getElements();
      if (elements.length == 3) {
        String name = PyPsiUtils.strValue(elements[0]);
        final String description = PyPsiUtils.strValue(elements[2]);
        if (name != null && description != null) {
          if (negativeOptMap.containsKey(name)) {
            return null;
          }
          if (description.contains("don't use") || description.contains("deprecated")) {
            return null;
          }
          final boolean checkbox = booleanOptions.contains(name);
          boolean negative = false;
          if (negativeOptMap.containsValue(name)) {
            negative = true;
            for (Map.Entry<String, String> entry : negativeOptMap.entrySet()) {
              if (entry.getValue().equals(name)) {
                name = entry.getKey();
                break;
              }
            }
          }
          return new SetupTask.Option(name, StringUtil.capitalize(description), checkbox, negative);
        }
      }
    }
    return null;
  }
}
