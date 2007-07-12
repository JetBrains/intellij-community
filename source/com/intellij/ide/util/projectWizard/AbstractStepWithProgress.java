/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.SwingWorker;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public abstract class AbstractStepWithProgress<Result> extends ModuleWizardStep {
  
  @NonNls private static final String PROGRESS_PANEL = "progress_panel";
  @NonNls private static final String RESULTS_PANEL = "results_panel";
  private JPanel myPanel;
  
  private JLabel myProgressLabel;
  private JLabel myProgressLabel2;
  private ProgressIndicator myProgressIndicator = null;
  private String myPromptStopSearch;

  public AbstractStepWithProgress(final String promptStopSearching) {
    myPromptStopSearch = promptStopSearching;
  }

  public final JComponent getComponent() {
    if (myPanel == null) {
      myPanel = new JPanel(new CardLayout());
      myPanel.setBorder(BorderFactory.createEtchedBorder());

      myPanel.add(createProgressPanel(), PROGRESS_PANEL);
      myPanel.add(createResultsPanel(), RESULTS_PANEL);
    }
    return myPanel;
  }

  protected abstract JComponent createResultsPanel();
  
  protected abstract String getProgressText();
  
  protected abstract boolean shouldRunProgress();
  
  protected abstract Result calculate();

  protected abstract void onFinished(Result result, boolean canceled);

  private JPanel createProgressPanel() {
    final JPanel progressPanel = new JPanel(new GridBagLayout());
    myProgressLabel = new JLabel();
    //myProgressLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
    progressPanel.add(myProgressLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 0, 10), 0, 0));

    myProgressLabel2 = new JLabel();
    //myProgressLabel2.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
    progressPanel.add(myProgressLabel2, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 0, 10), 0, 0));

    JButton stopButton = new JButton(IdeBundle.message("button.stop.searching"));
    stopButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cancelSearch();
      }
    });
    progressPanel.add(stopButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 2, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 10), 0, 0));
    return progressPanel;
  }

  private void cancelSearch() {
    if (myProgressIndicator != null) {
      myProgressIndicator.cancel();
    }
  }

  private synchronized boolean isProgressRunning() {
    return myProgressIndicator != null && myProgressIndicator.isRunning();
  }
  
  
  public void updateStep() {
    if (shouldRunProgress()) {
      runProgress();
    }
    else {
      showCard(RESULTS_PANEL);
    }
  }

  protected void runProgress() {
    final MyProgressIndicator progress = new MyProgressIndicator();
    progress.setText(getProgressText());
    showCard(PROGRESS_PANEL);
    myProgressIndicator = progress;
    new SwingWorker() {
      public Object construct() {
        final Ref<Result> result = Ref.create(null);
        ProgressManager.getInstance().runProcess(new Runnable() {
          public void run() {
            result.set(calculate());
          }
        }, progress);
        return result.get();
      }

      public void finished() {
        myProgressIndicator = null;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final Result result = (Result)get();
            onFinished(result, progress.isCanceled());
            showCard(RESULTS_PANEL);
          }
        });
      }
    }.start();
  }

  private void showCard(final String id) {
    ((CardLayout)myPanel.getLayout()).show(myPanel, id);
    myPanel.revalidate();
  }

  public boolean validate() {
    if (isProgressRunning()) {
      final int answer = Messages.showDialog(getComponent(), myPromptStopSearch,
                                             IdeBundle.message("title.question"), new String[] {IdeBundle.message("action.continue.searching"), IdeBundle.message("action.stop.searching")}, 0, Messages.getWarningIcon());
      if (answer == 1) { // terminate
        cancelSearch();
      }
      return false;
    }
    return true;
  }

  public void onStepLeaving() {
    if (isProgressRunning()) {
      cancelSearch();
    }
  }

  protected class MyProgressIndicator extends ProgressIndicatorBase {
    public void setText(String text) {
      super.setText(text);
      myProgressLabel.setText(text);
    }

    public void setText2(String text) {
      super.setText2(text);
      myProgressLabel2.setText(text);
    }
  }
}
