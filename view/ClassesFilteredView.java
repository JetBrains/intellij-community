package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.*;
import com.intellij.util.SmartList;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.component.CreationPositionTracker;
import org.jetbrains.debugger.memory.component.InstancesTracker;
import org.jetbrains.debugger.memory.component.MemoryViewManager;
import org.jetbrains.debugger.memory.component.MemoryViewManagerState;
import org.jetbrains.debugger.memory.event.InstancesTrackerListener;
import org.jetbrains.debugger.memory.event.MemoryViewManagerListener;
import org.jetbrains.debugger.memory.tracking.ClassPreparedListener;
import org.jetbrains.debugger.memory.tracking.ConstructorInstancesTracker;
import org.jetbrains.debugger.memory.tracking.TrackerForNewInstances;
import org.jetbrains.debugger.memory.tracking.TrackingType;
import org.jetbrains.debugger.memory.utils.AndroidUtil;
import org.jetbrains.debugger.memory.utils.KeyboardUtils;
import org.jetbrains.debugger.memory.utils.LowestPriorityCommand;
import org.jetbrains.debugger.memory.utils.SingleAlarmWithMutableDelay;

import javax.swing.FocusManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.jetbrains.debugger.memory.view.ClassesTable.DiffViewTableModel.CLASSNAME_COLUMN_INDEX;
import static org.jetbrains.debugger.memory.view.ClassesTable.DiffViewTableModel.DIFF_COLUMN_INDEX;

