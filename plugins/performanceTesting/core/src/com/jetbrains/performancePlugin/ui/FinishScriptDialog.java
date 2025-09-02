// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.PlatformUtils;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.profilers.Profiler;
import com.jetbrains.performancePlugin.profilers.ProfilersController;
import com.jetbrains.performancePlugin.utils.StatisticCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FinishScriptDialog extends DialogWrapper {

  private JPanel myMainPanel;
  private JTextField snapshotLabel;
  private JTextArea metricsPanel;
  private File snapshot = null;

  public FinishScriptDialog(@Nullable Project project) {
    super(project);
    if (ProfilersController.getInstance().isStoppedByScript()) {
      String path = ProfilersController.getInstance().getReportsPath();
      try {
        snapshot =
          ProfilersController.getInstance().getCurrentProfilerHandler()
            .compressResults(path, Profiler.formatSnapshotName(false));
        ProfilersController.getInstance().setReportsPath(snapshot.getParentFile().getAbsolutePath());
        snapshotLabel.setText(PerformanceTestingBundle.message("finish.path", snapshot.getAbsolutePath()));
        setTitle(PerformanceTestingBundle.message("finish.title"));
        snapshotLabel.setEditable(false);
        snapshotLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
      }
      catch (Exception ex) {
        throw new RuntimeException(ex.getMessage());
      }
    }
    String metrics = new StatisticCollector(project).collectMetrics(true);
    metricsPanel.setText(metrics);
    metricsPanel.setLineWrap(true);
    init();
    pack();
  }


  @Override
  protected Action @NotNull [] createActions() {
    List<Action> dialogActions = new ArrayList<>(3);
    dialogActions.add(new DialogWrapperAction(PerformanceTestingBundle.message("summary.action")) {
      @Override
      protected void doAction(ActionEvent e) {
        if(!PlatformUtils.isRider()){
          BrowserUtil.browse(PerformanceTestingBundle.message("summary.link"));
        } else{
          BrowserUtil.browse(PerformanceTestingBundle.message("summary.link.rider"));
        }
      }
    });
    if (snapshot != null) {
      dialogActions.add(new DialogWrapperAction(PerformanceTestingBundle.message("finish.open", RevealFileAction.getFileManagerName())) {
        @Override
        protected void doAction(ActionEvent e) {
          RevealFileAction.openDirectory(snapshot.getParentFile());
        }
      });
    }
    dialogActions.add(getOKAction());
    return dialogActions.toArray(new Action[0]);
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myMainPanel;
  }
}
