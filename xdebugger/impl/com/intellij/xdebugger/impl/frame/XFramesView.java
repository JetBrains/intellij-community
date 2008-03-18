package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.HashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class XFramesView extends XDebugViewBase {
  private JPanel myMainPanel;
  private XDebuggerFramesList myFramesList;
  private JComboBox myThreadComboBox;
  private XExecutionStack mySelectedStack;
  private boolean myListenersEnabled;
  private Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<XExecutionStack, StackFramesListBuilder>();

  public XFramesView(final XDebugSession session, final Disposable parentDisposable) {
    super(session, parentDisposable);

    myMainPanel = new JPanel(new BorderLayout());

    myThreadComboBox = new JComboBox();
    myThreadComboBox.setRenderer(new ThreadComboBoxRenderer());
    myThreadComboBox.addItemListener(new MyItemListener());
    myMainPanel.add(myThreadComboBox);

    myFramesList = new XDebuggerFramesList();
    myFramesList.addListSelectionListener(new MyListSelectionListener());
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myFramesList), BorderLayout.CENTER);
    rebuildView(SessionEvent.RESUMED);
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack) {
    StackFramesListBuilder builder = myBuilders.get(executionStack);
    if (builder == null) {
      builder = new StackFramesListBuilder(executionStack);
      myBuilders.put(executionStack, builder);
    }
    return builder;
  }

  protected void rebuildView(final SessionEvent event) {
    if (event == SessionEvent.FRAME_CHANGED || event == SessionEvent.BEFORE_RESUME) return;

    myListenersEnabled = false;
    for (StackFramesListBuilder builder : myBuilders.values()) {
      builder.dispose();
    }
    myBuilders.clear();
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

  private void onFrameSelected(final @NotNull XStackFrame stackFrame) {
    mySession.setCurrentStackFrame(stackFrame);
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }


  private class MyItemListener implements ItemListener {
    public void itemStateChanged(final ItemEvent e) {
      if (!myListenersEnabled) return;

      if (e.getStateChange() == ItemEvent.SELECTED) {
        Object item = e.getItem();
        if (item instanceof XExecutionStack) {
          updateFrames((XExecutionStack)item);
        }
      }
    }
  }

  private class MyListSelectionListener implements ListSelectionListener {
    public void valueChanged(final ListSelectionEvent e) {
      if (!myListenersEnabled || e.getValueIsAdjusting()) return;

      Object selected = myFramesList.getSelectedValue();
      if (selected instanceof XStackFrame) {
        onFrameSelected((XStackFrame)selected);
      }
    }
  }

  private class StackFramesListBuilder implements XExecutionStack.XStackFrameContainer {
    private XExecutionStack myExecutionStack;
    private List<XStackFrame> myStackFrames;
    private String myErrorMessage;
    private int myNextFrameIndex;
    private boolean myRunning;
    private boolean myAllFramesLoaded;

    private StackFramesListBuilder(final XExecutionStack executionStack) {
      myExecutionStack = executionStack;
      myStackFrames = new ArrayList<XStackFrame>();
      myStackFrames.add(executionStack.getTopFrame());
      myNextFrameIndex = 1;
    }

    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, final boolean last) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myStackFrames.addAll(stackFrames);
          addFrameListElements(stackFrames, last);
          myNextFrameIndex += stackFrames.size();
          myAllFramesLoaded = last;
          if (last) {
            myRunning = false;
          }
        }
      });
    }

    private void addFrameListElements(final List<?> values, final boolean last) {
      if (myExecutionStack != null && myExecutionStack == mySelectedStack) {
        DefaultListModel model = myFramesList.getModel();
        if (model.getElementAt(model.getSize() - 1) == null) {
          model.removeElementAt(model.getSize() - 1);
        }
        for (Object value : values) {
          model.addElement(value);
        }
        if (!last) {
          model.addElement(null);
        }
        myFramesList.repaint();
      }
    }

    public boolean isObsolete() {
      return !myRunning;
    }

    public void errorOccured(final String errorMessage) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myErrorMessage = errorMessage;
          addFrameListElements(Collections.singletonList(errorMessage), true);
          myRunning = false;
        }
      });
    }

    public void dispose() {
      myRunning = false;
      myExecutionStack = null;
    }

    public void start() {
      if (myExecutionStack == null) {
        return;
      }
      myRunning = true;
      myExecutionStack.computeStackFrames(myNextFrameIndex, this);
    }

    public void stop() {
      myRunning = false;
    }

    public void initModel(final DefaultListModel model) {
      model.removeAllElements();
      for (XStackFrame stackFrame : myStackFrames) {
        model.addElement(stackFrame);
      }
      if (myErrorMessage != null) {
        model.addElement(myErrorMessage);
      }
      else if (!myAllFramesLoaded) {
        model.addElement(null);
      }
    }
  }
}
