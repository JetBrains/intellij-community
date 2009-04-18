package com.jetbrains.python.testing;

import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Collection;
import java.io.File;

public class PythonUnitTestTestIdUrlProvider implements TestLocationProvider {
  @NonNls
  private static final String PROTOCOL_ID = "python_uttestid";

  @NotNull
  public List<Location> getLocation(@NotNull final String protocolId, @NotNull final String path,
                                    final Project project) {
    if (!PROTOCOL_ID.equals(protocolId)) {
      return Collections.emptyList();
    }

    final List<String> list = StringUtil.split(path, ".");
    if (list.isEmpty()) {
      return Collections.emptyList();
    }
    final int listSize = list.size();

    // parse path as ns.[ns.]*className.methodName
    if (listSize < 3) {
      return Collections.emptyList();
    }

    final String className = list.get(listSize - 2);
    final String methodName = list.get(listSize - 1);
    final String fileName = list.get(listSize - 3);

    final List<Location> locations = new ArrayList<Location>();
    for (PyClass cls : getClassesByName(project, className)) {
      ProgressManager.getInstance().checkCanceled();

      final PyFunction method = cls.findMethodByName(methodName);
      if (method == null) {
        continue;
      }

      final String clsFileName = FileUtil.getNameWithoutExtension(cls.getContainingFile().getName());
      if (!clsFileName.equalsIgnoreCase(fileName)) {
        continue;
      }

      locations.add(new PsiLocation<PyFunction>(project, method));
    }

    return locations;
  }

  private static Collection<PyClass> getClassesByName(final Project project, final String name) {
    final GlobalSearchScope scope = ProjectScope.getProjectScope(project);
    return StubIndex.getInstance().get(PyClassNameIndex.KEY, name, project, scope);
  }
}
