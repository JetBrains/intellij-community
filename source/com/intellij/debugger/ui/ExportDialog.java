/**
 * created at Dec 14, 2001
 * @author Jeka
 */
package com.intellij.debugger.ui;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.sun.jdi.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Iterator;
import java.util.List;

public class ExportDialog extends DialogWrapper {
  private JTextArea myTextArea = new JTextArea();
  private TextFieldWithBrowseButton myTfFilePath;
  private Project myProject;
  private final DebugProcessImpl myDebugProcess;
  private CopyToClipboardAction myCopyToClipboardAction = new CopyToClipboardAction();

  public ExportDialog(DebugProcessImpl debugProcess, String destinationDirectory) {
    super(debugProcess.getProject(), true);
    myDebugProcess = debugProcess;
    myProject = debugProcess.getProject();
    setTitle("Export Threads");
    setOKButtonText("Save");

    init();

    setOKActionEnabled(false);
    myCopyToClipboardAction.setEnabled(false);

    myTextArea.setText(MessageDescriptor.EVALUATING.getLabel());
    debugProcess.getManagerThread().invoke(new ExportThreadsCommand(ApplicationManager.getApplication().getModalityStateForComponent(myTextArea)));

    myTfFilePath.setText(destinationDirectory + File.separator + "threads_report.txt");
    setHorizontalStretch(1.5f);
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(), myCopyToClipboardAction, getCancelAction()};
  }

  protected JComponent createNorthPanel() {
    JPanel box = new JPanel(new BorderLayout());
    box.add(new JLabel("Export to file:"), BorderLayout.WEST);
    myTfFilePath = new TextFieldWithBrowseButton();
    myTfFilePath.addBrowseFolderListener(null, null, myProject, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    box.add(myTfFilePath, BorderLayout.CENTER);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    panel.add(Box.createVerticalStrut(7), BorderLayout.SOUTH);
    return panel;
  }

  protected JComponent createCenterPanel() {
    myTextArea.setEditable(false);
    JScrollPane pane = new JScrollPane(myTextArea);
    pane.setPreferredSize(new Dimension(400, 300));
    return pane;
  }

  protected void doOKAction() {
    String path = myTfFilePath.getText();
    File file = new File(path);
    if (file.isDirectory()) {
      Messages.showMessageDialog(
        myProject,
        "The specified file is a directory.\nPlease specify a correct file name.",
        "Error",
        Messages.getErrorIcon()
      );
    }
    else if (file.exists()) {
      int answer = Messages.showYesNoDialog(
        myProject,
        "The file\n\"" + path + "\"\nalready exists. Would you like to overwrite it?",
        "File Exists",
        Messages.getQuestionIcon()
      );
      if (answer == 0) {
        super.doOKAction();
      }
    }
    else {
      super.doOKAction();
    }
  }

  public String getFilePath() {
    return myTfFilePath.getText();
  }

  public String getTextToSave() {
    return myTextArea.getText();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.ExportDialog";
  }

  public String getExportThreadsText(VirtualMachineProxyImpl vmProxy) {
    StringBuffer buffer = new StringBuffer(512);
    List threads = vmProxy.getVirtualMachine().allThreads();
    for (Iterator it = threads.iterator(); it.hasNext();) {
      ThreadReference threadReference = (ThreadReference)it.next();
      buffer.append(threadName(threadReference));
      ReferenceType referenceType = threadReference.referenceType();
      if(referenceType != null) {
        Field daemon = referenceType.fieldByName("daemon");
        if(daemon != null) {
          Value value = threadReference.getValue(daemon);
          if(value instanceof BooleanValue && ((BooleanValue)value).booleanValue()) {
            buffer.append(" daemon");
          }
        }

        Field priority = referenceType.fieldByName("priority");
        if(priority != null) {
          Value value = threadReference.getValue(priority);
          if(value instanceof IntegerValue) {
            buffer.append(" prio=" + ((IntegerValue)value).intValue());
          }
        }
      }

      ThreadGroupReference groupReference = threadReference.threadGroup();
      if (groupReference != null) {
        buffer.append(", in group \"");
        buffer.append(groupReference.name());
        buffer.append("\"");
      }
      buffer.append(", status: ");
      buffer.append(DebuggerUtilsEx.getThreadStatusText(threadReference.status()));

      try {
        if(vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
          List list = threadReference.ownedMonitors();
          for (Iterator iterator = list.iterator(); iterator.hasNext();) {
            ObjectReference reference = (ObjectReference)iterator.next();
            List waiting = reference.waitingThreads();
            for (Iterator iterator1 = waiting.iterator(); iterator1.hasNext();) {
              ThreadReference thread = (ThreadReference)iterator1.next();
              buffer.append("\n\t blocks " + threadName(thread));
            }
          }
        }

        ObjectReference waitedMonitor = vmProxy.canGetCurrentContendedMonitor() ? threadReference.currentContendedMonitor() : null;
        if(waitedMonitor != null) {
          if(vmProxy.canGetMonitorInfo()) {
            ThreadReference waitedThread = waitedMonitor.owningThread();
            if (waitedThread != null) {
              buffer.append("\n\t waiting for " + threadName(waitedThread));
            }
          }
        }

        List frames = threadReference.frames();
        for (Iterator frit = frames.iterator(); frit.hasNext();) {
          StackFrame stackFrame = (StackFrame)frit.next();
          Location location = stackFrame.location();
          Method method = location.method();
          buffer.append("\n\t  ");
          buffer.append(method.name());
          buffer.append("():");
          buffer.append(Integer.toString(location.lineNumber()));
          try {
            String sourceName = location.sourceName();
            buffer.append(", ");
            buffer.append(sourceName);
          }
          catch (AbsentInformationException e) {
          }
          catch (InternalError e) {
          }
        }
      }
      catch (IncompatibleThreadStateException e) {
        buffer.append("\n\t Incompatible thread state");
      }
      buffer.append("\n\n");
    }
    return buffer.toString();
  }

  private String threadName(ThreadReference threadReference) {
    return threadReference.name() + "@" + Long.toHexString(threadReference.uniqueID());
  }

  private class CopyToClipboardAction extends AbstractAction {
    public CopyToClipboardAction() {
      super("Copy");
      putValue(Action.SHORT_DESCRIPTION,"&Copy text to clipboard");
    }

    public void actionPerformed(ActionEvent e) {
      String s = StringUtil.convertLineSeparators(myTextArea.getText(), "\n");
      CopyPasteManager.getInstance().setContents(new StringSelection(s));
    }
  }

  private class ExportThreadsCommand extends DebuggerCommandImpl {
    protected ModalityState myModalityState;

    public ExportThreadsCommand(ModalityState modalityState) {
      myModalityState = modalityState;
    }

    private void setText(final String text) {
      DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
          public void run() {
            myTextArea.setText(text);
            setOKActionEnabled(true);
            myCopyToClipboardAction.setEnabled(true);
          }
        }, myModalityState);
    }

    protected void action() {
      setText(getExportThreadsText(myDebugProcess.getVirtualMachineProxy()));
    }
  }
}
