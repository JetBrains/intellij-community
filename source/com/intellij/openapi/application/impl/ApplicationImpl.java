package com.intellij.openapi.application.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AreaPicoContainer;
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
import com.intellij.openapi.project.impl.convertors.Convertor34;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class ApplicationImpl extends ComponentManagerImpl implements ApplicationEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.impl.ApplicationImpl");
  private ModalityState MODALITY_STATE_NONE;

  private final List<ApplicationListener> myListeners = new CopyOnWriteArrayList<ApplicationListener>();

  private boolean myTestModeFlag = false;
  private boolean myHeadlessMode = false;

  private String myComponentsDescriptor;

  private boolean myIsInternal = false;
  private static boolean ourSaveSettingsInProgress = false;
  @NonNls private static final String APPLICATION_LAYER = "application-components";
  private String myName;

  private ReentrantWriterPreferenceReadWriteLock myActionsLock = new ReentrantWriterPreferenceReadWriteLock();
  private final Stack<Runnable> myWriteActionsStack = new Stack<Runnable>();

  private Thread myExceptionalThreadWithReadAccess = null;

  private int myInEditorPaintCounter = 0;
  private long myStartTime = 0;
  private boolean myDoNotSave = false;
  private boolean myIsWaitingForWriteAction = false;
  @NonNls private static final String APPLICATION_ELEMENT = "application";
  @NonNls private static final String ELEMENT_COMPONENT = "component";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";
  @NonNls private static final String NULL_STR = "null";
  @NonNls private static final String XML_EXTENSION = ".xml";

  public ApplicationImpl(String componentsDescriptor, boolean isInternal, boolean isUnitTestMode, boolean isHeadless, String appName) {
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

    if (!isUnitTestMode) {
      Toolkit.getDefaultToolkit().getSystemEventQueue().push(IdeEventQueue.getInstance());
      IconLoader.activate();
    }

    getPicoContainer().registerComponentInstance(ApplicationEx.class, this);

    myComponentsDescriptor = componentsDescriptor;
    myIsInternal = isInternal;
    myTestModeFlag = isUnitTestMode;
    myHeadlessMode = isHeadless;

    loadApplicationComponents();

    if (SystemInfo.isMac || myTestModeFlag) {
      registerShutdownHook();
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

  protected void initComponents() {
    initComponentsFromExtensions(Extensions.getRootArea());
    super.initComponents();
  }


  @Override
  protected void handleInitComponentError(final BaseComponent component, final Class componentClass, final Throwable ex) {
    if (PluginManager.isPluginClass(componentClass.getName())) {
      PluginId pluginId = PluginManager.getPluginByClassName(componentClass.getName());
      final String errorMessage = "Plugin " + pluginId.getIdString() + " failed to initialize:\n" + ex.getMessage() +
                                  "\nPlease remove the plugin and restart " + ApplicationNamesInfo.getInstance().getFullProductName() + ".";
      if (!GraphicsEnvironment.isHeadless()) {
        JOptionPane.showMessageDialog(null, errorMessage);
      }
      else {
        System.out.println(errorMessage);
      }
      System.exit(1);
    }
    super.handleInitComponentError(component, componentClass, ex);
  }

  private void loadApplicationComponents() {
    loadComponentsConfiguration(APPLICATION_LAYER, true);

    if (PluginManager.shouldLoadPlugins()) {
      final IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
      for (IdeaPluginDescriptor plugin : plugins) {
        if (!PluginManager.shouldLoadPlugin(plugin)) continue;
        final Element appComponents = plugin.getAppComponents();
        if (appComponents != null) {
          loadComponentsConfiguration(appComponents, plugin, true);
        }
      }
    }
  }

  protected MutablePicoContainer createPicoContainer() {
    final AreaPicoContainer picoContainer = Extensions.getRootArea().getPicoContainer();
    picoContainer.setComponentAdapterFactory(new MyComponentAdapterFactory());
    return picoContainer;
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

  private static Thread ourDispatchThread = null;

  public boolean isDispatchThread() {
    return EventQueue.isDispatchThread();
  }

  private void save(String path) throws IOException {
    deleteBackupFiles(path);
    backupFiles(path);

    Class[] componentClasses = getComponentInterfaces();

    HashMap<String, Element> fileNameToRootElementMap = new HashMap<String, Element>();

    for (Class<?> componentClass : componentClasses) {
      Object component = getComponent(componentClass);
      if (!(component instanceof BaseComponent)) continue;
      String fileName;
      if (component instanceof NamedJDOMExternalizable) {
        fileName = ((NamedJDOMExternalizable)component).getExternalFileName() + XML_EXTENSION;
      }
      else {
        fileName = PathManager.DEFAULT_OPTIONS_FILE_NAME + XML_EXTENSION;
      }

      Element root = getRootElement(fileNameToRootElementMap, fileName);
      try {
        Element node = serializeComponent((BaseComponent)component);
        if (node != null) {
          root.addContent(node);
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    for (String fileName : fileNameToRootElementMap.keySet()) {
      Element root = fileNameToRootElementMap.get(fileName);

      JDOMUtil.writeDocument(new Document(root), path + File.separatorChar + fileName,
                             CodeStyleSettingsManager.getSettings(null).getLineSeparator());
    }

    deleteBackupFiles(path);
  }

  private static void backupFiles(String path) throws IOException {
    String[] list = new File(path).list();
    for (String name : list) {
      if (name.toLowerCase().endsWith(XML_EXTENSION)) {
        File file = new File(path + File.separatorChar + name);
        File newFile = new File(path + File.separatorChar + name + "~");
        FileUtil.rename(file, newFile);
      }
    }
  }

  private static Element getRootElement(Map<String, Element> fileNameToRootElementMap, String fileName) {
    Element root = fileNameToRootElementMap.get(fileName);
    if (root == null) {
      root = new Element(APPLICATION_ELEMENT);
      fileNameToRootElementMap.put(fileName, root);
    }
    return root;
  }

  private static void deleteBackupFiles(String path) throws IOException {
    String[] list = new File(path).list();
    for (String name : list) {
      if (StringUtil.endsWithChar(name.toLowerCase(), '~')) {
        File file = new File(path + File.separatorChar + name);
        if (!file.delete()) {
          throw new IOException(ApplicationBundle.message("backup.cannot.delete.file", file.getPath()));
        }
      }
    }
  }

  public void load(String path) throws IOException, InvalidDataException {
    try {
      if (path == null) return;
      loadConfiguration(path);
    }
    finally {
      initComponents();
      clearDomMap();
    }
  }

  private void loadConfiguration(String path) {
    clearDomMap();

    File configurationDir = new File(path);
    if (!configurationDir.exists()) return;

    Set<String> names = new HashSet<String>(Arrays.asList(configurationDir.list()));

    for (Iterator<String> i = names.iterator(); i.hasNext();) {
      String name = i.next();
      if (name.endsWith(XML_EXTENSION)) {
        String backupName = name + "~";
        if (names.contains(backupName)) i.remove();
      }
    }

    for (String name : names) {
      if (!name.endsWith(XML_EXTENSION) && !name.endsWith(XML_EXTENSION + "~")) continue; // see SCR #12791
      final String filePath = path + File.separatorChar + name;
      File file = new File(filePath);
      if (!file.exists() || !file.isFile()) continue;

      try {
        loadFile(filePath);
      }
      catch (Exception e) {
        //OK here. Just drop corrupted settings.
      }
    }
  }

  private void loadFile(String filePath) throws JDOMException, InvalidDataException, IOException {
    Document document = JDOMUtil.loadDocument(new File(filePath));
    if (document == null) {
      throw new InvalidDataException();
    }

    Element root = document.getRootElement();
    if (root == null || !APPLICATION_ELEMENT.equals(root.getName())) {
      throw new InvalidDataException();
    }

    final List<String> additionalFiles = new ArrayList<String>();
    synchronized (this) {
      List children = root.getChildren(ELEMENT_COMPONENT);
      for (final Object aChildren : children) {
        Element element = (Element)aChildren;

        String name = element.getAttributeValue(ATTRIBUTE_NAME);
        if (name == null || name.length() == 0) {
          String className = element.getAttributeValue(ATTRIBUTE_CLASS);
          if (className == null) {
            throw new InvalidDataException();
          }
          name = className.substring(className.lastIndexOf('.') + 1);
        }

        convertComponents(root, filePath, additionalFiles);

        addConfiguration(name, element);
      }
    }

    for (String additionalPath : additionalFiles) {
      loadFile(additionalPath);
    }
  }

  private static void convertComponents(Element root, String filePath, final List<String> additionalFiles) {// Converting components
    final String additionalFilePath;
    additionalFilePath = Convertor34.convertLibraryTable34(root, filePath);
    if (additionalFilePath != null) {
      additionalFiles.add(additionalFilePath);
    }
    // Additional converors here probably, adding new files to load
    // to aditionalFiles
  }

  public void dispose() {
    Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
    final boolean[] canClose = new boolean[]{true};
    for (final Project project : openProjects) {
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.executeCommand(project, new Runnable() {
        public void run() {
          FileDocumentManager.getInstance().saveAllDocuments();
          if (!ProjectManagerEx.getInstanceEx().closeProject(project)) {
            canClose[0] = false;
          }
        }
      }, ApplicationBundle.message("command.exit"), null);
      if (!canClose[0]) break;
      Disposer.dispose(project);
    }

    if (canClose[0]) {
      fireApplicationExiting();
      disposeComponents();
    }

    super.dispose();
  }

  public boolean runProcessWithProgressSynchronously(final Runnable process, String progressTitle, boolean canBeCanceled, Project project) {
    assertIsDispatchThread();

    if (myExceptionalThreadWithReadAccess != null || ApplicationManager.getApplication().isUnitTestMode()) {
      process.run();
      return true;
    }

    final ProgressWindow progress = new ProgressWindow(canBeCanceled, project);
    progress.setTitle(progressTitle);

    class MyThread extends Thread {
      private final Runnable myProcess;

      public MyThread() {
        //noinspection HardCodedStringLiteral
        super("Process with Progress");
        myProcess = process;
      }

      public void run() {
        if (myExceptionalThreadWithReadAccess != this) {
          if (myExceptionalThreadWithReadAccess == null) {
            LOG.error("myExceptionalThreadWithReadAccess = null!");
          }
          else {
            LOG.error("myExceptionalThreadWithReadAccess != thread, process = " + ((MyThread)myExceptionalThreadWithReadAccess).myProcess);
          }
        }

        try {
          ProgressManager.getInstance().runProcess(myProcess, progress);
        }
        catch (ProcessCanceledException e) {
          // ok to ignore.
        }
      }
    }
    final MyThread thread = new MyThread();
    try {
      myExceptionalThreadWithReadAccess = thread;

      final boolean[] threadStarted = new boolean[]{false};
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myExceptionalThreadWithReadAccess != thread) {
            if (myExceptionalThreadWithReadAccess == null) {
              LOG.error("myExceptionalThreadWithReadAccess = null!");
            }
            else {
              LOG.error("myExceptionalThreadWithReadAccess != thread, process = " + ((MyThread)myExceptionalThreadWithReadAccess)
                .myProcess);
            }
          }

          thread.start();
          threadStarted[0] = true;
        }
      });

      progress.startBlocking();
      LOG.assertTrue(threadStarted[0]);
      LOG.assertTrue(!progress.isRunning());
    }
    finally {
      myExceptionalThreadWithReadAccess = null;
    }

    return !progress.isCanceled();
  }

  public void invokeLater(Runnable runnable) {
    LaterInvocator.invokeLater(runnable);
  }

  public void invokeLater(Runnable runnable, ModalityState state) {
    LaterInvocator.invokeLater(runnable, state);
  }

  public void invokeAndWait(Runnable runnable, ModalityState modalityState) {
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
        invokeLater(runnable, ModalityState.NON_MMODAL);
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
    boolean isExceptionalThread = isExceptionalThreadWithReadAccess(Thread.currentThread());

    if (!isExceptionalThread) {
      while (true) {
        try {
          myActionsLock.readLock().acquire();
        }
        catch (InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
        break;
      }
    }

    try {
      action.run();
    }
    finally {
      if (!isExceptionalThread) {
        myActionsLock.readLock().release();
      }
    }
  }

  public boolean isExceptionalThreadWithReadAccess(final Thread thread) {
    return myExceptionalThreadWithReadAccess != null && thread == myExceptionalThreadWithReadAccess;
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

    myIsWaitingForWriteAction = true;
    try {
      while (true) {
        try {
          myActionsLock.writeLock().acquire();
        }
        catch (InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
        break;
      }
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
      if(project != null) {
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
        if (actionClass == null || actionClass.isAssignableFrom(action.getClass())) return action;
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
      //noinspection HardCodedStringLiteral
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
    else {
      if (myExceptionalThreadWithReadAccess == currentThread) return true;
      if (myActionsLock.isReadLockAcquired(currentThread)) return true;
      if (myActionsLock.isWriteLockAcquired(currentThread)) return true;
      return isDispatchThread();
    }
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

  protected void assertCanRunWriteAction() {
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

  protected Element getDefaults(BaseComponent component) throws IOException, JDOMException, InvalidDataException {
    InputStream stream = getDefaultsInputStream(component);

    if (stream != null) {
      Document document = null;
      try {
        document = JDOMUtil.loadDocument(stream);
      }
      finally {
        stream.close();
      }
      if (document == null) {
        throw new InvalidDataException();
      }
      Element root = document.getRootElement();
      if (root == null || !ELEMENT_COMPONENT.equals(root.getName())) {
        throw new InvalidDataException();
      }
      return root;
    }
    return null;
  }

  @Nullable
  protected ComponentManagerImpl getParentComponentManager() {
    return null;
  }

  private static InputStream getDefaultsInputStream(BaseComponent component) {
    return DecodeDefaultsUtil.getDefaultsInputStream(component);
  }

  public void saveSettings() {
    if (myDoNotSave) return;

    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());

    try {
      if (!ourSaveSettingsInProgress) {
        ourSaveSettingsInProgress = true;
        final String optionsPath = PathManager.getOptionsPath();
        File file = new File(optionsPath);
        if (!file.exists()) {
          file.mkdirs();
        }

        try {
          save(optionsPath);
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
        finally {
          ourSaveSettingsInProgress = false;
        }
      }

      saveSettingsSavingComponents();
    }
    finally {
      ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
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
}
