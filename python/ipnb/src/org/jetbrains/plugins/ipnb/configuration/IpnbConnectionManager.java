package org.jetbrains.plugins.ipnb.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IpnbConnectionManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(IpnbConnectionManager.class);
  private final Project myProject;
  private Map<String, IpnbConnection> myKernels = new HashMap<String, IpnbConnection>();
  private Map<String, IpnbCodePanel> myUpdateMap = new HashMap<String, IpnbCodePanel>();
  private KillableColoredProcessHandler myProcessHandler;

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
      String url = IpnbSettings.getInstance(myProject).getURL();
      if (StringUtil.isEmptyOrSpaces(url)) {
        showWarning(fileEditor, "Please, specify IPython Notebook URL in <a href=\"\">Settings->IPython Notebook</a>");
        return;
      }
      if (startConnection(codePanel, path, url, false)) {
        return;
      }
      url = showDialogUrl(url);
      if (url == null) return;
      IpnbSettings.getInstance(myProject).setURL(url);
      boolean connectionStarted = startConnection(codePanel, path, url, false);
      if (!connectionStarted) {
        final boolean serverStarted = startIpythonServer(url, fileEditor);
        if (!serverStarted) {
          return;
        }
        final Notification notification =
          new Notification("IPythonNotebook", "", "<html>IPython notebook started at <a href=\"" + url +
                                                  "\">" + url + "</a></html>", NotificationType.INFORMATION,
                           NotificationListener.URL_OPENING_LISTENER);
        notification.notify(myProject);
        IpnbSettings.getInstance(myProject).setURL(url);
      }
      final String finalUrl = url;
      new Alarm().addRequest(new Runnable() {
        @Override
        public void run() {
          startConnection(codePanel, path, finalUrl, true);
        }
      }, 3000);
    }
    else {
      final IpnbConnection connection = myKernels.get(path);
      if (connection != null) {
        final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
        myUpdateMap.put(messageId, codePanel);
      }
    }
  }

  private static String showDialogUrl(@NotNull final String initialUrl) {
    final String url = Messages.showInputDialog("IPython Notebook URL:", "Start IPython Notebook", null, initialUrl,
                                              new InputValidator() {
                                                @Override
                                                public boolean checkInput(String inputString) {
                                                  try {
                                                    new URL(inputString);
                                                  }
                                                  catch (MalformedURLException e) {
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

  private boolean startConnection(@NotNull final IpnbCodePanel codePanel, @NotNull final String path, @NotNull final String url,
                                  boolean showNotification) {
    try {
      final IpnbConnection connection = new IpnbConnection(new URI(url), new IpnbConnectionListenerBase() {
        @Override
        public void onOpen(@NotNull IpnbConnection connection) {
          final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
          myUpdateMap.put(messageId, codePanel);
        }

        @Override
        public void onOutput(@NotNull IpnbConnection connection,
                             @NotNull String parentMessageId,
                             @NotNull List<IpnbOutputCell> outputs,
                             @Nullable Integer execCount) {
          if (!myUpdateMap.containsKey(parentMessageId)) return;
          final IpnbCodePanel cell = myUpdateMap.remove(parentMessageId);
          cell.getCell().setPromptNumber(execCount);
          cell.updatePanel(outputs);
        }
      });
      myKernels.put(path, connection);
    }
    catch (URISyntaxException e) {
      if (showNotification)
        showWarning(codePanel.getFileEditor(), "Please, check IPython Notebook URL in Settings->IPython Notebook");
      LOG.warn("IPython Notebook URI Syntax Error: " + e.getMessage());
      return false;
    }
    catch (IOException e) {
      if (showNotification)
        showWarning(codePanel.getFileEditor(), "IPython Notebook connection refused");
      LOG.warn("IPython Notebook connection refused: " + e.getMessage());
      return false;
    }
    return true;
  }

  private static void showWarning(@NotNull final IpnbFileEditor fileEditor, @NotNull final String message) {
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
      message, null, MessageType.WARNING.getPopupBackground(), null);
    final Balloon balloon = balloonBuilder.createBalloon();
    balloon.showInCenterOf(fileEditor.getRunCellButton());
  }

  private boolean startIpythonServer(@NotNull final String url, @NotNull final IpnbFileEditor fileEditor) {
    final Module module = ProjectFileIndex.SERVICE.getInstance(myProject).getModuleForFile(fileEditor.getVirtualFile());
    if (module == null) return false;
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      showWarning(fileEditor, "Please check Python Interpreter in Settings->Python Interpreter");
      return false;
    }
    try {
      final PyPackage ipythonPackage = PyPackageManager.getInstance(sdk).findPackage("ipython", false);
      if (ipythonPackage == null) {
        showWarning(fileEditor, "Add IPython to the interpreter of the current project.");
        return false;
      }
    }
    catch (ExecutionException ignored) {
    }
    final Map<String, String> env = ImmutableMap.of("PYCHARM_EP_DIST", "ipython", "PYCHARM_EP_NAME", "ipython");
    try {
      final Pair<String, String> hostPort = getHostPortFromUrl(url);
      final String ipython = PythonHelpersLocator.getHelperPath("pycharm/pycharm_load_entry_point.py");
      final ArrayList<String> parameters = Lists.newArrayList(sdk.getHomePath(), ipython, "notebook", "--no-browser");
      if (hostPort.getFirst() != null) {
        parameters.add("--ip");
        parameters.add(hostPort.getFirst());
      }
      if (hostPort.getSecond() != null) {
        parameters.add("--port");
        parameters.add(hostPort.getSecond());
      }
      final GeneralCommandLine commandLine = new GeneralCommandLine(parameters).withWorkDirectory(myProject.getBasePath()).
        withEnvironment(env);

      myProcessHandler = new KillableColoredProcessHandler(commandLine);

      return true;
    }
    catch (ExecutionException e) {
      return false;
    }
  }

  @NotNull
  public static Pair<String, String> getHostPortFromUrl(@NotNull String url) {
    String host = null;
    String port = null;
    int index = url.indexOf("://");
    if (index != -1) {
      url = url.substring(index + 3);
    }
    index = url.indexOf(':');
    if (index != -1) {
      host = url.substring(0, index);
      port = url.substring(index + 1);
    }
    return Pair.create(host, port);
  }

  public void projectOpened() {}


  public void projectClosed() {
    shutdownKernels();
  }

  private void shutdownKernels() {
    for (IpnbConnection connection : myKernels.values()) {
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
    if (myProcessHandler != null) {
      myProcessHandler.killProcess();
    }
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
}
