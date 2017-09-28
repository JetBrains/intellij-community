/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.plots.PyPlotToolWindow;
import org.jetbrains.annotations.Nullable;
import sun.misc.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

import static com.intellij.codeInsight.documentation.DocumentationComponent.COLOR_KEY;
import static com.jetbrains.python.actions.PySciViewAction.hideDataViewer;
import static com.jetbrains.python.actions.PySciViewAction.showDataViewAsToolwindow;
import static com.jetbrains.python.debugger.containerview.PyDataView.DATA_VIEWER_ID;

@State(name = "PySciProjectComponent", storages = @Storage("other.xml"))
public class PySciProjectComponent extends AbstractProjectComponent implements PersistentStateComponent<PySciProjectComponent.State> {
  private State myState = new State();
  private ServerSocket myServerSocket;
  private Thread myPlotsThread;
  private volatile boolean myShouldAccept = true;

  private static final Logger LOG = Logger.getInstance(PySciProjectComponent.class);

  protected PySciProjectComponent(Project project) {
    super(project);
  }

  public static PySciProjectComponent getInstance(Project project) {
    return project.getComponent(PySciProjectComponent.class);
  }

  public void useSciView(boolean useSciView) {
    myState.PY_SCI_VIEW = useSciView;
  }

  public void sciViewSuggested(boolean suggested) {
    myState.PY_SCI_VIEW_SUGGESTED = suggested;
  }

  public boolean sciViewSuggested() {
    return myState.PY_SCI_VIEW_SUGGESTED;
  }

  public boolean useSciView() {
    return myState.PY_SCI_VIEW;
  }

  public void matplotlibInToolwindow(boolean inToolwindow) {
    myState.PY_MATPLOTLIB_IN_TOOLWINDOW = inToolwindow;

    if (inToolwindow) {
      if (myServerSocket == null) {
        initializeServerSocket();
      }
      if (!useSciView()) {
        showDataViewAsToolwindow(myProject);
      }
    }
    else if (!useSciView()) {
      hideDataViewer(myProject);
    }
  }

  public boolean matplotlibInToolwindow() {
    return myState.PY_MATPLOTLIB_IN_TOOLWINDOW;
  }

  @Override
  public void projectOpened() {
    final VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir == null) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (myState.PY_SCI_VIEW) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        scheme.setColor(COLOR_KEY, UIUtil.getEditorPaneBackground());

        final PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(baseDir);
        if (directory != null) {
          DocumentationManager.getInstance(myProject).showJavaDocInfo(directory, directory);
        }
      });
    }
    if (matplotlibInToolwindow()) {
      initializeServerSocket();

      if (!useSciView()) {
        StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> showDataViewAsToolwindow(myProject));
      }
    }
  }

  private void initializeServerSocket() {
    try {
      myServerSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      LOG.error("Failed to initialize server socket for python plots " + e.getMessage());
      return;
    }
    myPlotsThread = new Thread(()->{
      while (myShouldAccept) {
        processConnection();
      }
    }, "Python plots client");
    myPlotsThread.setDaemon(true);
    myPlotsThread.start();
  }

  private void processConnection() {
    try {
      final Socket socket = myServerSocket.accept();
      try {
        final InputStream stream = socket.getInputStream();
        byte[] widthBytes = new byte[4];
        final int read = stream.read(widthBytes);
        if (read != 0) {
          final int width = ByteBuffer.wrap(widthBytes).getInt();
          final byte[] raw = IOUtils.readFully(stream, -1, false);
          if (raw.length == 0) return;

          PyPlotToolWindow.getInstance(myProject).onMessage(width, raw);
          ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DATA_VIEWER_ID);
          if (window == null) {
            return;
          }
          ApplicationManager.getApplication().invokeLater(() -> {
            window.show(null);
            final Content plotsTab = window.getContentManager().getContent(1);
            if (plotsTab != null) {
              window.getContentManager().setSelectedContent(plotsTab);
            }
          });
        }
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
      finally {
        if (!socket.isClosed()) {
          socket.close();
        }
      }
    }
    catch (IOException e) {
      if (myServerSocket == null) {
        myShouldAccept = false;
      }
    }
  }

  public void stopMatplotlibCommunication() {
    if (myServerSocket != null && !myServerSocket.isClosed()) {
      try {
        myServerSocket.close();
      } catch (IOException ignore) {
      }
    }
    myShouldAccept = false;
    myServerSocket = null;
    try {
      if (myPlotsThread != null) {
        myPlotsThread.join();
      }
    }
    catch (InterruptedException ignored) {
    }
  }

  public int getPort() {
    return myServerSocket != null ? myServerSocket.getLocalPort() : -1;
  }

  @Override
  public void projectClosed() {
    stopMatplotlibCommunication();
  }

  @Nullable
  @Override
  public PySciProjectComponent.State getState() {
    return myState;
  }

  @Override
  public void loadState(PySciProjectComponent.State state) {
    myState.PY_SCI_VIEW = state.PY_SCI_VIEW;
    myState.PY_SCI_VIEW_SUGGESTED = state.PY_SCI_VIEW_SUGGESTED;
    myState.PY_MATPLOTLIB_IN_TOOLWINDOW = state.PY_MATPLOTLIB_IN_TOOLWINDOW;
  }

  public static class State {
    public boolean PY_SCI_VIEW = false;
    public boolean PY_SCI_VIEW_SUGGESTED = false;
    public boolean PY_MATPLOTLIB_IN_TOOLWINDOW = true;
  }
}
