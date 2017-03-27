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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"UseJBColor", "UndesirableClassUsage", "UseDPIAwareInsets", "SSBasedInspection"})
public class StandaloneSwingUpdaterUI extends SwingUpdaterUI {
  private static final int RESULT_REQUIRES_RESTART = 42;

  private static final EmptyBorder FRAME_BORDER = new EmptyBorder(8, 8, 8, 8);
  private static final EmptyBorder BUTTONS_BORDER = new EmptyBorder(5, 0, 0, 0);

  private static final String TITLE = "Update";

  private static final String CANCEL_BUTTON_TITLE = "Cancel";
  private static final String EXIT_BUTTON_TITLE = "Exit";

  private final InstallOperation myOperation;

  private final JLabel myProcessTitle;
  private final JProgressBar myProcessProgress;
  private final JLabel myProcessStatus;
  private final JTextArea myConsole;
  private final JPanel myConsolePane;

  private final JButton myCancelButton;
  private final JFrame myFrame;

  private final Queue<Runnable> myQueue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicBoolean hasError = new AtomicBoolean(false);
  private boolean myApplied;

  public StandaloneSwingUpdaterUI(InstallOperation operation) {
    myOperation = operation;

    myProcessTitle = new JLabel(" ");
    myProcessProgress = new JProgressBar(0, 100);
    myProcessStatus = new JLabel(" ");

    myCancelButton = new JButton(CANCEL_BUTTON_TITLE);

    myConsole = new JTextArea();
    myConsole.setLineWrap(true);
    myConsole.setWrapStyleWord(true);
    myConsole.setCaretPosition(myConsole.getText().length());
    myConsole.setTabSize(1);
    myConsole.setMargin(new Insets(2, 4, 2, 4));
    myConsolePane = new JPanel(new BorderLayout());
    myConsolePane.add(new JScrollPane(myConsole));
    myConsolePane.setBorder(BUTTONS_BORDER);
    myConsolePane.setVisible(false);

    myCancelButton.addActionListener(e -> doCancel());

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

    JPanel processPanel = new JPanel();
    processPanel.setLayout(new BoxLayout(processPanel, BoxLayout.Y_AXIS));
    processPanel.add(myProcessTitle);
    processPanel.add(myProcessProgress);
    processPanel.add(myProcessStatus);

    processPanel.add(myConsolePane);
    for (Component each : processPanel.getComponents()) {
      ((JComponent)each).setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BUTTONS_BORDER);
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    buttonsPanel.add(Box.createHorizontalGlue());
    buttonsPanel.add(myCancelButton);

    myFrame.add(processPanel, BorderLayout.CENTER);
    myFrame.add(buttonsPanel, BorderLayout.SOUTH);

    myFrame.setMinimumSize(new Dimension(500, 50));
    myFrame.pack();
    myFrame.setLocationRelativeTo(null);

    myFrame.setVisible(true);

    myQueue.add(this::doPerform);

    startRequestDispatching();
  }

  @Override
  public void setDescription(String oldBuildDesc, String newBuildDesc) {
    myProcessTitle.setText("<html>Updating " + oldBuildDesc + " to " + newBuildDesc + "...");
  }

  @Override
  public boolean showWarning(String message) {
    Object[] choices = { "Retry", "Exit" };
    int choice = JOptionPane.showOptionDialog(null, message, "Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
    return choice == 0;
  }

  private void startRequestDispatching() {
    new Thread("Updater UI Dispatcher") {
      @Override
      public void run() {
        while (true) {
          try {
            //noinspection BusyWait
            Thread.sleep(100);
          }
          catch (InterruptedException e) {
            Runner.printStackTrace(e);
            return;
          }

          List<Runnable> pendingRequests = new ArrayList<>();
          Runnable request;
          while ((request = myQueue.poll()) != null) {
            pendingRequests.add(request);
          }

          SwingUtilities.invokeLater(() -> {
            for (Runnable each : pendingRequests) {
              each.run();
            }
          });
        }
      }
    }.start();
  }

  private void doCancel() {
    if (isRunning.get()) {
      String message = "The patch has not been applied yet.\nAre you sure you want to abort the operation?";
      int result = JOptionPane.showConfirmDialog(myFrame, message, TITLE, JOptionPane.YES_NO_OPTION);
      if (result == JOptionPane.YES_OPTION) {
        isCancelled.set(true);
        myCancelButton.setEnabled(false);
      }
    }
    else {
      exit();
    }
  }

  private void doPerform() {
    isRunning.set(true);

    new Thread(() -> {
      try {
        myApplied = myOperation.execute(this);
      }
      catch (OperationCancelledException ignore) {
        Runner.printStackTrace(ignore);
      }
      catch(Throwable e) {
        Runner.printStackTrace(e);
        showError(e);
      }
      finally {
        isRunning.set(false);

        if (hasError.get()) {
          startProcess("Failed to apply patch");
          setProgress(100);
          myCancelButton.setText(EXIT_BUTTON_TITLE);
          myCancelButton.setEnabled(true);
        }
        else {
          exit();
        }
      }
    }, "swing updater").start();
  }

  @Override
  protected Component getParentComponent() {
    return myFrame;
  }

  @Override
  protected void notifyCancelled() {
    myCancelButton.setEnabled(false);
  }

  @Override
  protected void exit() {
    System.exit(myApplied ? RESULT_REQUIRES_RESTART : 0);
  }

  @Override
  public void startProcess(String title) {
    myQueue.add(() -> {
      myProcessStatus.setText(title);
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(0);
    });
  }

  @Override
  public void setProgress(int percentage) {
    myQueue.add(() -> {
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(percentage);
    });
  }

  @Override
  public void setProgressIndeterminate() {
    myQueue.add(() -> myProcessProgress.setIndeterminate(true));
  }

  @Override
  public void setStatus(String status) {
  }

  @Override
  public void showError(Throwable e) {
    hasError.set(true);

    myQueue.add(() -> {
      StringWriter w = new StringWriter();
      if (!myConsolePane.isVisible()) {
        w.write("Temp. directory: ");
        w.write(System.getProperty("java.io.tmpdir"));
        w.write("\n\n");
      }
      e.printStackTrace(new PrintWriter(w));
      w.append("\n");
      myConsole.append(w.getBuffer().toString());
      if (!myConsolePane.isVisible()) {
        myConsole.setCaretPosition(0);
        myConsolePane.setVisible(true);
        myConsolePane.setPreferredSize(new Dimension(10, 200));
        myFrame.pack();
      }
    });
  }

  @Override
  public void checkCancelled() throws OperationCancelledException {
    if (isCancelled.get()) throw new OperationCancelledException();
  }

  @FunctionalInterface
  public interface InstallOperation {
    boolean execute(UpdaterUI ui) throws OperationCancelledException;
  }

  @FunctionalInterface
  private interface UpdateRequest {
    void perform();
  }
}
