/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

@SuppressWarnings({"UndesirableClassUsage", "UseJBColor", "UseDPIAwareInsets", "UseDPIAwareBorders"})
public class StandaloneSwingUpdaterUI extends SwingUpdaterUI {
  private static final EmptyBorder FRAME_BORDER = new EmptyBorder(8, 8, 8, 8);
  private static final EmptyBorder BUTTONS_BORDER = new EmptyBorder(5, 0, 0, 0);

  private final JLabel myProcessTitle;
  private final JProgressBar myProcessProgress;
  private final JLabel myProcessStatus;

  private final JButton myCancelButton;
  private final JFrame myFrame;

  public StandaloneSwingUpdaterUI() {

    myProcessTitle = new JLabel(" ");
    myProcessProgress = new JProgressBar(0, 100);
    myProcessStatus = new JLabel(" ");

    myCancelButton = new JButton(CANCEL_BUTTON_TITLE);
    myCancelButton.addActionListener(e -> doCancel());

    JPanel processPanel = new JPanel();
    processPanel.setLayout(new BoxLayout(processPanel, BoxLayout.Y_AXIS));
    processPanel.add(myProcessTitle);
    processPanel.add(myProcessProgress);
    processPanel.add(myProcessStatus);
    for (Component each : processPanel.getComponents()) {
      ((JComponent)each).setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BUTTONS_BORDER);
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    buttonsPanel.add(Box.createHorizontalGlue());
    buttonsPanel.add(myCancelButton);

    myFrame = new JFrame();
    myFrame.setTitle(TITLE);
    myFrame.setLayout(new BorderLayout());
    myFrame.getRootPane().setBorder(FRAME_BORDER);
    myFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    myFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        doCancel();
      }
    });
    myFrame.add(processPanel, BorderLayout.CENTER);
    myFrame.add(buttonsPanel, BorderLayout.SOUTH);
    myFrame.setMinimumSize(new Dimension(500, 50));
    myFrame.pack();
    myFrame.setLocationRelativeTo(null);
    myFrame.setVisible(true);

    invokeAndWait(() -> {});
  }

  @Override
  public void setDescription(String oldBuildDesc, String newBuildDesc) {
    invokeLater(() -> myProcessTitle.setText("<html>Updating " + oldBuildDesc + " to " + newBuildDesc + "..."));
  }

  @Override
  public void setDescription(String text) {
    invokeLater(() -> myProcessTitle.setText(text.isEmpty() ? " " : text));
  }

  @Override
  public void startProcess(String title) {
    invokeLater(() -> {
      myProcessStatus.setText(title);
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(0);
    });
  }

  @Override
  public void setProgress(int percentage) {
    invokeLater(() -> {
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(percentage);
    });
  }

  @Override
  public void setProgressIndeterminate() {
    invokeLater(() -> myProcessProgress.setIndeterminate(true));
  }

  @Override
  public void checkCancelled() throws OperationCancelledException {
    while (myPaused) Utils.pause(10);
    if (myCancelled) throw new OperationCancelledException();
  }

  @Override
  protected Component getParentComponent() {
    return myFrame;
  }

  @Override
  protected void notifyCancelled() {
    myCancelButton.setEnabled(false);
  }
}
