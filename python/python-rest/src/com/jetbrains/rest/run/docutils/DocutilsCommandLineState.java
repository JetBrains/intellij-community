package com.jetbrains.rest.run.docutils;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.rest.run.RestCommandLineState;
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
    return new Runnable() {
      @Override
      public void run() {
        VirtualFile virtualFile = findOutput();
        if (virtualFile != null) {
          if (myConfiguration.openInBrowser()) {
            BrowserUtil.browse(virtualFile);
          }
          else {
            FileEditorManager.getInstance(myConfiguration.getProject()).openFile(virtualFile, true);
          }
        }
      }
    };
  }

  @Override
  protected String getRunnerPath() {
    return "rest_runners/rst2smth.py";
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
