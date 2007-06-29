package com.intellij.openapi.application.impl;

import com.intellij.CommonBundle;
import com.intellij.Patches;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.HackyRepaintManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.impl.ApplicationPathMacroManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.stores.IApplicationStore;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.StoresFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class ApplicationImpl extends ComponentManagerImpl implements ApplicationEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.impl.ApplicationImpl");
  private ModalityState MODALITY_STATE_NONE;

  private final List<ApplicationListener> myListeners = new CopyOnWriteArrayList<ApplicationListener>();

  private boolean myTestModeFlag = false;
  private boolean myHeadlessMode = false;

  private String myComponentsDescriptor;

  private boolean myIsInternal = false;
  @NonNls private static final String APPLICATION_LAYER = "application-components";
  private String myName;

  private ReentrantWriterPreferenceReadWriteLock myActionsLock = new ReentrantWriterPreferenceReadWriteLock();
  private final Stack<Runnable> myWriteActionsStack = new Stack<Runnable>();

  private Thread myExceptionalThreadWithReadAccess = null;
  private Runnable myExceptionalThreadWithReadAccessRunnable;

  private int myInEditorPaintCounter = 0;
  private long myStartTime = 0;
  private boolean myDoNotSave = false;
  private boolean myIsWaitingForWriteAction = false;
  @NonNls private static final String NULL_STR = "null";

  private final ExecutorService ourThreadExecutorsService = new ThreadPoolExecutor(
    3,
    Integer.MAX_VALUE,
    30 * 60L,
    TimeUnit.SECONDS,
    new SynchronousQueue<Runnable>(),
    new ThreadFactory() {
      public Thread newThread(Runnable r) {
        return new Thread(r, "ApplicationImpl pooled thread")
        {
          public void interrupt() {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Interrupted worker, will remove from pool");
            }
            super.interrupt();
          }

          public void run() {
            try {
              super.run();
            } catch(Throwable t) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Worker exits due to exception", t);
              }
            }
          }
        };
      }
    }
  );


  protected void boostrapPicoContainer() {
    super.boostrapPicoContainer();
    getPicoContainer().registerComponentImplementation(IComponentStore.class, StoresFactory.getApplicationStoreClass());
    getPicoContainer().registerComponentImplementation(ApplicationPathMacroManager.class);
  }

  @Override
  @NotNull
  public synchronized IApplicationStore getStateStore() {
    return (IApplicationStore)super.getStateStore();
  }

  public ApplicationImpl(String componentsDescriptor, boolean isInternal, boolean isUnitTestMode, boolean isHeadless, String appName) {
    super(null);

    if (isInternal || isUnitTestMode) {
      Disposer.setDebugMode(true);
    }
    myStartTime = System.currentTimeMillis();
    myName = appName;
    ApplicationManagerEx.setApplication(this);

    PluginsFacade.INSTANCE = new PluginsFacade() {
      public IdeaPluginDescriptor getPlugin(PluginId id) {
        return PluginManager.getPlugin(id);
      }

      public IdeaPluginDescriptor[] getPlugins() {
        return PluginManager.getPlugins();
      }
    };

    if (!isUnitTestMode && !isHeadless) {
      Toolkit.getDefaultToolkit().getSystemEventQueue().push(IdeEventQueue.getInstance());
      if (Patches.SUN_BUG_ID_6209673) {
        RepaintManager.setCurrentManager(new HackyRepaintManager());
      }
      IconLoader.activate();
    }

    myComponentsDescriptor = componentsDescriptor;
    myIsInternal = isInternal;
    myTestModeFlag = isUnitTestMode;
    myHeadlessMode = isHeadless;

    loadApplicationComponents();

    if (SystemInfo.isMac || myTestModeFlag) {
      registerShutdownHook();
    }

    if (!isUnitTestMode && !isHeadless) {
      Disposer.register(this, new Disposable() {
        public void dispose() {
        }
      }, "ui");
    }
  }

  private void registerShutdownHook() {
    ShutDownTracker.getInstance(); // Necessary to avoid creating an instance while already shutting down.

    ShutDownTracker.getInstance().registerShutdownThread(new Thread(new Runnable() {
      public void run() {
        try {
          SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
              saveAll();
              disposeSelf();
            }
          });
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        catch (InvocationTargetException e) {
          LOG.error(e);
        }
      }
    }));
  }

  private void disposeSelf() {
    Disposer.dispose(this);
    Disposer.assertIsEmpty();
  }

  public String getComponentsDescriptor() {
    return myComponentsDescriptor;
  }

  public String getName() {
    return myName;
  }

  @Override
  protected void handleInitComponentError(final Throwable ex, final boolean fatal, final String componentClassName) {
    if (PluginManager.isPluginClass(componentClassName)) {
      LOG.error(ex);
      PluginId pluginId = PluginManager.getPluginByClassName(componentClassName);
      @NonNls final String errorMessage = "Plugin " + pluginId.getIdString() + " failed to initialize:\n" + ex.getMessage() +
                                          "\nPlease remove the plugin and restart " + ApplicationNamesInfo.getInstance().getFullProductName() + ".";
      if (!myHeadlessMode) {
        JOptionPane.showMessageDialog(null, errorMessage);
      }
      else {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println(errorMessage);
      }
      System.exit(1);
    }
    else if (fatal) {
      LOG.error(ex);
      @NonNls final String errorMessage = "Fatal error initializing class " + componentClassName + ":\n" +
                                          ex.toString() +
                                          "\nComplete error stacktrace was written to idea.log";
      if (!myHeadlessMode) {
        JOptionPane.showMessageDialog(null, errorMessage);
      }
      else {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println(errorMessage);
      }
    }
    super.handleInitComponentError(ex, fatal, componentClassName);
  }

  private void loadApplicationComponents() {
    loadComponentsConfiguration(APPLICATION_LAYER, true);

    final IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManager.shouldSkipPlugin(plugin)) continue;
      loadComponentsConfiguration(plugin.getAppComponents(), plugin, true);
    }
  }

  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getRootArea().getPicoContainer();
  }

  public boolean isInternal() {
    return myIsInternal;
  }

  public boolean isUnitTestMode() {
    return myTestModeFlag;
  }

  public boolean isHeadlessEnvironment() {
    return myHeadlessMode;
  }

  public IdeaPluginDescriptor getPlugin(PluginId id) {
    return PluginsFacade.INSTANCE.getPlugin(id);
  }

  public IdeaPluginDescriptor[] getPlugins() {
    return PluginsFacade.INSTANCE.getPlugins();
  }

  public Future<?> executeOnPooledThread(final Runnable action) {
    return ourThreadExecutorsService.submit(new Runnable() {
      public void run() {
        try {
          action.run();
        }
        catch (Throwable t) {
          LOG.error(t);
        }
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }
    });
  }

  private static Thread ourDispatchThread = null;

  public boolean isDispatchThread() {
    return EventQueue.isDispatchThread();
  }


  public void load(String path) throws IOException, InvalidDataException {
    getStateStore().setConfigPath(path);
    try {
      getStateStore().load();
    }
    catch (StateStorage.StateStorageException e) {
      throw new IOException(e.getMessage());
    }
  }


  public void dispose() {
    Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
    final boolean[] canClose = new boolean[]{true};
    for (final Project project : openProjects) {
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.executeCommand(project, new Runnable() {
        public void run() {
          FileDocumentManager.getInstance().saveAllDocuments();
          canClose[0] = ProjectUtil.closeProject(project);
        }
      }, ApplicationBundle.message("command.exit"), null);
      if (!canClose[0]) break;
    }

    if (canClose[0]) {
      fireApplicationExiting();
      disposeComponents();
    }

    ourThreadExecutorsService.shutdownNow();
    super.dispose();
  }

  public boolean runProcessWithProgressSynchronously(final Runnable process, String progressTitle, boolean canBeCanceled, Project project) {
    assertIsDispatchThread();

    if (myExceptionalThreadWithReadAccess != null ||
        myExceptionalThreadWithReadAccessRunnable != null ||
        ApplicationManager.getApplication().isUnitTestMode() ||
        ApplicationManager.getApplication().isHeadlessEnvironment()) {
      process.run();
      return true;
    }

    final ProgressWindow progress = new ProgressWindow(canBeCanceled, project);
    progress.setTitle(progressTitle);

    try {
      myExceptionalThreadWithReadAccessRunnable = process;
      final boolean[] threadStarted = new boolean[]{false};
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myExceptionalThreadWithReadAccessRunnable != process) {
              LOG.error("myExceptionalThreadWithReadAccessRunnable != process, process = " + myExceptionalThreadWithReadAccessRunnable);
          }

          executeOnPooledThread(new Runnable() {
            public void run() {
              if (myExceptionalThreadWithReadAccessRunnable != process) {
                LOG.error("myExceptionalThreadWithReadAccessRunnable != process, process = " + myExceptionalThreadWithReadAccessRunnable);
              }

              myExceptionalThreadWithReadAccess = Thread.currentThread();
              boolean old = setExceptionalThreadWithReadAccessFlag(true);
              LOG.assertTrue(isReadAccessAllowed());
              try {
                ProgressManager.getInstance().runProcess(process, progress);
              }
              catch (ProcessCanceledException e) {
                // ok to ignore.
              }
              finally {
                setExceptionalThreadWithReadAccessFlag(old);
              }
            }
          });
          threadStarted[0] = true;
        }
      });

      progress.startBlocking();
      LOG.assertTrue(threadStarted[0]);
      LOG.assertTrue(!progress.isRunning());
    }
    finally {
      myExceptionalThreadWithReadAccess = null;
      myExceptionalThreadWithReadAccessRunnable = null;
    }

    return !progress.isCanceled();
  }

  public <T> List<Future<T>> invokeAllUnderReadAction(@NotNull Collection<Callable<T>> tasks, final ExecutorService executorService) throws
                                                                                                                               Throwable {
    final List<Callable<T>> newCallables = new ArrayList<Callable<T>>(tasks.size());
    for (final Callable<T> task : tasks) {
      Callable<T> newCallable = new Callable<T>() {
        public T call() throws Exception {
          boolean old = setExceptionalThreadWithReadAccessFlag(true);
          try {
            LOG.assertTrue(isReadAccessAllowed());
            return task.call();
          }
          finally {
            setExceptionalThreadWithReadAccessFlag(old);
          }
        }
      };
      newCallables.add(newCallable);
    }
    final Ref<Throwable> exception = new Ref<Throwable>();
    List<Future<T>> result = runReadAction(new Computable<List<Future<T>>>() {
      public List<Future<T>> compute() {
        try {
          return ConcurrencyUtil.invokeAll(newCallables, executorService);
        }
        catch (Throwable throwable) {
          exception.set(throwable);
          return null;
        }
      }
    });
    if (exception.get() != null) throw exception.get();
    return result;
  }

  public void invokeLater(Runnable runnable) {
    LaterInvocator.invokeLater(runnable);
  }

  public void invokeLater(Runnable runnable, @NotNull ModalityState state) {
    LaterInvocator.invokeLater(runnable, state);
  }

  public void invokeAndWait(Runnable runnable, @NotNull ModalityState modalityState) {
    if (isDispatchThread()) {
      LOG.error("invokeAndWait should not be called from event queue thread");
      runnable.run();
      return;
    }

    Thread currentThread = Thread.currentThread();
    if (myExceptionalThreadWithReadAccess == currentThread) { //OK if we're in exceptional thread.
      LaterInvocator.invokeAndWait(runnable, modalityState);
      return;
    }

    if (myActionsLock.isReadLockAcquired(currentThread)) {
      final Throwable stack = new Throwable();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          LOG.error("Calling invokeAndWait from read-action leads to possible deadlock.", stack);
        }
      });
      if (myIsWaitingForWriteAction) return; // The deadlock indeed. Do not perform request or we'll stall here immediately.
    }

    LaterInvocator.invokeAndWait(runnable, modalityState);
  }

  public ModalityState getCurrentModalityState() {
    Object[] entities = LaterInvocator.getCurrentModalEntities();
    return entities.length > 0 ? new ModalityStateEx(entities) : getNoneModalityState();
  }

  public ModalityState getModalityStateForComponent(Component c) {
    Window window = c instanceof Window ? (Window)c : SwingUtilities.windowForComponent(c);
    if (window == null) return getNoneModalityState(); //?
    return LaterInvocator.modalityStateForWindow(window);
  }

  public ModalityState getDefaultModalityState() {
    if (EventQueue.isDispatchThread()) {
      return getCurrentModalityState();
    }
    else {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        return progress.getModalityState();
      }
      else {
        return getNoneModalityState();
      }
    }
  }

  public ModalityState getNoneModalityState() {
    if (MODALITY_STATE_NONE == null) {
      MODALITY_STATE_NONE = new ModalityStateEx(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    return MODALITY_STATE_NONE;
  }

  public long getStartTime() {
    return myStartTime;
  }

  public long getIdleTime() {
    return IdeEventQueue.getInstance().getIdleTime();
  }

  public void exit() {
    exit(false);
  }

  public void exit(final boolean force) {
    if (SystemInfo.isMac) {
      if (!force) {
        if (!showConfirmation()) return;
      }
      if (!canExit()) return;
      new Thread(new Runnable() {
        public void run() {
          System.exit(0);
        }
      }).start();
    }
    else {
      Runnable runnable = new Runnable() {
        public void run() {
          if (!force) {
            if (!showConfirmation()) {
              saveAll();
              return;
            }
          }
          saveAll();
          if (!canExit()) return;

          disposeSelf();

          System.exit(0);
        }
      };
      if (!isDispatchThread()) {
        invokeLater(runnable, ModalityState.NON_MODAL);
      }
      else {
        runnable.run();
      }
    }
  }

  private static boolean showConfirmation() {
    if (GeneralSettings.getInstance().isConfirmExit()) {
      final ConfirmExitDialog confirmExitDialog = new ConfirmExitDialog();
      confirmExitDialog.show();
      if (!confirmExitDialog.isOK()) {
        return false;
      }
    }
    return true;
  }

  private boolean canExit() {
    for (ApplicationListener applicationListener : myListeners) {
      if (!applicationListener.canExitApplication()) {
        return false;
      }
    }

    ProjectManagerEx projectManager = (ProjectManagerEx)ProjectManager.getInstance();
    Project[] projects = projectManager.getOpenProjects();
    for (Project project : projects) {
      if (!projectManager.canClose(project)) {
        return false;
      }
    }

    return true;
  }

  public void runReadAction(final Runnable action) {
    /** if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
     * see {@link com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock#allowReader()} */
    boolean mustAcquire = !isReadAccessAllowed();

    if (mustAcquire) {
      LOG.assertTrue(!Thread.holdsLock(PsiLock.LOCK), "Thread must not hold PsiLock while performing readAction");
      try {
        myActionsLock.readLock().acquire();
      }
      catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    try {
      action.run();
    }
    finally {
      if (mustAcquire) {
        myActionsLock.readLock().release();
      }
    }
  }

  private static final ThreadLocal<Boolean> exceptionalThreadWithReadAccessFlag = new ThreadLocal<Boolean>();
  private static boolean isExceptionalThreadWithReadAccess() {
    Boolean flag = exceptionalThreadWithReadAccessFlag.get();
    return flag != null && flag.booleanValue();
  }

  public static boolean setExceptionalThreadWithReadAccessFlag(boolean flag) {
    boolean old = isExceptionalThreadWithReadAccess();
    if (flag) {
      exceptionalThreadWithReadAccessFlag.set(true);
    }
    else {
      exceptionalThreadWithReadAccessFlag.remove();
    }
    return old;
  }

  public <T> T runReadAction(final Computable<T> computation) {
    final Ref<T> ref = Ref.create(null);
    runReadAction(new Runnable() {
      public void run() {
        ref.set(computation.compute());
      }
    });
    return ref.get();
  }

  public void runWriteAction(final Runnable action) {
    assertCanRunWriteAction();
    fireBeforeWriteActionStart(action);

    LOG.assertTrue(myActionsLock.isWriteLockAcquired(Thread.currentThread()) || !Thread.holdsLock(PsiLock.LOCK), "Thread must not hold PsiLock while performing writeAction");
    myIsWaitingForWriteAction = true;
    try {
      myActionsLock.writeLock().acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
    finally {
      myIsWaitingForWriteAction = false;
    }

    fireWriteActionStarted(action);

    try {
      synchronized (myWriteActionsStack) {
        myWriteActionsStack.push(action);
      }
      final Project project = CommandProcessor.getInstance().getCurrentCommandProject();
      if(project != null/* && !(action instanceof PsiExternalChangeAction)*/) {
        // run postprocess formatting inside commands only
        PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Computable<Object>() {
          public Object compute() {
            action.run();
            return null;
          }
        });
      }
      else action.run();
    }
    finally {
      synchronized (myWriteActionsStack) {
        myWriteActionsStack.pop();
      }
      fireWriteActionFinished(action);
      myActionsLock.writeLock().release();
    }

  }

  public <T> T runWriteAction(final Computable<T> computation) {
    final Ref<T> ref = Ref.create(null);
    runWriteAction(new Runnable() {
      public void run() {
        ref.set(computation.compute());
      }
    });
    return ref.get();
  }

  public Object getCurrentWriteAction(Class actionClass) {
    synchronized (myWriteActionsStack) {
      for (int i = myWriteActionsStack.size() - 1; i >= 0; i--) {
        Runnable action = myWriteActionsStack.get(i);
        if (actionClass == null || ReflectionCache.isAssignable(actionClass, action.getClass())) return action;
      }
    }
    return null;
  }

  public void assertReadAccessAllowed() {
    if (myTestModeFlag || myHeadlessMode) return;
    if (!isReadAccessAllowed()) {
      LOG.error(
        "Read access is allowed from event dispatch thread or inside read-action only (see com.intellij.openapi.application.Application.runReadAction())",
        "Current thread: " + describe(Thread.currentThread()), "Our dispatch thread:" + describe(ourDispatchThread),
        "SystemEventQueueThread: " + describe(getEventQueueThread()));
    }
  }

  private static String describe(Thread o) {
    if (o == null) return NULL_STR;
    return o.toString() + " " + System.identityHashCode(o);
  }

  @Nullable
  private static Thread getEventQueueThread() {
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    try {
      Method method = EventQueue.class.getDeclaredMethod("getDispatchThread");
      method.setAccessible(true);
      return (Thread)method.invoke(eventQueue);
    }
    catch (Exception e1) {
      // ok
    }
    return null;
  }

  public boolean isReadAccessAllowed() {
    Thread currentThread = Thread.currentThread();
    if (ourDispatchThread == currentThread) {
      //TODO!
      /*
      IdeEventQueue eventQueue = IdeEventQueue.getInstance(); //TODO: cache?
      if (!eventQueue.isInInputEvent() && !LaterInvocator.isInMyRunnable() && !myInEditorPaint) {
        LOG.error("Read access from event dispatch thread is allowed only inside input event processing or LaterInvocator.invokeLater");
      }
      */

      return true;
    }
    if (isExceptionalThreadWithReadAccess()) return true;
    if (myActionsLock.isReadLockAcquired(currentThread)) return true;
    if (myActionsLock.isWriteLockAcquired(currentThread)) return true;
    return isDispatchThread();
  }

  public void assertReadAccessToDocumentsAllowed() {
    /* TODO
    Thread currentThread = Thread.currentThread();
    if (ourDispatchThread != currentThread) {
      if (myExceptionalThreadWithReadAccess == currentThread) return;
      if (myActionsLock.isReadLockAcquired(currentThread)) return;
      if (myActionsLock.isWriteLockAcquired(currentThread)) return;
      if (isDispatchThread(currentThread)) return;
      LOG.error(
        "Read access is allowed from event dispatch thread or inside read-action only (see com.intellij.openapi.application.Application.runReadAction())");
    }
    */
  }

  private void assertCanRunWriteAction() {
    assertIsDispatchThread("Write access is allowed from event dispatch thread only");

  }

  public void assertIsDispatchThread() {
    assertIsDispatchThread("Access is allowed from event dispatch thread only.");
  }

  private void assertIsDispatchThread(String message) {
    if (myTestModeFlag || myHeadlessMode) return;
    final Thread currentThread = Thread.currentThread();
    if (ourDispatchThread == currentThread) return;

    if (EventQueue.isDispatchThread()) {
      ourDispatchThread = currentThread;
    }
    if (ourDispatchThread == currentThread) return;

    LOG.error(message,
              "Current thread: " + describe(Thread.currentThread()),
              "Our dispatch thread:" + describe(ourDispatchThread),
              "SystemEventQueueThread: " + describe(getEventQueueThread()));
  }

  public void assertWriteAccessAllowed() {
    LOG.assertTrue(isWriteAccessAllowed(),
                   "Write access is allowed inside write-action only (see com.intellij.openapi.application.Application.runWriteAction())");
  }

  public boolean isWriteAccessAllowed() {
    return myActionsLock.isWriteLockAcquired(Thread.currentThread());
  }

  public void editorPaintStart() {
    myInEditorPaintCounter++;
  }

  public void editorPaintFinish() {
    myInEditorPaintCounter--;
    LOG.assertTrue(myInEditorPaintCounter >= 0);
  }

  public void addApplicationListener(ApplicationListener l) {
    myListeners.add(l);
  }

  public void removeApplicationListener(ApplicationListener l) {
    myListeners.remove(l);
  }

  private void fireApplicationExiting() {
    for (ApplicationListener applicationListener : myListeners) {
      applicationListener.applicationExiting();
    }
  }

  private void fireBeforeWriteActionStart(Runnable action) {
    for (ApplicationListener listener : myListeners) {
      listener.beforeWriteActionStart(action);
    }
  }

  private void fireWriteActionStarted(Runnable action) {
    for (ApplicationListener listener : myListeners) {
      listener.writeActionStarted(action);
    }
  }

  private void fireWriteActionFinished(Runnable action) {
    for (ApplicationListener listener : myListeners) {
      listener.writeActionFinished(action);
    }
  }


  public void saveSettings() {
    if (myDoNotSave) return;

    try {
      doSave();
    }
    catch (final Throwable ex) {
      LOG.info("Saving application settings failed", ex);
      invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog(ApplicationBundle.message("application.save.settings.error", ex.getLocalizedMessage()),
                                     CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        }
      });
    }
  }

  public void saveAll() {
    if (myDoNotSave || isUnitTestMode() || isHeadlessEnvironment()) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      ProjectEx project = (ProjectEx)openProject;
      project.save();
    }

    saveSettings();
  }

  public void doNotSave() {
    myDoNotSave = true;
  }


  public boolean isDoNotSave() {
    return myDoNotSave;
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }
}
