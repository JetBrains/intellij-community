// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonPluginDisposable;
import com.jetbrains.python.console.PydevConsoleCommunication;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class PyDataView implements DumbAware {
  public static final String DATA_VIEWER_ID = "Plots";
  public static final String COLORED_BY_DEFAULT = "python.debugger.dataview.coloredbydefault";
  public static final String AUTO_RESIZE = "python.debugger.dataview.autoresize";
  private static final Logger LOG = Logger.getInstance(PyDataView.class);
  private static final String HELP_ID = "reference.toolWindows.PyDataView";

  @NotNull private final Project myProject;
  private JBEditorTabs myTabs;
  private final Map<ProcessHandler, TabInfo> mySelectedInfos = new ConcurrentHashMap<>();

  public PyDataView(@NotNull Project project) {
    myProject = project;
  }

  public void show(@NotNull PyDebugValue value) {
    if (ToolWindowManager.getInstance(myProject).getToolWindow(DATA_VIEWER_ID) != null) {
      showInToolwindow(value);
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        final PyDataViewDialog dialog = new PyDataViewDialog(myProject, value);
        dialog.show();
      });
    }
  }

  private void showInToolwindow(@NotNull PyDebugValue value) {
    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DATA_VIEWER_ID);
    if (window == null) {
      LOG.error("Tool window '" + DATA_VIEWER_ID + "' is not found");
      return;
    }
    window.getContentManager().getReady(this).doWhenDone(() -> {
      TabInfo selectedInfo = addTab(value.getFrameAccessor());
      PyDataViewerPanel dataViewerPanel = (PyDataViewerPanel)selectedInfo.getComponent();
      dataViewerPanel.apply(value, false);
    });
    window.show(null);
    final Content dataView = window.getContentManager().getContent(0);
    if (dataView != null) {
      window.getContentManager().setSelectedContent(dataView);
    }
  }

  public void closeTabs(Predicate<PyFrameAccessor> ifClose) {
    List<TabInfo> tabsToRemove = new ArrayList<>();
    for (TabInfo info : myTabs.getTabs()) {
      if (ifClose.test(getPanel(info).getFrameAccessor())) {
        tabsToRemove.add(info);
      }
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      for (TabInfo info : tabsToRemove) {
        myTabs.removeTab(info);
        getPanel(info).closeEditorTabs();
      }
    });
  }

  public void updateTabs(@NotNull ProcessHandler handler) {
    saveSelectedInfo();
    for (TabInfo info : myTabs.getTabs()) {
      PyDataViewerPanel panel = getPanel(info);
      PyFrameAccessor accessor = panel.getFrameAccessor();
      if (!(accessor instanceof PyDebugProcess)) {
        continue;
      }
      boolean shouldBeShown = Comparing.equal(handler, ((PyDebugProcess)accessor).getProcessHandler());
      info.setHidden(!shouldBeShown);
    }
    restoreSelectedInfo(handler);
    if (myTabs.getSelectedInfo() == null) {
      PyFrameAccessor accessor = getFrameAccessor(handler);
      if (accessor != null) {
        addTab(accessor);
      }
    }
  }

  private void restoreSelectedInfo(@NotNull ProcessHandler handler) {
    TabInfo savedSelection = mySelectedInfos.get(handler);
    if (savedSelection != null) {
      myTabs.select(savedSelection, true);
      mySelectedInfos.remove(handler);
    }
  }

  private void saveSelectedInfo() {
    TabInfo selectedInfo = myTabs.getSelectedInfo();
    if (!hasOnlyEmptyTab() && selectedInfo != null) {
      PyFrameAccessor accessor = getPanel(selectedInfo).getFrameAccessor();
      if (accessor instanceof PyDebugProcess) {
        mySelectedInfos.put(((PyDebugProcess)accessor).getProcessHandler(), selectedInfo);
      }
    }
  }

  @Nullable
  private PyFrameAccessor getFrameAccessor(@NotNull ProcessHandler handler) {
    for (PyDebugProcess process : XDebuggerManager.getInstance(myProject).getDebugProcesses(PyDebugProcess.class)) {
      if (Comparing.equal(handler, process.getProcessHandler())) {
        return process;
      }
    }
    return null;
  }

  public void closeDisconnectedFromConsoleTabs() {
    closeTabs(
      frameAccessor -> frameAccessor instanceof PydevConsoleCommunication && !isConnected(((PydevConsoleCommunication)frameAccessor)));
  }

  private static boolean isConnected(PydevConsoleCommunication accessor) {
    return !accessor.isCommunicationClosed();
  }

  public static PyDataView getInstance(@NotNull final Project project) {
    return project.getService(PyDataView.class);
  }

  public void init(@NotNull ToolWindow toolWindow) {
    myTabs = new JBRunnerTabs(myProject, PythonPluginDisposable.getInstance(myProject));
    myTabs.setDataProvider(dataId -> {
      if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
        return HELP_ID;
      }
      return null;
    });
    myTabs.getPresentation().setEmptyText(PyBundle.message("debugger.data.view.empty.text"));
    myTabs.setPopupGroup(new DefaultActionGroup(new ColoredAction()), ActionPlaces.UNKNOWN, true);
    myTabs.setTabDraggingEnabled(true);
    final Content content = ContentFactory.getInstance().createContent(myTabs, PyBundle.message("debugger.data.view.data"), false);
    content.setCloseable(true);
    toolWindow.getContentManager().addContent(content);
    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        ToolWindow window = toolWindowManager.getToolWindow(DATA_VIEWER_ID);
        if (window == null) {
          return;
        }
        if (toolWindow.isAvailable() && toolWindow.getType().equals(ToolWindowType.FLOATING) && !toolWindow.isVisible()) {
          toolWindow.setShowStripeButton(false);
          myTabs.removeAllTabs();
        }
      }
    });
  }

  public TabInfo addTab(@NotNull PyFrameAccessor frameAccessor) {
    if (hasOnlyEmptyTab()) {
      myTabs.removeTab(myTabs.getSelectedInfo());
    }
    PyDataViewerPanel panel = null;
    for (PyDataViewPanelFactory factory : PyDataViewPanelFactory.EP_NAME.getExtensionList()) {
      panel = factory.createDataViewPanel(myProject, frameAccessor);
      if (panel != null) break;
    }
    if (panel == null) {
      panel = new PyDataViewerPanel(myProject, frameAccessor);
    }
    TabInfo info = new TabInfo(panel);
    if (frameAccessor instanceof PydevConsoleCommunication) {
      info.setIcon(PythonIcons.Python.PythonConsole);
      info.setTooltipText(PyBundle.message("debugger.data.view.connected.to.python.console"));
    }
    if (frameAccessor instanceof PyDebugProcess) {
      info.setIcon(AllIcons.Toolwindows.ToolWindowDebugger);
      String name = ((PyDebugProcess)frameAccessor).getSession().getSessionName();
      info.setTooltipText(PyBundle.message("debugger.data.view.connected.to.debug.session", name));
    }
    info.setText(PyBundle.message("debugger.data.view.empty.tab"));
    info.setPreferredFocusableComponent(panel.getSliceTextField());
    info.setActions(new DefaultActionGroup(new NewViewerAction(frameAccessor)), ActionPlaces.EDITOR_TAB);
    info.setTabLabelActions(new DefaultActionGroup(new CloseViewerAction(info, frameAccessor)), ActionPlaces.EDITOR_TAB);
    panel.addListener(name -> info.setText(name));
    myTabs.addTab(info);
    myTabs.select(info, true);
    return info;
  }

  private boolean hasOnlyEmptyTab() {
    if (getVisibleTabs().size() != 1) {
      return false;
    }
    TabInfo info = myTabs.getSelectedInfo();
    if (info == null) {
      return false;
    }
    return getPanel(info).getSliceTextField().getText().isEmpty();
  }

  public List<TabInfo> getVisibleTabs() {
    return ContainerUtil.filter(myTabs.getTabs(), tabInfo -> !tabInfo.isHidden());
  }


  private class NewViewerAction extends AnAction {
    private final PyFrameAccessor myFrameAccessor;

    NewViewerAction(PyFrameAccessor frameAccessor) {
      super(PyBundle.message("debugger.data.view.view.new.container"), PyBundle.message("debugger.data.view.open.new.container.viewer"),
            AllIcons.General.Add);
      myFrameAccessor = frameAccessor;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addTab(myFrameAccessor);
    }
  }


  private class CloseViewerAction extends AnAction {
    private final TabInfo myInfo;
    private final PyFrameAccessor myFrameAccessor;

    CloseViewerAction(TabInfo info, PyFrameAccessor frameAccessor) {
      super(PyBundle.message("debugger.data.view.close.viewer"), PyBundle.message("debugger.data.view.close.selected.viewer"),
            AllIcons.Actions.Close);
      myInfo = info;
      myFrameAccessor = frameAccessor;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myTabs.removeTab(myInfo);
      if (getVisibleTabs().isEmpty()) {
        addTab(myFrameAccessor);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private class ColoredAction extends ToggleAction {
    ColoredAction() {
      super(PyBundle.messagePointer("debugger.data.view.colored"));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      PyDataViewerPanel panel = getPanel();
      if (panel == null) {
        return true;
      }
      return panel.isColored();
    }

    @Nullable
    private PyDataViewerPanel getPanel() {
      TabInfo info = myTabs.getSelectedInfo();
      if (info == null) {
        return null;
      }
      return PyDataView.getPanel(info);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      PyDataViewerPanel panel = getPanel();
      if (panel != null) {
        panel.setColored(state);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  public void changeAutoResize(boolean autoResize) {
    for (TabInfo info : myTabs.getTabs()) {
      getPanel(info).resize(autoResize);
    }
  }

  private static PyDataViewerPanel getPanel(TabInfo tabInfo) {
    return ((PyDataViewerPanel)tabInfo.getComponent());
  }
}