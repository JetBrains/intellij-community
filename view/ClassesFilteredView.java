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
import org.jetbrains.debugger.memory.component.InstancesTracker;
import org.jetbrains.debugger.memory.component.MemoryViewManager;
import org.jetbrains.debugger.memory.component.MemoryViewManagerState;
import org.jetbrains.debugger.memory.event.InstancesTrackerListener;
import org.jetbrains.debugger.memory.event.MemoryViewManagerListener;
import org.jetbrains.debugger.memory.tracking.ConstructorInstancesTracker;
import org.jetbrains.debugger.memory.tracking.InstanceTrackingStrategy;
import org.jetbrains.debugger.memory.tracking.TrackerForNewInstances;
import org.jetbrains.debugger.memory.tracking.TrackingType;
import org.jetbrains.debugger.memory.utils.AndroidUtil;
import org.jetbrains.debugger.memory.utils.KeyboardUtils;
import org.jetbrains.debugger.memory.utils.LowestPriorityCommand;
import org.jetbrains.debugger.memory.utils.SingleAlarmWithMutableDelay;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class ClassesFilteredView extends BorderLayoutPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  private final static double DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT = 0.5;
  private final static int DEFAULT_BATCH_SIZE = Integer.MAX_VALUE;

  private final Project myProject;
  private final XDebugSession myDebugSession;
  private final DebugProcessImpl myDebugProcess;
  private final SingleAlarmWithMutableDelay mySingleAlarm;

  private final SearchTextField myFilterTextField = new SearchTextField(false);
  private final ClassesTable myTable;
  private final InstancesTracker myInstancesTracker;
  private final Map<ReferenceType, InstanceTrackingStrategy> myTrackedClasses = new ConcurrentHashMap<>();
  private final Map<ReferenceType, ConstructorInstancesTracker> myConstructorTrackedClasses = new ConcurrentHashMap<>();

  private volatile SuspendContextImpl myLastSuspendContext;
  private volatile boolean myNeedReloadClasses = false;

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
                  trackClass(ref, type, suspendContext);
                }
              });
        }
      }

      @Override
      public void classRemoved(@NotNull String name) {
        myTrackedClasses.keySet().removeIf(referenceType -> name.equals(referenceType.name()));
        myTable.getRowSorter().allRowsChanged();
      }
    };

    myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        List<String> constructorTrackerClasses = myInstancesTracker.getClassesByTrackingType(TrackingType.CREATION);
        myInstancesTracker.addTrackerListener(instancesTrackerListener, ClassesFilteredView.this);
        for (String className : constructorTrackerClasses) {
          List<ReferenceType> classes = myDebugProcess.getVirtualMachineProxy().classesByName(className);
          if (classes.isEmpty()) {
            // TODO: track class preparation
          } else {
            // TODO: foreach add breakpoint
          }
        }
      }
    });

    MemoryViewManagerState memoryViewManagerState = MemoryViewManager.getInstance().getState();

    myTable = new ClassesTable(myDebugSession, memoryViewManagerState.isShowWithDiffOnly,
        memoryViewManagerState.isShowWithInstancesOnly, this);
    Disposer.register(this, myTable);

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

    MemoryViewManager.getInstance().addMemoryViewManagerListener(memoryViewManagerListener, this);

    myDebugSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionResumed() {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::obsolete);
        SwingUtilities.invokeLater(myTable::hideContent);
        mySingleAlarm.cancelAllRequests();
      }

      @Override
      public void sessionStopped() {
        debugSession.removeSessionListener(this);
      }

      @Override
      public void sessionPaused() {
        if (myNeedReloadClasses) {
          myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::commitTracked);
          updateClassesAndCounts();
        }
      }
    }, this);

    myFilterTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myTable.setFilterPattern(myFilterTextField.getText());
      }
    });

    mySingleAlarm = new SingleAlarmWithMutableDelay(() -> {
      myLastSuspendContext = getSuspendContext();
      if (myLastSuspendContext != null) {
        SwingUtilities.invokeLater(() -> {
          myTable.setBusy(true);
          myDebugProcess.getManagerThread()
              .schedule(new MyUpdateClassesCommand(myLastSuspendContext));
        });
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
    if (myTrackedClasses.containsKey(ref)) {
      return myTrackedClasses.get(ref);
    }

    return myConstructorTrackedClasses.getOrDefault(ref, null);
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

  private void trackClass(@NotNull ReferenceType ref,
                          @NotNull TrackingType type,
                          @Nullable SuspendContextImpl suspendContext) {
    LOG.assertTrue(DebuggerManager.getInstance(myProject).isDebuggerManagerThread());
    if (type == TrackingType.CREATION) {
      ConstructorInstancesTracker old = myConstructorTrackedClasses.getOrDefault(ref, null);
      if (old != null) {
        Disposer.dispose(old);
      }

      ConstructorInstancesTracker tracker = new ConstructorInstancesTracker(ref, myDebugSession);
      Disposer.register(ClassesFilteredView.this, tracker);
      myConstructorTrackedClasses.put(ref, tracker);
    } else {
      List<ObjectReference> instances = ref.instances(0);
      myTrackedClasses.put(ref, InstanceTrackingStrategy.create(ref, suspendContext, type, instances));
    }
  }

  private void handleClassSelection(@Nullable ReferenceType ref) {
    if (ref != null && myDebugSession.isSuspended()) {
      new InstancesWindow(myDebugSession, ref::instances, ref.name()).show();
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

  boolean isTrackingActive(@NotNull ReferenceType ref) {
    TrackerForNewInstances strategy = myConstructorTrackedClasses.getOrDefault(ref, null);
    if (strategy != null) {
      return strategy.isReady();
    }

    strategy = myTrackedClasses.getOrDefault(ref, null);
    return strategy != null && strategy.isReady();

  }

  @Override
  public void dispose() {
  }

  private final class MyUpdateClassesCommand extends LowestPriorityCommand {

    MyUpdateClassesCommand(@Nullable SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
      final List<ReferenceType> classes = myDebugProcess.getVirtualMachineProxy().allClasses();

      for (Map.Entry<ReferenceType, InstanceTrackingStrategy> entry : myTrackedClasses.entrySet()) {
        entry.getValue().update(suspendContext, entry.getKey().instances(0));
      }

      for (ReferenceType ref : classes) {
        TrackingType type = myInstancesTracker.getTrackingType(ref.name());
        if (type != null && !myTrackedClasses.containsKey(ref) && !myConstructorTrackedClasses.containsKey(ref)) {
          trackClass(ref, type, suspendContext);
        }
      }

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

    private boolean contextIsValid() {
      return ClassesFilteredView.this.getSuspendContext() == getSuspendContext();
    }
  }
}
