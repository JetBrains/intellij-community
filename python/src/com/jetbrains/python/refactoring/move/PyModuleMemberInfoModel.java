package com.jetbrains.python.refactoring.move;

import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
class PyModuleMemberInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyFile, PyModuleMemberInfo> {
  final PyFile myPyFile;

  public PyModuleMemberInfoModel(@NotNull PyFile file) {
    super(file, null, false);
    myPyFile = file;
  }

  @Override
  public boolean isAbstractEnabled(PyModuleMemberInfo member) {
    return false;
  }

  @Override
  protected int doCheck(@NotNull PyModuleMemberInfo memberInfo, int problem) {
    return problem == ERROR ? WARNING : problem;
  }

  @NotNull
  public List<PyElement> collectTopLevelSymbols() {
    final List<PyElement> result = new ArrayList<PyElement>();
    result.addAll(myPyFile.getTopLevelAttributes());
    result.addAll(myPyFile.getTopLevelClasses());
    result.addAll(myPyFile.getTopLevelFunctions());
    return result;
  }

  @NotNull
  public List<PyModuleMemberInfo> collectTopLevelSymbolsInfo() {
    return ContainerUtil.mapNotNull(collectTopLevelSymbols(), new Function<PyElement, PyModuleMemberInfo>() {
      @Override
      public PyModuleMemberInfo fun(PyElement element) {
        return new PyModuleMemberInfo(element);
      }
    });
  }
}
