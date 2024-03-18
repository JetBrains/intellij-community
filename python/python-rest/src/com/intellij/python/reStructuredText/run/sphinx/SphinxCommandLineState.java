// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run.sphinx;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.reStructuredText.run.RestCommandLineState;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelper;

import java.util.Collections;

/**
 * User : catherine
 */
public class SphinxCommandLineState extends RestCommandLineState {

  public SphinxCommandLineState(SphinxRunConfiguration configuration,
                                ExecutionEnvironment env) {
    super(configuration, env);
  }

  @Override
  protected Runnable getAfterTask() {
    return () -> {
      VirtualFile virtualFile = findOutput();
      if (virtualFile != null)
        LocalFileSystem.getInstance().refreshFiles(Collections.singleton(virtualFile), false, true, null);
    };
  }

  @Override
  protected HelperPackage getRunner() {
    return PythonHelper.SPHINX_RUNNER;
  }

  @Override
  protected String getKey() {
    return "-b";
  }

  @Override
  protected String getTask() {
    return myConfiguration.getTask().trim();
  }
}
