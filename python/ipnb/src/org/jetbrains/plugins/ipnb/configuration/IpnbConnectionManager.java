package org.jetbrains.plugins.ipnb.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionV3;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IpnbConnectionManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(IpnbConnectionManager.class);
  private final Project myProject;
  private Map<String, IpnbConnection> myKernels = new HashMap<>();
  private Map<String, IpnbCodePanel> myUpdateMap = new HashMap<>();
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

  private void startConnection(@NotNull final IpnbCodePanel codePanel, final IpnbFileEditor fileEditor, final String path) {
    String url = getURL();

    boolean connectionStarted = startConnection(codePanel, path, url, false);
    if (!connectionStarted) {

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final boolean serverStarted = startIpythonServer(url, fileEditor);
          if (!serverStarted) {
            return;
          }
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              startConnection(codePanel, path, url, true);
            }
          });
        }
      });
    }
  }

  private String getURL() {
    String url = IpnbSettings.getInstance(myProject).getURL();
    return StringUtil.isEmptyOrSpaces(url) ? IpnbSettings.DEFAULT_URL : url;
  }

  @Nullable
  public static String showDialogUrl(@NotNull final String initialUrl) {
    final String url = UIUtil.invokeAndWaitIfNeeded(new Computable<String>() {
      @Override
      public String compute() {
        return Messages.showInputDialog("Jupyter Notebook URL:", "Start Jupyter Notebook", null, initialUrl,
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
                                     return !inputString.isEmpty();
                                   }

                                   @Override
                                   public boolean canClose(String inputString) {
                                     return true;
                                   }
                                 });
      }
    });
    return url == null ? null : StringUtil.trimEnd(url, "/");
  }

  public boolean startConnection(@Nullable final IpnbCodePanel codePanel, @NotNull final String path, @NotNull final String urlString,
                                  final boolean showNotification) {
    try {
      final boolean[] connectionOpened = {false};
      final IpnbConnectionListenerBase listener = new IpnbConnectionListenerBase() {
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
          //noinspection unchecked
          cell.updatePanel(null, (List<IpnbOutputCell>)connection.getOutput().clone());
        }

        @Override
        public void onPayload(@Nullable String payload, @NotNull String parentMessageId) {
          if (!myUpdateMap.containsKey(parentMessageId)) return;
          final IpnbCodePanel cell = myUpdateMap.remove(parentMessageId);
          if (payload != null) {
            cell.updatePanel(payload, null);
          }
        }
      };

      try {
        final IpnbConnection connection = getConnection(codePanel, urlString, listener);
        int countAttempt = 0;
        while (!connectionOpened[0] && countAttempt < MAX_ATTEMPTS) {
          countAttempt += 1;
          TimeoutUtil.sleep(1000);
        }
        myKernels.put(path, connection);
      }
      catch (URISyntaxException e) {
        if (showNotification && codePanel != null) {
          showWarning(codePanel.getFileEditor(),
                      "Please, check Jupyter Notebook URL in <a href=\"\">Settings->Tools->Jupyter Notebook</a>",
                      new IpnbSettingsAdapter());
          LOG.warn("Jupyter Notebook connection refused: " + e.getMessage());
        }
        return false;
      }
    }
    catch (IOException e) {
      if (showNotification) {
        LOG.warn("Jupyter Notebook connection refused: " + e.getMessage());
      }
      return false;
    }
    return true;
  }

  @NotNull
  private static IpnbConnection getConnection(@Nullable final IpnbCodePanel codePanel, @NotNull final String urlString,
                                              @NotNull final IpnbConnectionListenerBase listener)
    throws IOException, URISyntaxException {
    if (codePanel != null && !IpnbParser.isIpythonNewFormat(codePanel.getFileEditor().getVirtualFile())) {
      return new IpnbConnection(urlString, listener);
    }
    return new IpnbConnectionV3(urlString, listener);
  }

  public void interruptKernel(@NotNull final String filePath) {
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

  public void reloadKernel(@NotNull final String filePath) {
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

  private static void showWarning(@NotNull final IpnbFileEditor fileEditor, @NotNull final String message,
                                  @Nullable final HyperlinkAdapter listener) {
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
      message, null, MessageType.WARNING.getPopupBackground(), listener);
    final Balloon balloon = balloonBuilder.createBalloon();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        balloon.showInCenterOf(fileEditor.getRunCellButton());
      }
    });
  }

  public boolean startIpythonServer(@NotNull final String initUrl, @NotNull final IpnbFileEditor fileEditor) {
    final Module module = ProjectFileIndex.SERVICE.getInstance(myProject).getModuleForFile(fileEditor.getVirtualFile());
    if (module == null) return false;
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      showWarning(fileEditor, "Please check Python Interpreter in Settings->Python Interpreter", null);
      return false;
    }
    final List<PyPackage> packages = PyPackageManager.getInstance(sdk).getPackages();
    final PyPackage ipythonPackage = packages != null ? PyPackageUtil.findPackage(packages, "ipython") : null;
    final PyPackage jupyterPackage = packages != null ? PyPackageUtil.findPackage(packages, "jupyter") : null;
    if (ipythonPackage == null && jupyterPackage == null) {
      showWarning(fileEditor, "Add Jupyter to the interpreter of the current project.", null);
      return false;
    }

    String url = showDialogUrl(initUrl);
    if (url == null) return false;
    IpnbSettings.getInstance(myProject).setURL(url);

    final Pair<String, String> hostPort = getHostPortFromUrl(url);
    if (hostPort == null) {
      showWarning(fileEditor, "Please, check Jupyter Notebook URL in <a href=\"\">Settings->Tools->Jupyter Notebook</a>",
                  new IpnbSettingsAdapter());
      return false;
    }
    final String homePath = sdk.getHomePath();
    if (homePath == null) {
      showWarning(fileEditor, "Python Sdk is invalid, please check Python Interpreter in Settings->Python Interpreter", null);
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
    final String baseDir = ModuleRootManager.getInstance(module).getContentRoots()[0].getCanonicalPath();
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
          UnixProcessManager.sendSigIntToProcessTree(getProcess());
        }

        @Override
        public void coloredTextAvailable(@NonNls String text, Key attributes) {
          super.coloredTextAvailable(text, attributes);
          if (text.toLowerCase().contains("active kernels")) {
            serverStarted[0] = true;
          }
        }

        @Override
        public boolean isSilentlyDestroyOnClose() {
          return true;
        }
      };
      processHandler.setShouldDestroyProcessRecursively(true);
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          new RunContentExecutor(myProject, processHandler)
            .withTitle("Jupyter Notebook")
            .withStop(new Runnable() {
              @Override
              public void run() {
                myKernels.clear();
                processHandler.destroyProcess();
                UnixProcessManager.sendSigIntToProcessTree(processHandler.getProcess());
              }
            }, new Computable<Boolean>() {
              @Override
              public Boolean compute() {
                return !processHandler.isProcessTerminated();
              }
            })
            .withRerun(new Runnable() {
              @Override
              public void run() {
                startIpythonServer(url, fileEditor);
              }
            })
            .withHelpId("reference.manage.py")
            .run();
        }
      });
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
  public static Pair<String, String> getHostPortFromUrl(@NotNull String url) {
    try {
      final URI uri = new URI(url);
      final int port = uri.getPort();
      return Pair.create(uri.getHost(), port == -1 ? null : String.valueOf(port));
    }
    catch (URISyntaxException e) {
      return null;
    }
  }

  public void projectOpened() {
  }


  public void projectClosed() {
    shutdownKernels();
  }

  private void shutdownKernels() {
    for (IpnbConnection connection : myKernels.values()) {
      if (!connection.isAlive()) continue;
      connection.shutdown();
      try {
        connection.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }
    myKernels.clear();
  }

  @NotNull
  public String getComponentName() {
    return "IpnbConnectionManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    shutdownKernels();
  }

  class IpnbSettingsAdapter extends HyperlinkAdapter {
    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      ShowSettingsUtil.getInstance().showSettingsDialog(myProject, "Jupyter Notebook");
    }
  }
}
