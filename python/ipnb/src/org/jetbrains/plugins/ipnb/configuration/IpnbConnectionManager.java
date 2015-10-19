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
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.Alarm;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.sdk.PythonSdkType;
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
  private Map<String, IpnbConnection> myKernels = new HashMap<String, IpnbConnection>();
  private Map<String, IpnbCodePanel> myUpdateMap = new HashMap<String, IpnbCodePanel>();

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
    if (!myKernels.containsKey(path)) {
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

  private void startConnection(@NotNull final IpnbCodePanel codePanel, final IpnbFileEditor fileEditor, final String path) {
    String url = IpnbSettings.getInstance(myProject).getURL();
    if (StringUtil.isEmptyOrSpaces(url)) {
      url = IpnbSettings.DEFAULT_URL;
    }

    boolean connectionStarted = startConnection(codePanel, path, url, false);
    if (!connectionStarted) {
      final String finalUrl = url;
      url = showDialogUrl(url);
      if (url == null) return;
      IpnbSettings.getInstance(myProject).setURL(url);

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final boolean serverStarted = startIpythonServer(finalUrl, fileEditor);
          if (!serverStarted) {
            return;
          }
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              new Alarm(Alarm.ThreadToUse.SWING_THREAD).addRequest(new Runnable() {
                @Override
                public void run() {
                  startConnection(codePanel, path, finalUrl, true);
                }
              }, 3000);
            }
          });
        }
      });
    }
  }

  @Nullable
  private static String showDialogUrl(@NotNull final String initialUrl) {
    final String url = Messages.showInputDialog("IPython Notebook URL:", "Start IPython Notebook", null, initialUrl,
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
    return url == null ? null : StringUtil.trimEnd(url, "/");
  }

  private boolean startConnection(@NotNull final IpnbCodePanel codePanel, @NotNull final String path, @NotNull final String urlString,
                                  final boolean showNotification) {
    try {
      final IpnbConnectionListenerBase listener = new IpnbConnectionListenerBase() {
        @Override
        public void onOpen(@NotNull IpnbConnection connection) {
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
            //noinspection unchecked
            cell.updatePanel(payload, null);
          }
        }
      };

      try {
        final IpnbConnection connection = getConnection(codePanel, urlString, listener);
        myKernels.put(path, connection);
      }
      catch (URISyntaxException e) {
        if (showNotification) {
          showWarning(codePanel.getFileEditor(),
                      "Please, check IPython Notebook URL in <a href=\"\">Settings->Tools->IPython Notebook</a>",
                      new IpnbSettingsAdapter());
          LOG.warn("IPython Notebook connection refused: " + e.getMessage());
        }
        return false;
      }
    }
    catch (IOException e) {
      if (showNotification) {
        LOG.warn("IPython Notebook connection refused: " + e.getMessage());
      }
      return false;
    }
    return true;
  }

  @NotNull
  private static IpnbConnection getConnection(@NotNull final IpnbCodePanel codePanel, @NotNull final String urlString,
                                              @NotNull final IpnbConnectionListenerBase listener)
    throws IOException, URISyntaxException {
    if (!IpnbParser.isIpythonNewFormat(codePanel.getFileEditor().getVirtualFile())) {
      return new IpnbConnection(urlString, listener);
    }
    return new IpnbConnectionV3(urlString, listener);
  }

  public void interruptKernel(@NotNull final String filePath) {
    if (!myKernels.containsKey(filePath)) return;
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
    if (!myKernels.containsKey(filePath)) return;
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

  private boolean startIpythonServer(@NotNull final String url, @NotNull final IpnbFileEditor fileEditor) {
    final Module module = ProjectFileIndex.SERVICE.getInstance(myProject).getModuleForFile(fileEditor.getVirtualFile());
    if (module == null) return false;
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      showWarning(fileEditor, "Please check Python Interpreter in Settings->Python Interpreter", null);
      return false;
    }
    try {
      final PyPackage ipythonPackage = PyPackageManager.getInstance(sdk).findPackage("ipython", false);
      if (ipythonPackage == null) {
        showWarning(fileEditor, "Add IPython to the interpreter of the current project.", null);
        return false;
      }
    }
    catch (ExecutionException ignored) {
    }

    final Pair<String, String> hostPort = getHostPortFromUrl(url);
    if (hostPort == null) {
      showWarning(fileEditor, "Please, check IPython Notebook URL in <a href=\"\">Settings->Tools->IPython Notebook</a>",
                  new IpnbSettingsAdapter());
      return false;
    }
    final String homePath = sdk.getHomePath();
    if (homePath == null) {
      showWarning(fileEditor, "Python Sdk is invalid, please check Python Interpreter in Settings->Python Interpreter", null);
      return false;
    }
    String ipython = findIPythonRunner(homePath);
    Map<String, String> env = null;
    if (ipython == null) {
      ipython = PythonHelper.LOAD_ENTRY_POINT.asParamString();
      env = ImmutableMap.of("PYCHARM_EP_DIST", "ipython", "PYCHARM_EP_NAME", "ipython");
    }

    final ArrayList<String> parameters = Lists.newArrayList(homePath, ipython, "notebook", "--no-browser");
    if (hostPort.getFirst() != null) {
      parameters.add("--ip");
      parameters.add(hostPort.getFirst());
    }
    if (hostPort.getSecond() != null) {
      parameters.add("--port");
      parameters.add(hostPort.getSecond());
    }
    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters).withWorkDirectory(myProject.getBasePath());
    if (env != null) {
      commandLine.withEnvironment(env);
    }

    try {
      final KillableColoredProcessHandler processHandler = new KillableColoredProcessHandler(commandLine) {
        @Override
        protected void doDestroyProcess() {
          super.doDestroyProcess();
          UnixProcessManager.sendSigIntToProcessTree(getProcess());
        }

        @Override
        public boolean isSilentlyDestroyOnClose() {
          return true;
        }
      };
      processHandler.setShouldDestroyProcessRecursively(true);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          new RunContentExecutor(myProject, processHandler)
            .withTitle("IPython Notebook")
            .withStop(new Runnable() {
              @Override
              public void run() {
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
            .run();
        }
      });
      return true;
    }
    catch (ExecutionException e) {
      return false;
    }
  }

  @Nullable
  private static String findIPythonRunner(String homePath) {
    for (String name : Lists.newArrayList("ipython", "ipython-script.py")) {
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
      ShowSettingsUtil.getInstance().showSettingsDialog(myProject, "IPython Notebook");
    }
  }
}
