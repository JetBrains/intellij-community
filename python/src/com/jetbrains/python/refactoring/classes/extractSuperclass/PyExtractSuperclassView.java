package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedView;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
public interface PyExtractSuperclassView extends MembersBasedView<PyExtractSuperclassInitializationInfo> {

  /**
   *
   * @return path to destination file (module) where user wants to create new class
   */
  @NotNull
  String getModuleFile();

  /**
   *
   * @return name user wants to give to new class
   */
  @NotNull
  String getSuperClassName();

}
