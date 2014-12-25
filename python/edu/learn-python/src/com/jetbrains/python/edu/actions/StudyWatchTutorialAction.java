package com.jetbrains.python.edu.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class StudyWatchTutorialAction extends DumbAwareAction {
  public StudyWatchTutorialAction() {
    super("Learn more about PyCharm Educational Edition", null, null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    BrowserUtil.browse("https://www.jetbrains.com/pycharm-educational/quickstart/");
  }
}
