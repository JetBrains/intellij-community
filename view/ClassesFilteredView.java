package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.*;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.debugger.memory.event.MemoryViewManagerListener;
import org.jetbrains.debugger.memory.component.MemoryViewManager;
import org.jetbrains.debugger.memory.component.MemoryViewManagerState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.utils.KeyboardUtils;
import org.jetbrains.debugger.memory.utils.SingleAlarmWithMutableDelay;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClassesFilteredView extends BorderLayoutPanel {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  private final static double DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT = 0.5;

  private final Project myProject;
  private final XDebugSession myDebugSession;
  private final DebugProcess myDebugProcess;
  private final SingleAlarmWithMutableDelay mySingleAlarm;

  private final SearchTextField myFilterTextField = new SearchTextField(false);
  private final ClassesTable myTable;

  private boolean myNeedReloadClasses = false;

  public ClassesFilteredView(@NotNull XDebugSession debugSession) {
    super();

    myProject = debugSession.getProject();
    myDebugSession = debugSession;
    myDebugProcess = DebuggerManager.getInstance(myProject)
        .getDebugProcess(myDebugSession.getDebugProcess().getProcessHandler());

    MemoryViewManagerState memoryViewManagerState = MemoryViewManager.getInstance(myProject).getState();

    myTable = new ClassesTable(memoryViewManagerState.isShowWithDiffOnly,
        memoryViewManagerState.isShowWithInstancesOnly);

    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (KeyboardUtils.isEnterKey(keyCode)) {
          handleClassSelection(myTable.getSelectedClass());
        } else if (!KeyboardUtils.isArrowKey(keyCode) && KeyboardUtils.isCharacter(keyCode)) {
          SwingUtilities.invokeLater(myFilterTextField::requestFocusInWindow);
          String text = myFilterTextField.getText();
          myFilterTextField.setText(text + KeyEvent.getKeyText(keyCode).toLowerCase());
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        handleClassSelection(myTable.getSelectedClass());
        return true;
      }
    }.installOn(myTable);

    final MemoryViewManagerListener memoryViewManagerListener = state -> {
      myTable.setFilteringByDiffNonZero(state.isShowWithDiffOnly);
      myTable.setFilteringByInstanceExists(state.isShowWithInstancesOnly);
    };

    MemoryViewManager.getInstance(myProject).addMemoryViewManagerListener(memoryViewManagerListener);

    myDebugSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionResumed() {
        SwingUtilities.invokeLater(myTable::hideContent);
      }

      @Override
      public void sessionStopped() {
        debugSession.removeSessionListener(this);
        MemoryViewManager.getInstance(myProject).removeMemoryViewManagerListener(memoryViewManagerListener);
      }

      @Override
      public void sessionPaused() {
        if (myNeedReloadClasses) {
          updateClassesAndCounts();
        }
      }
    });

    myFilterTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myTable.setFilterPattern(myFilterTextField.getText());
      }
    });

    mySingleAlarm = new SingleAlarmWithMutableDelay(() -> {
      SuspendContextImpl suspendContext = getSuspendContext();
      if (suspendContext != null) {
        SwingUtilities.invokeLater(() -> myTable.setBusy(true));

        ((DebuggerManagerThreadImpl) myDebugProcess.getManagerThread())
            .schedule(new MyUpdateClassesCommand(suspendContext));
      }
    });

    JScrollPane scroll = ScrollPaneFactory.createScrollPane(myTable, SideBorder.TOP);
    addToTop(myFilterTextField);
    addToCenter(scroll);
  }

  public void setNeedReloadClasses(boolean value) {
    if (myNeedReloadClasses != value) {
      myNeedReloadClasses = value;
      if (myNeedReloadClasses) {
        updateClassesAndCounts();
      }
    }
  }

  private void handleClassSelection(@Nullable ReferenceType ref) {
    if (ref != null && myDebugSession.isSuspended()) {
      InstancesWindow window = new InstancesWindow(myProject, myDebugSession, ref);
      window.show();
    }
  }

  private SuspendContextImpl getSuspendContext() {
    return DebuggerManagerImpl.getInstanceEx(myProject).getContext().getSuspendContext();
  }

  private void updateClassesAndCounts() {
    if (myDebugProcess.isAttached()) {
      mySingleAlarm.cancelAndRequest();
    }
  }

  private final class MyUpdateClassesCommand extends SuspendContextCommandImpl {
    MyUpdateClassesCommand(@Nullable SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    @Override
    public Priority getPriority() {
      return Priority.LOWEST;
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
      loadClassesAndCounts();
    }

    @Override
    public void commandCancelled() {
    }
  }

  private void loadClassesAndCounts() {
    assert DebuggerManager.getInstance(myProject).isDebuggerManagerThread();
    myDebugProcess.getVirtualMachineProxy();
    List<ReferenceType> classes = myDebugProcess.getVirtualMachineProxy().allClasses();

    if (classes.isEmpty()) {
      return;
    }

    VirtualMachine vm = classes.get(0).virtualMachine();
    long start = System.nanoTime();
    long[] counts = vm.instanceCounts(classes);
    long delay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    mySingleAlarm.setDelay((int) (DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT * delay));

    LOG.info(String.format("Instances query time = %d ms. Count = %d%n", delay, classes.size()));
    SwingUtilities.invokeLater(() -> myTable.setClassesAndCounts(classes, counts));
  }
}
