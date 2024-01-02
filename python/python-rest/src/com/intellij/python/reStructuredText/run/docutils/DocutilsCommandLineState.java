// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run.docutils;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.reStructuredText.run.RestCommandLineState;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class DocutilsCommandLineState extends RestCommandLineState {

  public DocutilsCommandLineState(DocutilsRunConfiguration configuration,
                                  ExecutionEnvironment env) {
    super(configuration, env);
  }

  @Override
  protected Runnable getAfterTask() {
    return () -> {
      VirtualFile virtualFile = findOutput();
      if (virtualFile != null) {
        if (myConfiguration.openInBrowser()) {
          BrowserUtil.browse(virtualFile);
        }
        else {
          FileEditorManager.getInstance(myConfiguration.getProject()).openFile(virtualFile, true);
        }
      }
    };
  }

  @Override
  protected HelperPackage getRunner() {
    return PythonHelper.REST_RUNNER;
  }

  @Override
  protected String getTask() {
    return myConfiguration.getTask();
  }

  @Override
  @Nullable
  protected String getKey() {
    return null;
  }
}
