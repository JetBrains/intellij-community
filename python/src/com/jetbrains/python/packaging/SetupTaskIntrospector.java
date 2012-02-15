package com.jetbrains.python.packaging;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class SetupTaskIntrospector {
  private static class SetupTask {
    private final String myName;
    private final String myDescription;

    private SetupTask(String name, String description) {
      myName = name;
      myDescription = description;
    }
  }

  private static final Map<String, List<SetupTask>> ourTaskCache = new HashMap<String, List<SetupTask>>();

  public static List<AnAction> createSetupTaskActions(Module module, PyFile setupPyFile) {
    List<AnAction> result = new ArrayList<AnAction>();
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
        for (SetupTask task : tasks) {
          result.add(new RunSetupTaskAction(task.myName, task.myDescription));
        }

      }
    }
    return result;
  }

  private static List<SetupTask> collectTasks(PsiDirectory dir) {
    List<SetupTask> result = new ArrayList<SetupTask>();
    for (PsiFile commandFile : dir.getFiles()) {
      if (commandFile instanceof PyFile && !commandFile.getName().equals(PyNames.INIT_DOT_PY)) {
        final String taskName = FileUtil.getNameWithoutExtension(commandFile.getName());
        result.add(new SetupTask(taskName, getTaskDescription((PyFile) commandFile, taskName)));
      }
    }
    return result;
  }

  private static String getTaskDescription(PyFile file, String name) {
    final PyClass taskClass = file.findTopLevelClass(name);
    if (taskClass != null) {
      final PyTargetExpression description = taskClass.findClassAttribute("description", false);
      if (description != null) {
        final String descriptionText = PyUtil.strValue(PyUtil.flattenParens(description.findAssignedValue()));
        if (descriptionText != null) {
          return StringUtil.capitalize(descriptionText);
        }
      }
    }
    return name;
  }
}
