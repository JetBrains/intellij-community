package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * View configuration for "extract superclass"
 *
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassInitializationInfo extends MembersViewInitializationInfo {

  @NotNull
  private final String myDefaultFilePath;
  @NotNull
  private final VirtualFile[] myRoots;

  /**
   * @param defaultFilePath module file path to display. User will be able to change it later.
   * @param roots           virtual files where user may add new module
   */
  PyExtractSuperclassInitializationInfo(@NotNull final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                                        @NotNull final Collection<PyMemberInfo<PyElement>> memberInfos,
                                        @NotNull final String defaultFilePath,
                                        @NotNull final VirtualFile... roots) {
    super(memberInfoModel, memberInfos);
    myDefaultFilePath = defaultFilePath;
    myRoots = roots.clone();
  }

  @NotNull
  public String getDefaultFilePath() {
    return myDefaultFilePath;
  }

  @NotNull
  public VirtualFile[] getRoots() {
    return myRoots.clone();
  }
}
