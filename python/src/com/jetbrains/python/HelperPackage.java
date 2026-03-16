// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public interface HelperPackage {
  void addToPythonPath(@NotNull Map<String, String> environment);

  /**
   * @return entry (directory or ZIP archive) that will be added to <tt>PYTHONPATH</tt> environment variable before the process is started.
   */
  @NotNull
  String getPythonPathEntry();

  /**
   * @return entries (directory or ZIP archive) that should be added to
   * <tt>PYTHONPATH</tt> environment variable before the process is started.
   */
  @NotNull
  List<String> getPythonPathEntries();

  void addToGroup(@NotNull ParamsGroup group, @NotNull GeneralCommandLine cmd);

  /**
   * @return the first parameter passed to Python interpreter that indicates which script to run. For scripts started as modules it's
   * module name with <tt>-m</tt> flag, like <tt>-mpackage.module.name</tt>, and for average helpers it's full path to the script.
   */
  @NotNull
  String asParamString();

  @NotNull
  GeneralCommandLine newCommandLine(@NotNull String sdkPath, @NotNull List<String> parameters);
}
