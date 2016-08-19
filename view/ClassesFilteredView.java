package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.*;
import com.intellij.util.SmartList;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.component.MemoryViewManager;
import org.jetbrains.debugger.memory.component.MemoryViewManagerState;
import org.jetbrains.debugger.memory.event.MemoryViewManagerListener;
import org.jetbrains.debugger.memory.utils.AndroidUtil;
import org.jetbrains.debugger.memory.utils.KeyboardUtils;
import org.jetbrains.debugger.memory.utils.SingleAlarmWithMutableDelay;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class ClassesFilteredView extends BorderLayoutPanel {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  private final static double DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT = 0.5;
  private final static int DEFAULT_BATCH_SIZE = Integer.MAX_VALUE;
  private static final String EMPTY_TABLE_CONTENT_WHEN_RUNNED = "The application is running";
  private static final String EMPTY_TABLE_CONTENT_WHEN_SUSPENDED = "Nothing to show";


  private final Project myProject;
  private final XDebugSession myDebugSession;
  private final DebugProcess myDebugProcess;
  private final SingleAlarmWithMutableDelay mySingleAlarm;

  private final SearchTextField myFilterTextField = new FilterTextField();
  private final ClassesTable myTable;

  private volatile SuspendContextImpl myLastSuspendContext;
  private volatile boolean myNeedReloadClasses = false;

  public ClassesFilteredView(@NotNull XDebugSession debugSession) {
    super();

    myProject = debugSession.getProject();
    myDebugSession = debugSession;
    myDebugProcess = DebuggerManager.getInstance(myProject)
        .getDebugProcess(myDebugSession.getDebugProcess().getProcessHandler());

    MemoryViewManagerState memoryViewManagerState = MemoryViewManager.getInstance().getState();

    myTable = new ClassesTable(myDebugSession, memoryViewManagerState.isShowWithDiffOnly,
        memoryViewManagerState.isShowWithInstancesOnly);

    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (KeyboardUtils.isEnterKey(keyCode)) {
          handleClassSelection(myTable.getSelectedClass());
        } else if (KeyboardUtils.isCharacter(keyCode) || KeyboardUtils.isBackSpace(keyCode)) {
          String text = myFilterTextField.getText();
          String newText = KeyboardUtils.isBackSpace(keyCode)
              ? text.substring(0, text.length() - 1)
              : text + e.getKeyChar();
          myFilterTextField.setText(newText);
          FocusManager.getCurrentManager().focusNextComponent(myFilterTextField);
        }
      }
    });

    myFilterTextField.addKeyboardListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        dispatch(e);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        dispatch(e);
      }

      private void dispatch(KeyEvent e) {
        if (KeyboardUtils.isUpDownKey(e.getKeyCode()) || KeyboardUtils.isEnterKey(e.getKeyCode())) {
          myTable.dispatchEvent(e);
        }
      }
    });

    myFilterTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myTable.setFilterPattern(myFilterTextField.getText());
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

    MemoryViewManager.getInstance().addMemoryViewManagerListener(memoryViewManagerListener);

    myDebugSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionResumed() {
        SwingUtilities.invokeLater(() -> {
          myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_RUNNED);
          myTable.hideContent();
        });
        mySingleAlarm.cancelAllRequests();
      }

      @Override
      public void sessionStopped() {
        debugSession.removeSessionListener(this);
        MemoryViewManager.getInstance().removeMemoryViewManagerListener(memoryViewManagerListener);
      }

      @Override
      public void sessionPaused() {
        SwingUtilities.invokeLater(() -> myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_SUSPENDED));
        if (myNeedReloadClasses) {
          updateClassesAndCounts();
        }
      }
    });

    mySingleAlarm = new SingleAlarmWithMutableDelay(() -> {
      myLastSuspendContext = getSuspendContext();
      if (myLastSuspendContext != null) {
        SwingUtilities.invokeLater(() -> {
          myTable.setBusy(true);
          ((DebuggerManagerThreadImpl) myDebugProcess.getManagerThread())
              .schedule(new MyUpdateClassesCommand(myLastSuspendContext));
        });
      }
    });

    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu menu = createContextMenu();
        if (menu != null) {
          menu.getComponent().show(comp, x, y);
        }
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
        SuspendContextImpl suspendContext = getSuspendContext();
        if (suspendContext != null && !suspendContext.equals(myLastSuspendContext)) {
          updateClassesAndCounts();
        }
      }
    }
  }

  private void handleClassSelection(@Nullable ReferenceType ref) {
    if (ref != null && myDebugSession.isSuspended()) {
      new InstancesWindow(myDebugSession, ref).show();
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

  private ActionPopupMenu createContextMenu() {
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("ClassesView.PopupActionGroup");
    return ActionManager.getInstance().createActionPopupMenu("ClassesView.PopupActionGroup", group);
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
      final List<ReferenceType> classes = myDebugProcess.getVirtualMachineProxy().allClasses();

      if (classes.isEmpty()) {
        return;
      }

      VirtualMachine vm = classes.get(0).virtualMachine();
      int batchSize = AndroidUtil.isAndroidVM(vm)
          ? AndroidUtil.ANDROID_COUNT_BY_CLASSES_BATCH_SIZE
          : DEFAULT_BATCH_SIZE;

      List<long[]> chunks = new SmartList<>();
      int size = classes.size();
      for (int begin = 0, end = Math.min(batchSize, size);
           begin != size && contextIsValid();
           begin = end, end = Math.min(end + batchSize, size)) {
        List<ReferenceType> batch = classes.subList(begin, end);

        long start = System.nanoTime();
        long[] counts = vm.instanceCounts(batch);
        long delay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        chunks.add(counts);

        mySingleAlarm.setDelay((int) (DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT * delay));
        LOG.info(String.format("Instances query time = %d ms. Count = %d", delay, batch.size()));
      }

      final long[] counts = chunks.size() == 1 ? chunks.get(0) : IntStream.range(0, chunks.size()).boxed()
          .flatMapToLong(integer -> Arrays.stream(chunks.get(integer)))
          .toArray();

      SwingUtilities.invokeLater(() -> {
        myTable.setClassesAndUpdateCounts(classes, counts);
        myTable.setBusy(false);
      });
    }

    @Override
    public void commandCancelled() {
    }

    private boolean contextIsValid() {
      return ClassesFilteredView.this.getSuspendContext() == getSuspendContext();
    }
  }

  private class FilterTextField extends SearchTextField {
    FilterTextField() {
      super(false);
    }

    @Override
    protected void showPopup() {
    }

    @Override
    protected boolean hasIconsOutsideOfTextField() {
      return false;
    }
  }
}
