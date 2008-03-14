package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.HashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Map;

/**
 * @author nik
 */
public class XDebugFramesView extends XDebugViewBase {
  private JPanel myMainPanel;
  private XDebuggerFramesList myFramesList;
  private JComboBox myThreadComboBox;
  private XExecutionStack mySelectedStack;
  private boolean myListenersEnabled;
  private Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<XExecutionStack, StackFramesListBuilder>();

  public XDebugFramesView(final XDebugSession session, final Disposable parentDisposable) {
    super(session, parentDisposable);

    myMainPanel = new JPanel(new BorderLayout());

    myThreadComboBox = new JComboBox();
    myThreadComboBox.setRenderer(new ThreadComboBoxRenderer());
    myThreadComboBox.addItemListener(new MyItemListener());
    myMainPanel.add(myThreadComboBox);

    myFramesList = new XDebuggerFramesList();
    myFramesList.addListSelectionListener(new MyListSelectionListener());
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myFramesList), BorderLayout.CENTER);
    rebuildView();
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack) {
    StackFramesListBuilder builder = myBuilders.get(executionStack);
    if (builder == null) {
      builder = new StackFramesListBuilder(executionStack);
      myBuilders.put(executionStack, builder);
    }
    return builder;
  }

  protected void rebuildView() {
    myListenersEnabled = false;
    XSuspendContext suspendContext = mySession.getSuspendContext();
    if (suspendContext == null) {
      myThreadComboBox.removeAllItems();
      myFramesList.clear();
      return;
    }

    XExecutionStack[] executionStacks = suspendContext.getExecutionStacks();
    for (XExecutionStack executionStack : executionStacks) {
      myThreadComboBox.addItem(executionStack);
    }
    XExecutionStack activeExecutionStack = suspendContext.getActiveExecutionStack();
    myThreadComboBox.setSelectedItem(activeExecutionStack);
    myThreadComboBox.setVisible(executionStacks.length != 1);
    updateFrames(activeExecutionStack);
    myListenersEnabled = true;
  }

  private void updateFrames(final XExecutionStack executionStack) {
    if (mySelectedStack == executionStack) {
      return;
    }
    if (mySelectedStack != null) {
      getOrCreateBuilder(mySelectedStack).stop();
    }

    mySelectedStack = executionStack;
    StackFramesListBuilder builder = getOrCreateBuilder(executionStack);
    builder.initModel(myFramesList.getModel());
    builder.start();
    XStackFrame topFrame = executionStack.getTopFrame();
    if (topFrame != null) {
      myFramesList.setSelectedValue(topFrame, true);
      onFrameSelected(topFrame);
    }
  }

  public XDebuggerFramesList getFramesList() {
    return myFramesList;
  }

  private void onFrameSelected(final XStackFrame stackFrame) {
    mySession.setCurrentStackFrame(stackFrame);
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }


  private class MyItemListener implements ItemListener {
    public void itemStateChanged(final ItemEvent e) {
      if (!myListenersEnabled) return;

      if (e.getStateChange() == ItemEvent.SELECTED) {
        updateFrames((XExecutionStack)e.getItem());
      }
    }
  }

  private class MyListSelectionListener implements ListSelectionListener {
    public void valueChanged(final ListSelectionEvent e) {
      if (!myListenersEnabled || e.getValueIsAdjusting()) return;

      onFrameSelected((XStackFrame)myFramesList.getSelectedValue());
    }
  }

  private class StackFramesListBuilder implements XExecutionStack.XStackFrameCallback {
    private XExecutionStack myExecutionStack;
    private XStackFrame[] myStackFrames;
    private TIntObjectHashMap<String> myErrorMessages;
    private int myNextFrameIndex;
    private boolean myRunning;

    private StackFramesListBuilder(final XExecutionStack executionStack) {
      myExecutionStack = executionStack;
      myStackFrames = new XStackFrame[executionStack.getFramesCount()];
      myStackFrames[0] = executionStack.getTopFrame();
      myNextFrameIndex = 1;
    }

    public void stackFrameObtained(@NotNull final XStackFrame stackFrame) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myStackFrames[myNextFrameIndex] = stackFrame;
          setFrameListElement(stackFrame);
          myNextFrameIndex++;
          continueBuilding();
        }
      });
    }

    private void setFrameListElement(final Object value) {
      if (myExecutionStack == mySelectedStack) {
        myFramesList.getModel().setElementAt(value, myNextFrameIndex);
      }
      myFramesList.repaint();
    }

    private void continueBuilding() {
      if (myNextFrameIndex >= myStackFrames.length) {
        myRunning = false;
      }
      if (!myRunning) return;

      myExecutionStack.obtainStackFrame(myNextFrameIndex, this);
    }

    public void errorOccured(final String errorMessage) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myErrorMessages == null) {
            myErrorMessages = new TIntObjectHashMap<String>();
          }
          myErrorMessages.put(myNextFrameIndex, errorMessage);
          setFrameListElement(errorMessage);
          myNextFrameIndex++;
          continueBuilding();
        }
      });
    }

    public void start() {
      myRunning = true;
      continueBuilding();
    }

    public void stop() {
      myRunning = false;
    }

    public void initModel(final DefaultListModel model) {
      model.removeAllElements();
      for (int i = 0; i < myStackFrames.length; i++) {
        XStackFrame frame = myStackFrames[i];
        if (frame != null) {
          model.addElement(frame);
        }
        else if (myErrorMessages != null && myErrorMessages.containsKey(i)) {
          model.addElement(myErrorMessages.get(i));
        }
        else {
          model.addElement(null);
        }
      }
    }
  }
}
