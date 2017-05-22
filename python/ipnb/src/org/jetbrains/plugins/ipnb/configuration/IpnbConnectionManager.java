package org.jetbrains.plugins.ipnb.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionV3;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IpnbConnectionManager implements ProjectComponent, Disposable {
  private static final Logger LOG = Logger.getInstance(IpnbConnectionManager.class);
  private final Project myProject;
  private final Map<String, IpnbConnection> myKernels = new HashMap<>();
  private final Map<String, IpnbCodePanel> myUpdateMap = new HashMap<>();
  @Nullable private String myToken;
  
  private static final String CONNECTION_REFUSED = "Connection refused";
  private static final int MAX_ATTEMPTS = 10;

  public IpnbConnectionManager(final Project project) {
    myProject = project;
  }

  public static IpnbConnectionManager getInstance(Project project) {
    return project.getComponent(IpnbConnectionManager.class);
  }

  public void executeCell(@NotNull final IpnbCodePanel codePanel) {
    final IpnbFileEditor fileEditor = codePanel.getFileEditor();
    final VirtualFile virtualFile = fileEditor.getVirtualFile();
    final String path = virtualFile.getPath();
    if (!hasConnection(path)) {
      startConnection(codePanel, fileEditor, path);
    }
    else {
      IpnbConnection connection = myKernels.get(path);
      if (!connection.isAlive()) {
        myKernels.remove(path);
        startConnection(codePanel, fileEditor, path);
      }
      else {
        final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
        myUpdateMap.put(messageId, codePanel);
      }
    }
  }

  public boolean hasConnection(String path) {
    return myKernels.containsKey(path);
  }

  private void startConnection(@NotNull final IpnbCodePanel codePanel, @NotNull final IpnbFileEditor fileEditor,
                               @NotNull final String path) {
    final String url = getURL();
    if (connectToIpythonServer(codePanel, fileEditor, path, url)) return;
    final boolean isRemote = IpnbSettings.getInstance(myProject).isRemote();
    if (!isRemote) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        final boolean serverStarted = startIpythonServer(url, fileEditor);
        if (!serverStarted) {
          return;
        }
        GuiUtils.invokeLaterIfNeeded(() -> startConnection(codePanel, path, url, true), ModalityState.defaultModalityState());
      });
    }
  }

  private boolean connectToIpythonServer(@NotNull final IpnbCodePanel codePanel,
                                         @NotNull final IpnbFileEditor fileEditor,
                                         @NotNull final String path,
                                         @NotNull final String url) {
    final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
    final boolean isRemote = ipnbSettings.isRemote();
    if (!isRemote) {
      if (myToken != null) return startConnection(codePanel, path, url, true);
      final Module module = ProjectFileIndex.SERVICE.getInstance(myProject).getModuleForFile(fileEditor.getVirtualFile());
      if (module != null) {
        final Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null) {
          final List<PyPackage> packages = PyPackageManager.getInstance(sdk).getPackages();
          if (packages != null) {
            final PyPackage notebookPackage = PyPackageUtil.findPackage(packages, "notebook");
            if (notebookPackage != null && VersionComparatorUtil.compare(notebookPackage.getVersion(), "4.3.0") >= 0) {
              ApplicationManager.getApplication().invokeAndWait(() -> myToken = askForToken(url));
              if (myToken == null) return false;
            }
          }
        }
      }
    }
    return startConnection(codePanel, path, url, isRemote);
  }

  private static String askForToken(@NotNull final String url) {
    if (pingHost(url)) {
      final String token = Messages.showInputDialog("Please, enter your authentication token",
                                                    "Jupyter Notebook " + url, null);
      if (!StringUtil.isEmptyOrSpaces(token)) {
        return token.trim();
      }
    }
    return null;
  }

  public static boolean pingHost(@NotNull final String url) {
    final Pair<String, String> hostPort = getHostPortFromUrl(url);
    if (hostPort == null) return false;
    final String host = hostPort.getFirst();
    final String port = hostPort.getSecond();
    try (Socket socket = new Socket()) {
      if (port == null) {
        return InetAddress.getByName(host).isReachable(1000);
      }
      socket.connect(new InetSocketAddress(host, Integer.parseInt(port)), 1000);
      return true;
    }
    catch (IOException | IllegalArgumentException e) {
      return false;
    }
  }

  private String getURL() {
    String url = IpnbSettings.getInstance(myProject).getURL();
    return StringUtil.isEmptyOrSpaces(url) ? IpnbSettings.DEFAULT_URL : url;
  }

  @Nullable
  public static String showDialogUrl(@NotNull final String initialUrl) {
    final String url = UIUtil.invokeAndWaitIfNeeded(
      () -> Messages.showInputDialog("Jupyter Notebook URL:", "Start Jupyter Notebook", null, initialUrl,
                                     new InputValidator() {
                                       @Override
                                       public boolean checkInput(String inputString) {
                                         try {
                                           final URI uri = new URI(inputString);
                                           if (uri.getPort() == -1 || StringUtil.isEmptyOrSpaces(uri.getHost())) {
                                             return false;
                                           }
                                         }
                                         catch (URISyntaxException e) {
                                           return false;
                                         }
                                         // Do not start notebook if host is busy
                                         if (pingHost(inputString)) return false;
                                         return !inputString.isEmpty();
                                       }

                                       @Override
                                       public boolean canClose(String inputString) {
                                         return true;
                                       }
                                     }));
    return url == null ? null : StringUtil.trimEnd(url, "/");
  }

  public boolean startConnection(@Nullable final IpnbCodePanel codePanel,
                                 @NotNull final String path,
                                 @NotNull final String urlString,
                                 final boolean showNotification) {
    final boolean[] connectionOpened = {false};

    if (codePanel == null) return false;
    final VirtualFile file = codePanel.getFileEditor().getVirtualFile();
    String pathToFile = getRelativePathToFile(file);
    if (pathToFile == null) return false;
    final boolean format = IpnbParser.isIpythonNewFormat(file);
    Boolean isConnected = IpnbUtils.runCancellableProcessUnderProgress(myProject, () -> setupConnection(codePanel, path, urlString,
                                                                                                        showNotification,
                                                                                                        connectionOpened,
                                                                                                        format),
                                                                       "Connecting to Jupyter Notebook Server");
    return isConnected != null ? isConnected : false;
  }

  @NotNull
  private IpnbConnectionListenerBase createConnectionListener(@Nullable IpnbCodePanel codePanel, boolean[] connectionOpened) {
    return new IpnbConnectionListenerBase() {
      @Override
      public void onOpen(@NotNull IpnbConnection connection) {
        connectionOpened[0] = true;
        if (codePanel == null) return;
        final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
        myUpdateMap.put(messageId, codePanel);
      }

      @Override
      public void onOutput(@NotNull IpnbConnection connection,
                           @NotNull String parentMessageId) {
        if (!myUpdateMap.containsKey(parentMessageId)) return;
        final IpnbCodePanel cell = myUpdateMap.get(parentMessageId);
        cell.getCell().setPromptNumber(connection.getExecCount());
        cell.updatePanel(null, connection.getOutput());
      }

      @Override
      public void onPayload(@Nullable String payload, @NotNull String parentMessageId) {
        if (!myUpdateMap.containsKey(parentMessageId)) return;
        final IpnbCodePanel cell = myUpdateMap.remove(parentMessageId);
        if (payload != null) {
          cell.updatePanel(payload, null);
        }
      }

      @Override
      public void onFinished(@NotNull IpnbConnection connection, @NotNull String parentMessageId) {
        if (!myUpdateMap.containsKey(parentMessageId)) return;
        final IpnbCodePanel cell = myUpdateMap.remove(parentMessageId);
        cell.getCell().setPromptNumber(connection.getExecCount());
        cell.finishExecution();
      }
    };
  }

  
  private boolean setupConnection(@NotNull IpnbCodePanel codePanel,
                                  @NotNull String path,
                                  @NotNull String urlString,
                                  boolean showNotification,
                                  boolean[] connectionOpened,
                                  boolean isNewFormat) {
    try {
      final IpnbConnectionListenerBase listener = createConnectionListener(codePanel, connectionOpened);
      final VirtualFile file = codePanel.getFileEditor().getVirtualFile();
      final String pathToFile = getRelativePathToFile(file);
      if (pathToFile == null) return false;
      final IpnbConnection connection = getConnection(urlString, listener, pathToFile, isNewFormat);
      int countAttempt = 0;
      while (!connectionOpened[0] && countAttempt < MAX_ATTEMPTS) {
        countAttempt += 1;
        TimeoutUtil.sleep(1000);
      }
      myKernels.put(path, connection);
    }
    catch (URISyntaxException e) {
      if (showNotification) {
        showMessage(codePanel.getFileEditor(),
                    "Please, check Jupyter Notebook URL in <a href=\"\">Settings->Tools->Jupyter Notebook</a>",
                    new IpnbSettingsAdapter(), MessageType.WARNING);
        LOG.warn("Jupyter Notebook connection refused: " + e.getMessage());
      }
      return false;
    }
    catch (UnsupportedOperationException e) {
      showMessage(codePanel.getFileEditor(), e.getMessage(), new IpnbSettingsAdapter(), MessageType.WARNING);
    }
    catch (UnknownHostException e) {
      showMessage(codePanel.getFileEditor(), "Please, check Jupyter Notebook URL in <a href=\"\">Settings->Tools->Jupyter Notebook</a>",
                  new IpnbSettingsAdapter(), MessageType.WARNING);
    }
    catch (IOException e) {
      if (IpnbConnection.AUTHENTICATION_NEEDED.equals(e.getMessage())) {
        ApplicationManager.getApplication().invokeAndWait(() -> myToken = askForToken(urlString));
        if (myToken != null) {
          return setupConnection(codePanel, path, urlString, showNotification, connectionOpened, isNewFormat);
        }
      }
      if (showNotification) {
        final String message = e.getMessage();
        if (message.startsWith(IpnbConnection.UNABLE_LOGIN_MESSAGE)) {
          showMessage(codePanel.getFileEditor(), "Cannot connect to Jupyter Notebook: login failed", new IpnbSettingsAdapter(),
                      MessageType.WARNING);
        }
        else if (message.startsWith(CONNECTION_REFUSED) || message.startsWith(IpnbConnection.CANNOT_START_JUPYTER)) {
          showMessage(codePanel.getFileEditor(), "Cannot connect to Jupyter Notebook: cannot connect to Jupyter server",
                      new IpnbSettingsAdapter(), MessageType.WARNING);
        }
        
        LOG.warn("Jupyter Notebook connection refused: " + message);
      }
      return false;
    }
    return true;
  }

  @NotNull
  private IpnbConnection getConnection(@NotNull String urlString,
                                       @NotNull IpnbConnectionListenerBase listener,
                                       @NotNull String pathToFile,
                                       boolean isNewFormat) throws IOException, URISyntaxException {
    if (!isNewFormat) {
      return new IpnbConnection(urlString, listener, myToken, myProject, pathToFile);
    }
    return new IpnbConnectionV3(urlString, listener, myToken, myProject, pathToFile);
  }

  @Nullable
  private String getRelativePathToFile(VirtualFile file) {
    final String workingDirectory = IpnbSettings.getInstance(myProject).getWorkingDirectory();
    final String realWorkingDir = workingDirectory.isEmpty() ? myProject.getBasePath() : workingDirectory;
    if (realWorkingDir != null) {
      final Path basePath = Paths.get(realWorkingDir);
      final Path filePath = Paths.get(file.getPath());
      return basePath.relativize(filePath).toString();
    }
    return null;
  }

  public void interruptKernel(@NotNull String filePath) {
    if (!hasConnection(filePath)) return;
    final IpnbConnection connection = myKernels.get(filePath);
    try {
      connection.interrupt();
    }
    catch (IOException e) {
      LOG.warn("Failed to interrupt kernel " + filePath);
      LOG.warn(e.getMessage());
    }
  }

  public void reloadKernel(@NotNull String filePath) {
    if (!hasConnection(filePath)) return;
    final IpnbConnection connection = myKernels.get(filePath);
    try {
      connection.reload();
    }
    catch (IOException e) {
      LOG.warn("Failed to reload kernel " + filePath);
      LOG.warn(e.getMessage());
    }
  }

  private static void showMessage(@NotNull final IpnbFileEditor fileEditor,
                                  @NotNull final String message,
                                  @Nullable final HyperlinkAdapter listener, MessageType messageType) {
    ApplicationManager.getApplication().invokeLater(() -> {
      BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
        message, null, messageType.getPopupBackground(), listener);
      final Balloon balloon = balloonBuilder.setHideOnLinkClick(true).createBalloon();
      ApplicationManager.getApplication().invokeLater(() -> balloon.show(fileEditor.getRunButtonPlace(), Balloon.Position.above));
    });
  }

  public boolean startIpythonServer(@NotNull final String initUrl, @NotNull final IpnbFileEditor fileEditor) {
    final Module module = ProjectFileIndex.SERVICE.getInstance(myProject).getModuleForFile(fileEditor.getVirtualFile());
    if (module == null) return false;
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      showMessage(fileEditor, "Please check Python Interpreter in Settings->Python Interpreter", null, MessageType.WARNING);
      return false;
    }
    final List<PyPackage> packages = PyPackageManager.getInstance(sdk).getPackages();
    final PyPackage ipythonPackage = packages != null ? PyPackageUtil.findPackage(packages, "ipython") : null;
    final PyPackage jupyterPackage = packages != null ? PyPackageUtil.findPackage(packages, "jupyter") : null;
    if (ipythonPackage == null && jupyterPackage == null) {
      showMessage(fileEditor, "<a href=\"\">Add Jupyter</a> to the interpreter of the current project.",
                  new HyperlinkAdapter() {

                    @Override
                    protected void hyperlinkActivated(HyperlinkEvent e) {

                      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Installing Jupyter", false) {
                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                          try {
                            PyPackageManager.getInstance(sdk).install("jupyter");
                            showMessage(fileEditor, "Jupyter successfully installed", null, MessageType.INFO);
                          }
                          catch (ExecutionException e1) {
                            showMessage(fileEditor,
                                        "Failed to install Jupyter. Please, install it manually in <a href=\"\">Settings->Python Interpreter</a>",
                                        new InterpreterSettingsAdapter(), MessageType.WARNING);
                          }
                        }
                      });
                    }
                  }, MessageType.WARNING);
      return false;
    }

    String url = showDialogUrl(initUrl);
    if (url == null) return false;
    final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
    ipnbSettings.setURL(url);

    final Pair<String, String> hostPort = getHostPortFromUrl(url);
    if (hostPort == null) {
      showMessage(fileEditor, "Please, check Jupyter Notebook URL in <a href=\"\">Settings->Tools->Jupyter Notebook</a>",
                  new IpnbSettingsAdapter(), MessageType.WARNING);
      return false;
    }
    final String homePath = sdk.getHomePath();
    if (homePath == null) {
      showMessage(fileEditor, "Python Sdk is invalid, please check Python Interpreter in Settings->Python Interpreter", null,
                  MessageType.WARNING);
      return false;
    }
    Map<String, String> env = null;
    final ArrayList<String> parameters = Lists.newArrayList(homePath);
    String ipython = findJupyterRunner(homePath);
    if (ipython == null) {
      ipython = findIPythonRunner(homePath);
      if (ipython == null) {
        ipython = PythonHelper.LOAD_ENTRY_POINT.asParamString();
        env = ImmutableMap.of("PYCHARM_EP_DIST", "ipython", "PYCHARM_EP_NAME", "ipython");
      }
      parameters.add(ipython);
      parameters.add("notebook");
    }
    else {
      parameters.add(ipython);
    }
    parameters.add("--no-browser");

    if (hostPort.getFirst() != null) {
      parameters.add("--ip");
      parameters.add(hostPort.getFirst());
    }
    if (hostPort.getSecond() != null) {
      parameters.add("--port");
      parameters.add(hostPort.getSecond());
    }
    final String arguments = ipnbSettings.getArguments();
    if (!StringUtil.isEmptyOrSpaces(arguments)) {
      parameters.addAll(StringUtil.split(arguments, " "));
    }

    final String directory = ipnbSettings.getWorkingDirectory();
    final String baseDir = !StringUtil.isEmptyOrSpaces(directory) ? directory :
                           ModuleRootManager.getInstance(module).getContentRoots()[0].getCanonicalPath();
    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters).withWorkDirectory(baseDir);
    if (env != null) {
      commandLine.withEnvironment(env);
    }

    try {
      final boolean[] serverStarted = {false};
      final KillableColoredProcessHandler processHandler = new KillableColoredProcessHandler(commandLine) {
        @Override
        protected void doDestroyProcess() {
          super.doDestroyProcess();
          myKernels.clear();
          myToken = null;
          UnixProcessManager.sendSigKillToProcessTree(getProcess());
        }

        @Override
        public void coloredTextAvailable(@NotNull @NonNls String text, @NotNull Key attributes) {
          super.coloredTextAvailable(text, attributes);
          if (text.toLowerCase().contains("active kernels")) {
            serverStarted[0] = true;
          }
          final String token = "?token=";
          if (text.toLowerCase().contains(token)) {
            myToken = text.substring(text.indexOf(token) + token.length()).trim();
          }
        }

        @Override
        public boolean isSilentlyDestroyOnClose() {
          return true;
        }
      };
      processHandler.setShouldDestroyProcessRecursively(true);
      GuiUtils.invokeLaterIfNeeded(() -> new RunContentExecutor(myProject, processHandler)
        .withTitle("Jupyter Notebook")
        .withStop(() -> {
          myKernels.clear();
          processHandler.destroyProcess();
          UnixProcessManager.sendSigKillToProcessTree(processHandler.getProcess());
        }, () -> !processHandler.isProcessTerminated())
        .withRerun(() -> startIpythonServer(url, fileEditor))
        .withHelpId("reference.manage.py")
        .withFilter(new UrlFilter())
        .run(), ModalityState.defaultModalityState());
      int countAttempt = 0;
      while (!serverStarted[0] && countAttempt < MAX_ATTEMPTS) {
        countAttempt += 1;
        TimeoutUtil.sleep(1000);
      }
      return true;
    }
    catch (ExecutionException e) {
      return false;
    }
  }

  @Nullable
  @Deprecated
  private static String findIPythonRunner(@NotNull final String homePath) {
    for (String name : Lists.newArrayList("ipython", "ipython-script.py")) {
      String runnerPath = PythonSdkType.getExecutablePath(homePath, name);
      if (runnerPath != null) {
        return runnerPath;
      }
    }

    return null;
  }

  @Nullable
  private static String findJupyterRunner(@NotNull final String homePath) {
    for (String name : Lists.newArrayList("jupyter-notebook", "jupyter")) {
      String runnerPath = PythonSdkType.getExecutablePath(homePath, name);
      if (runnerPath != null) {
        return runnerPath;
      }
    }

    return null;
  }

  @Nullable
  public static Pair<String, String> getHostPortFromUrl(@NotNull final String url) {
    try {
      final URI uri = new URI(url);
      final int port = uri.getPort();
      return Pair.create(uri.getHost(), port == -1 ? null : String.valueOf(port));
    }
    catch (URISyntaxException e) {
      return null;
    }
  }
  
  @Override
  public void projectClosed() {
    shutdownKernels();
  }

  public void shutdownKernels() {
    for (IpnbConnection connection : myKernels.values()) {
      if (!connection.isAlive()) continue;
      connection.shutdown();
      try {
        connection.close();
      }
      catch (IOException | InterruptedException e) {
        LOG.error(e);
      }
    }
    myKernels.clear();
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "IpnbConnectionManager";
  }

  @Override
  public void dispose() {
    shutdownKernels();
  }

  class IpnbSettingsAdapter extends HyperlinkAdapter {
    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      ShowSettingsUtil.getInstance().showSettingsDialog(myProject, "Jupyter Notebook");
    }
  }

  class InterpreterSettingsAdapter extends HyperlinkAdapter {
    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      ShowSettingsUtil.getInstance().showSettingsDialog(myProject, PyBundle.message("active.sdk.dialog.project.interpreter"));
    }
  }
}
