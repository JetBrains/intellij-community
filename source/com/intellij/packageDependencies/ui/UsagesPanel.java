package com.intellij.packageDependencies.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.FindDependencyUtil;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class UsagesPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packageDependencies.ui.UsagesPanel");

  private Project myProject;

  private ProgressIndicator myCurrentProgress;
  private JComponent myCurrentComponent;
  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public UsagesPanel(Project project) {
    super(new BorderLayout());
    myProject = project;
    setToInitialPosition();
  }

  public void setToInitialPosition() {
    cancelCurrentFindRequest();
    setToComponent(createLabel("Select where to search in left tree and what to search in right tree."));
  }

  public void findUsages(final DependenciesBuilder builder, final Set<PsiFile> searchIn, final Set<PsiFile> searchFor) {
    cancelCurrentFindRequest();

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        new Thread(new Runnable() {
          public void run() {
            final ProgressIndicator progress = new MyProgressIndicator();
            myCurrentProgress = progress;
            ProgressManager.getInstance().runProcess(new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                  public void run() {
                    UsageInfo[] usages = new UsageInfo[0];
                    try {
                      usages = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
                    }
                    catch (ProcessCanceledException e) {
                    }
                    catch (Exception e) {
                      LOG.error(e);
                    }

                    if (!progress.isCanceled()) {
                      final UsageInfo[] finalUsages = usages;
                      ApplicationManager.getApplication().invokeLater(new Runnable() {
                        public void run() {
                          showUsages(finalUsages);
                        }
                      }, ModalityState.stateForComponent(UsagesPanel.this));
                    }
                  }
                });
                myCurrentProgress = null;
              }
            }, progress);
          }
        }).start();
      }
    }, 300);
  }

  private void cancelCurrentFindRequest() {
    if (myCurrentProgress != null) {
      myCurrentProgress.cancel();
    }
  }

  private void showUsages(final UsageInfo[] usageInfos) {
    try {
      Usage[] usages = UsageInfo2UsageAdapter.convert(usageInfos);
      UsageViewPresentation presentation = new UsageViewPresentation();
      presentation.setCodeUsagesString("Usages of the right tree scope selection in the left tree scope selection");
      UsageView usageView = myProject.getComponent(UsageViewManager.class).createUsageView(new UsageTarget[0],
                                                                                           usages, presentation);
      setToComponent(usageView.getComponent());
    }
    catch (ProcessCanceledException e) {
      setToCanceled();
    }
  }

  private void setToCanceled() {
    setToComponent(createLabel("Canceled"));
  }

  private void setToComponent(final JComponent cmp) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myCurrentComponent != null) {
          remove(myCurrentComponent);
        }
        myCurrentComponent = cmp;
        add(cmp, BorderLayout.CENTER);
        revalidate();
      }
    });
  }

  private JComponent createLabel(String text) {
    JLabel label = new JLabel(text);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    return label;
  }

  private class MyProgressIndicator extends ProgressIndicatorBase {
    private MyProgressPanel myProgressPanel;
    private boolean myPaintInQueue;

    public MyProgressIndicator() {
      myProgressPanel = new MyProgressPanel();
    }

    public void start() {
      super.start();
      setToComponent(myProgressPanel.myPanel);
    }

    public void stop() {
      super.stop();
      if (isCanceled()) {
        setToCanceled();
      }
    }

    public void setText(String text) {
      if (!text.equals(getText())) {
        super.setText(text);
        update();
      }
    }

    public void setFraction(double fraction) {
      if (fraction != getFraction()) {
        super.setFraction(fraction);
        update();
      }
    }

    private void update() {
      if (myPaintInQueue) return;
      myPaintInQueue = true;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myPaintInQueue = false;
          myProgressPanel.myTextLabel.setText(getText());
          double fraction = getFraction();
          myProgressPanel.myFractionLabel.setText((int)(fraction * 99 + 0.5) + "%");
          myProgressPanel.myFractionProgress.setFraction(fraction);
        }
      });
    }
  }

  private static class MyProgressPanel {
    public JLabel myFractionLabel;
    public ColorProgressBar myFractionProgress;
    public JLabel myTextLabel;
    public JPanel myPanel;
  }
}