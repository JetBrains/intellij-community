package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: Make it generic to allow to reuse in another projects?
 *
 * @author Ilya.Kazakevich
 */
class PyUsageInfo extends UsageInfo {
  @NotNull
  private final PyMemberInfo myPyMemberInfo;

  PyUsageInfo(@NotNull final PyMemberInfo pyMemberInfo) {
    super(pyMemberInfo.getMember(), true);
    myPyMemberInfo = pyMemberInfo;
  }

  @NotNull
  PyMemberInfo getPyMemberInfo() {
    return myPyMemberInfo;
  }
}
