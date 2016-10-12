package com.intellij.updater;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"UndesirableClassUsage", "UseJBColor", "UseDPIAwareInsets", "SSBasedInspection"}) // Plain Swing
public class StandaloneSwingUpdaterUI extends SwingUpdaterUI {
  private static final int RESULT_REQUIRES_RESTART = 42;

  private static final EmptyBorder FRAME_BORDER = new EmptyBorder(8, 8, 8, 8);
  private static final EmptyBorder LABEL_BORDER = new EmptyBorder(0, 0, 5, 0);
  private static final EmptyBorder BUTTONS_BORDER = new EmptyBorder(5, 0, 0, 0);

  private static final String TITLE = "Update";

  private static final String CANCEL_BUTTON_TITLE = "Cancel";
  private static final String EXIT_BUTTON_TITLE = "Exit";

  private static final String PROCEED_BUTTON_TITLE = "Proceed";

  private final InstallOperation myOperation;

  private final JLabel myProcessTitle;
  private final JProgressBar myProcessProgress;
  private final JLabel myProcessStatus;
  private final JTextArea myConsole;
  private final JPanel myConsolePane;

  private final JButton myCancelButton;

  private final ConcurrentLinkedQueue<UpdateRequest> myQueue = new ConcurrentLinkedQueue<UpdateRequest>();
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicBoolean hasError = new AtomicBoolean(false);
  private final JFrame myFrame;
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
    myFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

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
    Object[] choices = new Object[] { "Retry", "Exit" };
    int choice = JOptionPane.showOptionDialog(null, message, "Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
    return choice == 0;
  }

  private void startRequestDispatching() {
    new Thread(() -> {
      while (true) {
        try {
          Thread.sleep(100);
        }
        catch (InterruptedException e) {
          Runner.printStackTrace(e);
          return;
        }

        final List<UpdateRequest> pendingRequests = new ArrayList<UpdateRequest>();
        UpdateRequest request;
        while ((request = myQueue.poll()) != null) {
          pendingRequests.add(request);
        }

        SwingUtilities.invokeLater(() -> {
          for (UpdateRequest each : pendingRequests) {
            each.perform();
          }
        });
      }
    }, "swing updater dispatch").start();
  }

  private void doCancel() {
    if (isRunning.get()) {
      int result = JOptionPane.showConfirmDialog(myFrame,
                                                 "The patch has not been applied yet.\nAre you sure you want to abort the operation?",
                                                 TITLE, JOptionPane.YES_NO_OPTION);
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
        } else {
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
  public void startProcess(final String title) {
    myQueue.add(() -> {
      myProcessStatus.setText(title);
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(0);
    });
  }

  @Override
  public void setProgress(final int percentage) {
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
  public void setStatus(final String status) {
  }

  @Override
  public void showError(final Throwable e) {
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

  public interface InstallOperation {
    boolean execute(UpdaterUI ui) throws OperationCancelledException;
  }

  private interface UpdateRequest {
    void perform();
  }

  public static void main(String[] args) {
    new StandaloneSwingUpdaterUI(ui -> {
      ui.startProcess("Process1");
      ui.checkCancelled();
      for (int i = 0; i < 200; i++) {
        ui.setStatus("i = " + i);
        ui.checkCancelled();
        try {
          Thread.sleep(10);
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        ui.setProgress((i + 1) * 100 / 200);
      }

      ui.showError(new Throwable());

      ui.startProcess("Process3");
      ui.checkCancelled();
      ui.setProgressIndeterminate();
      try {
        for (int i = 0; i < 200; i++) {
          ui.setStatus("i = " + i);
          ui.checkCancelled();
          try {
            Thread.sleep(10);
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          ui.setProgress((i + 1) * 100 / 200);
          if (i == 100) {
            List<ValidationResult> vr = new ArrayList<ValidationResult>();
            vr.add(new ValidationResult(ValidationResult.Kind.ERROR,
                                        "foo/bar", null,
                                        ValidationResult.Action.CREATE,
                                        "Hello",
                                        ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP));
            vr.add(new ValidationResult(ValidationResult.Kind.CONFLICT,
                                        "foo/bar/baz", null,
                                        ValidationResult.Action.DELETE,
                                        "World",
                                        ValidationResult.Option.DELETE, ValidationResult.Option.KEEP));
            vr.add(new ValidationResult(ValidationResult.Kind.INFO,
                                        "xxx", null,
                                        ValidationResult.Action.NO_ACTION,
                                        "bla-bla", ValidationResult.Option.IGNORE));
            ui.askUser(vr);
          }
        }
      }
      finally {
        ui.startProcess("Process2");
        for (int i = 0; i < 200; i++) {
          ui.setStatus("i = " + i);
          try {
            Thread.sleep(10);
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          ui.setProgress((i + 1) * 100 / 200);
        }
      }
      return true;
    });
  }
}
