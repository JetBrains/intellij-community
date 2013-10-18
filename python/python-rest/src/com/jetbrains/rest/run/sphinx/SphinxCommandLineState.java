package com.jetbrains.rest.run.sphinx;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.rest.run.RestCommandLineState;

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
    return new Runnable() {
      @Override
      public void run() {
        VirtualFile virtualFile = findOutput();
        if (virtualFile != null)
          LocalFileSystem.getInstance().refreshFiles(Collections.singleton(virtualFile), false, true, null);
      }
    };
  }

  @Override
  protected String getRunnerPath() {
    return "rest_runners/sphinx_runner.py";
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
