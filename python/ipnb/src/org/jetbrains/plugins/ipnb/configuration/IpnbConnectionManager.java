package org.jetbrains.plugins.ipnb.configuration;

import com.google.common.collect.ImmutableMap;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IpnbConnectionManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(IpnbConnectionManager.class);
  private final Project myProject;
  private static final String DEFAULT_URL = "http://127.0.0.1:8888";
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
      if (url == null) {
        url = DEFAULT_URL;
      }
      if (!isAvailable(url)) {
        url = showDialogUrl(url);
        if (url == null) return;
        startIpythonServer(url, fileEditor);
        if (myProcessHandler != null) {
          waitForIpythonServer();
          startConnection(codePanel, path, url);
          return;
        }
        else {
          showWarning(fileEditor, "Could not start IPython Notebook");
          return;
        }
      }
      if (StringUtil.isEmptyOrSpaces(url)) {
        showWarning(fileEditor, "Please, specify IPython Notebook URL in <a href=\"\">Settings->IPython Notebook</a>");
        return;
      }
      startConnection(codePanel, path, url);
    }
    else {
      final IpnbConnection connection = myKernels.get(path);
      if (connection != null) {
        final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
        myUpdateMap.put(messageId, codePanel);
      }
    }
  }

  private void waitForIpythonServer() {
    final long startTime = System.currentTimeMillis();

    final InputStream stream = myProcessHandler.getProcess().getErrorStream();
    final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

    try {
      long time = System.currentTimeMillis() - startTime;
      while (time < 5000) {
        final String line = reader.readLine();
        if (line != null && line.contains("The IPython Notebook is running")) {
          break;
        }
        time = System.currentTimeMillis() - startTime;
      }
    }
    catch (IOException ignored) {
    }
    finally {
      try {
        reader.close();
      }
      catch (IOException ignored) {
      }
    }
  }

  private static String showDialogUrl(@NotNull final String url) {
    return Messages.showInputDialog("Ipython Notebook URL:", "Start Ipython Notebook", null, url,
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
  }

  private void startConnection(@NotNull final IpnbCodePanel codePanel, @NotNull final String path, @NotNull final String url) {
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
      showWarning(codePanel.getFileEditor(), "Please, check IPython Notebook URL in Settings->IPython Notebook");
    }
    catch (IOException e) {
      showWarning(codePanel.getFileEditor(), "IPython Notebook connection refused");
    }
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
    final Map<String, String> env = ImmutableMap.of("PYCHARM_EP_DIST", "ipython", "PYCHARM_EP_NAME", "ipython");
    try {
      final String ipython = PythonHelpersLocator.getHelperPath("pycharm/pycharm_load_entry_point.py");
      final GeneralCommandLine commandLine = new GeneralCommandLine(sdk.getHomePath(), ipython, "notebook", "--no-browser").
        withWorkDirectory(myProject.getBasePath()).withEnvironment(env);

      myProcessHandler = new KillableColoredProcessHandler(commandLine);

      IpnbSettings.getInstance(myProject).setURL(url);
      final Notification notification = new Notification("IpythonNotebook", "", "<html>Ipython notebook started at <a href=\"" + url +
        "\">" + url + "</a></html>", NotificationType.INFORMATION, NotificationListener.URL_OPENING_LISTENER);
      notification.notify(myProject);
      return true;
    }
    catch (ExecutionException e) {
      return false;
    }
  }

  public static boolean isAvailable(@NotNull final String url) {
    try {
      final URLConnection connection = new URL(url).openConnection();
      connection.connect();
      return true;
    }
    catch (final MalformedURLException ignored) {}
    catch (final IOException ignored) {}
    return false;
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
