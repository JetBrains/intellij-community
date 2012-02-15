package com.jetbrains.python.packaging.setupPy;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class SetupTaskIntrospector {
  public static class SetupTaskOption {
    public final String name;
    public final String description;
    public final boolean checkbox;
    public final boolean negative;

    private SetupTaskOption(String name, String description, boolean checkbox, boolean negative) {
      this.name = name;
      this.description = description;
      this.checkbox = checkbox;
      this.negative = negative;
    }
  }

  private static class SetupTask {
    private final String name;
    private String description;
    private final List<SetupTaskOption> options = new ArrayList<SetupTaskOption>();

    private SetupTask(String name) {
      this.name = name;
      description = name;
    }
  }

  private static final Map<String, List<SetupTask>> ourTaskCache = new HashMap<String, List<SetupTask>>();

  public static List<AnAction> createSetupTaskActions(Module module, PyFile setupPyFile) {
    List<AnAction> result = new ArrayList<AnAction>();
    for (SetupTask task : getTaskList(module)) {
      result.add(new RunSetupTaskAction(task.name, task.description));
    }
    return result;
  }

  @Nullable
  public static List<SetupTaskOption> getSetupTaskOptions(Module module, String taskName) {
    for (SetupTask task : getTaskList(module)) {
      if (task.name.equals(taskName)) {
        return task.options;
      }
    }
    return null;
  }

  private static List<SetupTask> getTaskList(Module module) {
    final PyClass installClass = PyClassNameIndex.findClass("distutils.command.install.install", module.getProject());
    if (installClass != null) {
      final PsiDirectory distutilsCommandDir = installClass.getContainingFile().getParent();
      if (distutilsCommandDir != null) {
        final String path = distutilsCommandDir.getVirtualFile().getPath();
        List<SetupTask> tasks = ourTaskCache.get(path);
        if (tasks == null) {
          tasks = collectTasks(distutilsCommandDir);
          ourTaskCache.put(path, tasks);
        }
        return tasks;
      }
    }
    return Collections.emptyList();
  }

  private static List<SetupTask> collectTasks(PsiDirectory dir) {
    List<SetupTask> result = new ArrayList<SetupTask>();
    for (PsiFile commandFile : dir.getFiles()) {
      if (commandFile instanceof PyFile && !commandFile.getName().equals(PyNames.INIT_DOT_PY)) {
        final String taskName = FileUtil.getNameWithoutExtension(commandFile.getName());
        result.add(createTaskFromFile((PyFile)commandFile, taskName));
      }
    }
    return result;
  }

  private static SetupTask createTaskFromFile(PyFile file, String name) {
    SetupTask task = new SetupTask(name);
    final PyClass taskClass = file.findTopLevelClass(name);
    if (taskClass != null) {
      final PyTargetExpression description = taskClass.findClassAttribute("description", false);
      if (description != null) {
        final String descriptionText = PyUtil.strValue(PyUtil.flattenParens(description.findAssignedValue()));
        if (descriptionText != null) {
          task.description = StringUtil.capitalize(descriptionText);
        }
      }

      final PyTargetExpression booleanOptions = taskClass.findClassAttribute("boolean_options", false);
      final List<String> booleanOptionsList = booleanOptions == null
                                              ? Collections.<String>emptyList()
                                              : PyUtil.strListValue(booleanOptions.findAssignedValue());

      final PyTargetExpression negativeOpt = taskClass.findClassAttribute("negative_opt", false);
      final Map<String, String> negativeOptMap = negativeOpt == null
                                                 ? Collections.<String, String>emptyMap()
                                                 : parseNegativeOpt(negativeOpt.findAssignedValue());


      final PyTargetExpression userOptions = taskClass.findClassAttribute("user_options", false);
      if (userOptions != null) {
        final PyExpression optionsList = userOptions.findAssignedValue();
        if (optionsList instanceof PySequenceExpression) {
          final PyExpression[] elements = ((PySequenceExpression)optionsList).getElements();
          for (PyExpression element : elements) {
            final SetupTaskOption option = createOptionFromTuple(element, booleanOptionsList, negativeOptMap);
            if (option != null) {
              task.options.add(option);
            }
          }
        }
      }
    }
    return task;
  }

  private static Map<String, String> parseNegativeOpt(PyExpression dict) {
    Map<String, String> result = new HashMap<String, String>();
    dict = PyUtil.flattenParens(dict);
    if (dict instanceof PyDictLiteralExpression) {
      final PyKeyValueExpression[] elements = ((PyDictLiteralExpression)dict).getElements();
      for (PyKeyValueExpression element : elements) {
        String key = PyUtil.strValue(PyUtil.flattenParens(element.getKey()));
        String value = PyUtil.strValue(PyUtil.flattenParens(element.getValue()));
        if (key != null && value != null) {
          result.put(key, value);
        }
      }
    }
    return result;
  }

  @Nullable
  private static SetupTaskOption createOptionFromTuple(PyExpression tuple, List<String> booleanOptions, Map<String, String> negativeOptMap) {
    tuple = PyUtil.flattenParens(tuple);
    if (tuple instanceof PyTupleExpression) {
      final PyExpression[] elements = ((PyTupleExpression)tuple).getElements();
      if (elements.length == 3) {
        String name = PyUtil.strValue(elements[0]);
        final String description = PyUtil.strValue(elements[2]);
        if (name != null && description != null) {
          if (negativeOptMap.containsKey(name)) {
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
          return new SetupTaskOption(name, StringUtil.capitalize(description), checkbox, negative);
        }
      }
    }
    return null;
  }
}
