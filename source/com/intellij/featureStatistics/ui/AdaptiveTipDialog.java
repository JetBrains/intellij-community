package com.intellij.featureStatistics.ui;

import com.intellij.featureStatistics.*;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ide.util.TipUIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class AdaptiveTipDialog extends DialogWrapper {
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 200;

  private JEditorPane myBrowser;
  private String[] myFeatures;
  private int myCurrentFeature;

  public AdaptiveTipDialog(Project project, String[] features) {
    super(project, false);
    myFeatures = features;
    myCurrentFeature = 0;
    setCancelButtonText("&Close");
    setTitle("Adaptive Tip Of The Day");
    setModal(false);
    init();
    selectCurrentFeature();
  }

  private void selectCurrentFeature() {
    String id = myFeatures[myCurrentFeature];
    FeatureUsageTracker.getInstance().triggerFeatureShown(id);

    FeatureDescriptor feature = ProductivityFeaturesRegistry.getInstance().getFeatureDescriptor(id);
    TipUIUtil.openTipInBrowser(feature.getTipFileName(), myBrowser, feature.getProvider());
  }


  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    myBrowser = new JEditorPane("text/html", "Tip go here");
    panel.add(new JScrollPane(myBrowser));
    panel.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
    return panel;
  }

  protected Action[] createActions() {
    if (myFeatures.length == 1) {
      return new Action[] {getCancelAction()};
    }
    else {
      return new Action[] {new PrevAction(), new NextAction(), getCancelAction()};
    }
  }

  private class NextAction extends AbstractAction{
    public NextAction() {
      super("&Next Tip");
    }

    public void actionPerformed(ActionEvent e) {
      myCurrentFeature++;
      if (myCurrentFeature >= myFeatures.length) {
        myCurrentFeature = 0;
      }
      selectCurrentFeature();
    }
  }

  private class PrevAction extends AbstractAction{
    public PrevAction() {
      super("&Prev Tip");
    }

    public void actionPerformed(ActionEvent e) {
      myCurrentFeature--;
      if (myCurrentFeature < 0) {
        myCurrentFeature = myFeatures.length - 1;
      }
      selectCurrentFeature();
    }
  }
}