public class ClassesFilteredView extends BorderLayoutPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  private final static double DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT = 0.5;
  private final static double MAX_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(2);
  private final static int DEFAULT_BATCH_SIZE = Integer.MAX_VALUE;
  private static final String EMPTY_TABLE_CONTENT_WHEN_RUNNING = "The application is running";
  private static final String EMPTY_TABLE_CONTENT_WHEN_SUSPENDED = "Nothing to show";

  private final Project myProject;
  private final XDebugSession myDebugSession;
  private final DebugProcessImpl myDebugProcess;
  private final SingleAlarmWithMutableDelay mySingleAlarm;

  private final SearchTextField myFilterTextField = new FilterTextField();
  private final ClassesTable myTable;
  private final InstancesTracker myInstancesTracker;
  private final Map<ReferenceType, ConstructorInstancesTracker> myConstructorTrackedClasses = new ConcurrentHashMap<>();
  private final XDebugSessionListener myDebugSessionListener;

  private volatile SuspendContextImpl myLastSuspendContext;

  /**
   * Indicates that the debug session had been stopped at least once.
   * <p>
   * State: false to true
   */
  private volatile boolean myIsTrackersActivated = false;

  /**
   * Indicates that view is visible in tool window
   */
  private boolean myIsActive;

  public ClassesFilteredView(@NotNull XDebugSession debugSession) {
    super();

    myProject = debugSession.getProject();
    myDebugSession = debugSession;
    myDebugProcess = (DebugProcessImpl) DebuggerManager.getInstance(myProject)
        .getDebugProcess(myDebugSession.getDebugProcess().getProcessHandler());
    myInstancesTracker = InstancesTracker.getInstance(myProject);
    InstancesTrackerListener instancesTrackerListener = new InstancesTrackerListener() {
      @Override
      public void classChanged(@NotNull String name, @NotNull TrackingType type) {
        ReferenceType ref = myTable.getClassByName(name);
        if (ref != null) {
          myDebugProcess.getManagerThread()
              .schedule(new LowestPriorityCommand(getSuspendContext()) {
                @Override
                public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
                  trackClass(ref, type, myIsTrackersActivated);
                }
              });
        }
        myTable.repaint();
      }

      @Override
      public void classRemoved(@NotNull String name) {
        ReferenceType ref = myTable.getClassByName(name);
        if (ref != null && myConstructorTrackedClasses.containsKey(ref)) {
          ConstructorInstancesTracker removed = myConstructorTrackedClasses.remove(ref);
          Disposer.dispose(removed);
          myTable.getRowSorter().allRowsChanged();
        }
      }
    };

    myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      public Priority getPriority() {
        return Priority.LOWEST;
      }

      @Override
      protected void action() throws Exception {
        for (Map.Entry<String, TrackingType> entry : myInstancesTracker.getTrackedClasses().entrySet()) {
          TrackingType type = entry.getValue();
          String className = entry.getKey();
          List<ReferenceType> classes = myDebugProcess.getVirtualMachineProxy().classesByName(className);
          if (classes.isEmpty()) {
            new ClassPreparedListener(className, myDebugSession) {
              @Override
              public void onClassPrepared(@NotNull ReferenceType referenceType) {
                trackClass(referenceType, type, myIsTrackersActivated);
              }
            };
          } else {
            for (ReferenceType ref : classes) {
              trackClass(ref, type, myIsTrackersActivated);
            }
          }
        }

        myInstancesTracker.addTrackerListener(instancesTrackerListener, ClassesFilteredView.this);
      }
    });

    MemoryViewManagerState memoryViewManagerState = MemoryViewManager.getInstance().getState();

    myTable = new ClassesTable(myDebugSession, memoryViewManagerState.isShowWithDiffOnly,
        memoryViewManagerState.isShowWithInstancesOnly, memoryViewManagerState.isShowTrackedOnly, this);
    myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_RUNNING);
    Disposer.register(this, myTable);

    myTable.addMouseMotionListener(new MyMouseMotionListener());
    myTable.addMouseListener(new MyOpenNewInstancesListener());
    new MyDoubleClickListener().installOn(myTable);

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

    final MemoryViewManagerListener memoryViewManagerListener = state -> {
      myTable.setFilteringByDiffNonZero(state.isShowWithDiffOnly);
      myTable.setFilteringByInstanceExists(state.isShowWithInstancesOnly);
      myTable.setFilteringByTrackingState(state.isShowTrackedOnly);
    };

    MemoryViewManager.getInstance().addMemoryViewManagerListener(memoryViewManagerListener, this);

    myDebugSessionListener = new XDebugSessionListener() {
      @Override
      public void sessionResumed() {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::obsolete);
        SwingUtilities.invokeLater(() -> {
          myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_RUNNING);
          myTable.hideContent();
        });
        mySingleAlarm.cancelAllRequests();
      }

      @Override
      public void sessionStopped() {
        debugSession.removeSessionListener(this);
      }

      @Override
      public void sessionPaused() {
        SwingUtilities.invokeLater(() -> myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_SUSPENDED));
        updateClassesAndCounts();
      }
    };

    mySingleAlarm = new SingleAlarmWithMutableDelay(() -> {
      myLastSuspendContext = getSuspendContext();
      if (myLastSuspendContext != null) {
        SwingUtilities.invokeLater(() -> myTable.setBusy(true));
        myDebugProcess.getManagerThread()
            .schedule(new MyUpdateClassesCommand(myLastSuspendContext));
      }
    }, this);

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

  @Nullable
  TrackerForNewInstances getStrategy(@NotNull ReferenceType ref) {
    return myConstructorTrackedClasses.getOrDefault(ref, null);
  }

  private void trackClass(@NotNull ReferenceType ref,
                          @NotNull TrackingType type,
                          boolean isTrackerEnabled) {
    LOG.assertTrue(DebuggerManager.getInstance(myProject).isDebuggerManagerThread());
    if (type == TrackingType.CREATION) {
      ConstructorInstancesTracker old = myConstructorTrackedClasses.getOrDefault(ref, null);
      if (old != null) {
        Disposer.dispose(old);
      }

      ConstructorInstancesTracker tracker = new ConstructorInstancesTracker(ref, myDebugSession);
      tracker.setBackgroundMode(!myIsActive);
      if (isTrackerEnabled) {
        tracker.enable();
      } else {
        tracker.disable();
      }

      Disposer.register(ClassesFilteredView.this, tracker);
      myConstructorTrackedClasses.put(ref, tracker);
    }
  }

  private void handleClassSelection(@Nullable ReferenceType ref) {
    if (ref != null && myDebugSession.isSuspended()) {
      new InstancesWindow(myDebugSession, ref::instances, ref.name()).show();
    }
  }

  private void commitAllTrackers() {
    myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::commitTracked);
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

  @Override
  public void dispose() {
  }

  public void setActive(boolean active) {
    if (myIsActive == active) {
      return;
    }

    myIsActive = active;
    myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        if (active) {
          doActivate();
        } else {
          doPause();
        }
      }
    });
  }

  private void doActivate() {
    myDebugSession.addSessionListener(myDebugSessionListener, ClassesFilteredView.this);
    myConstructorTrackedClasses.values().forEach(x -> x.setBackgroundMode(false));
    if (myLastSuspendContext == null || !myLastSuspendContext.equals(getSuspendContext())) {
      updateClassesAndCounts();
    }
  }

  private void doPause() {
    myDebugSession.removeSessionListener(myDebugSessionListener);
    myConstructorTrackedClasses.values().forEach(x -> x.setBackgroundMode(true));
  }

  private final class MyUpdateClassesCommand extends LowestPriorityCommand {

    MyUpdateClassesCommand(@Nullable SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
      if (!myIsTrackersActivated) {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::enable);
        myIsTrackersActivated = true;
      } else {
        commitAllTrackers();
      }
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
           begin != size && isContextValid();
           begin = end, end = Math.min(end + batchSize, size)) {
        List<ReferenceType> batch = classes.subList(begin, end);

        long start = System.nanoTime();
        long[] counts = vm.instanceCounts(batch);
        long delay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        chunks.add(counts);

        int waitTime = (int) Math.min(DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT * delay, MAX_DELAY_MILLIS);
        mySingleAlarm.setDelay(waitTime);
        LOG.info(String.format("Instances query time = %d ms. Count = %d", delay, batch.size()));
      }

      if (isContextValid()) {
        final long[] counts = chunks.size() == 1 ? chunks.get(0) : IntStream.range(0, chunks.size()).boxed()
            .flatMapToLong(integer -> Arrays.stream(chunks.get(integer)))
            .toArray();
        SwingUtilities.invokeLater(() -> myTable.setClassesAndUpdateCounts(classes, counts));
      }

      SwingUtilities.invokeLater(() -> myTable.setBusy(false));
    }

    private boolean isContextValid() {
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

  private class MyOpenNewInstancesListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() != 1 || e.getButton() != MouseEvent.BUTTON1 || !isShowNewInstancesEvent(e)) {
        return;
      }

      final CreationPositionTracker tracker = CreationPositionTracker.getInstance(myDebugSession.getProject());
      ReferenceType ref = myTable.getSelectedClass();
      TrackerForNewInstances strategy = ref == null ? null : getStrategy(ref);
      if (strategy != null && tracker != null) {
        List<ObjectReference> newInstances = strategy.getNewInstances();
        tracker.pinStacks(myDebugSession, ref);
        InstancesWindow instancesWindow = new InstancesWindow(myDebugSession, limit -> newInstances, ref.name());
        Disposer.register(instancesWindow.getDisposable(), () -> tracker.unpinStacks(myDebugSession, ref));
        instancesWindow.show();
      }
    }
  }

  private class MyDoubleClickListener extends DoubleClickListener {
    @Override
    protected boolean onDoubleClick(MouseEvent event) {
      if (!isShowNewInstancesEvent(event)) {
        handleClassSelection(myTable.getSelectedClass());
        return true;
      }

      return false;
    }
  }

  private class MyMouseMotionListener implements MouseMotionListener {
    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isShowNewInstancesEvent(e)) {
        myTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      } else {
        myTable.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
  }

  private boolean isShowNewInstancesEvent(@NotNull MouseEvent e) {
    final int col = myTable.columnAtPoint(e.getPoint());
    final int row = myTable.rowAtPoint(e.getPoint());
    if (col == -1 || row == -1 || myTable.convertColumnIndexToModel(col) != DIFF_COLUMN_INDEX) {
      return false;
    }

    final int modelRow = myTable.convertRowIndexToModel(row);

    final ReferenceType ref = (ReferenceType) myTable.getModel().getValueAt(modelRow, CLASSNAME_COLUMN_INDEX);
    final ConstructorInstancesTracker tracker = myConstructorTrackedClasses.getOrDefault(ref, null);

    return tracker != null && tracker.isReady() && tracker.getCount() > 0;
  }
}
