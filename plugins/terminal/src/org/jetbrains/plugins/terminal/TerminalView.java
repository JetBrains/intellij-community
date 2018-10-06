// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ToggleDistractionFreeModeAction;
import com.intellij.ide.actions.ToggleToolbarAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author traff
 */
public class TerminalView {
  private final static String TERMINAL_FEATURE = "terminal";
  public final static Key<JBTerminalWidget> TERMINAL_WIDGET_KEY = new Key<>("TerminalWidget");

  private ToolWindow myToolWindow;
  private final Project myProject;
  private final LocalTerminalDirectRunner myTerminalRunner;
  private TerminalDockContainer myDockContainer;
  private int myNextTabNumber = 1;

  LocalTerminalDirectRunner getTerminalRunner() {
    return myTerminalRunner;
  }

  @Nullable
  private VirtualFile myFileToOpen;

  public TerminalView(Project project) {
    myProject = project;
    myTerminalRunner = LocalTerminalDirectRunner.createTerminalRunner(myProject);
  }

  public static TerminalView getInstance(@NotNull Project project) {
    return project.getComponent(TerminalView.class);
  }

  public void initTerminal(final ToolWindow toolWindow) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    myToolWindow = toolWindow;
    ((ToolWindowImpl)myToolWindow).setTabActions(new AnAction("New Session", "Create new session", AllIcons.General.Add) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        newTab(null);
      }
    });

    myToolWindow.setToHideOnEmptyContent(true);
    newTab(null);

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged() {
        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
        if (window != null) {
          boolean visible = window.isVisible();
          if (visible) {
            if (myToolWindow.getContentManager().getContentCount() == 0) {
              initTerminal(window);
            }
            else if (myFileToOpen != null) {
              //myTerminalRunner.openSessionForFile(myTerminalWidget, myFileToOpen);
            }
            myFileToOpen = null;
          }
        }
      }
    });

    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        final ContentManager contentManager = myToolWindow.getContentManager();
        for (Content tab : contentManager.getContents()) {
          Disposer.dispose(tab);
        }
      }
    });

    if (myDockContainer == null) {
      myDockContainer = new TerminalDockContainer(myToolWindow);
      Disposer.register(myProject, myDockContainer);
      DockManager.getInstance(myProject).register(myDockContainer);
    }
  }

  private Content newTab(@Nullable JBTerminalWidget terminalWidget) {
    final Content content = createTerminalContent(myTerminalRunner, myToolWindow, terminalWidget);
    final ContentManager contentManager = myToolWindow.getContentManager();
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
    return content;
  }

  private Content createTerminalContent(@NotNull AbstractTerminalRunner terminalRunner,
                                        final @NotNull ToolWindow toolWindow,
                                        @Nullable JBTerminalWidget terminalWidget) {
    TerminalToolWindowPanel panel = new TerminalToolWindowPanel(PropertiesComponent.getInstance(myProject), toolWindow);

    String name = (terminalWidget != null
                   ? terminalWidget.getSettingsProvider().tabName(terminalWidget.getTtyConnector(), terminalWidget.getSessionName())
                   : TerminalOptionsProvider.Companion.getInstance().getTabName()) + " " + (myNextTabNumber++);
    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, name, false);
    if (terminalWidget == null) {
      terminalWidget = terminalRunner.createTerminalWidget(content);
    }
    else {
      terminalWidget.setVirtualFile(null);
      terminalWidget.moveDisposable(content);
    }

    content.setCloseable(true);
    content.putUserData(TERMINAL_WIDGET_KEY, terminalWidget);

    terminalWidget.setListener(() -> newTab(null));
    panel.setContent(terminalWidget.getComponent());
    panel.addFocusListener(createFocusListener());

    panel.uiSettingsChanged(null);

    content.setPreferredFocusableComponent(terminalWidget.getPreferredFocusableComponent());

    terminalWidget.addListener(widget -> {
      ApplicationManager.getApplication().invokeLater(() -> removeTab(content, true));
    });

    return content;
  }

  private void removeTab(Content content, boolean keepFocus) {
    final ContentManager contentManager = myToolWindow.getContentManager();
    contentManager.removeContent(content, true, keepFocus, keepFocus);
  }

  private FocusListener createFocusListener() {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus();
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    };
  }

  private JComponent getComponentToFocus() {
    Content selectedContent = myToolWindow.getContentManager().getSelectedContent();
    if (selectedContent != null) {
      return selectedContent.getPreferredFocusableComponent();
    }
    else {
      return myToolWindow.getComponent();
    }
  }

  @Nullable
  public VirtualFile getFileToOpen() {
    return myFileToOpen;
  }

  public void setFileToOpen(@Nullable VirtualFile fileToOpen) {
    myFileToOpen = fileToOpen;
  }

  /**
   * @author traff
   */
  public class TerminalDockContainer implements DockContainer {
    private final ToolWindow myToolWindow;

    TerminalDockContainer(ToolWindow toolWindow) {
      myToolWindow = toolWindow;
    }

    @Override
    public RelativeRectangle getAcceptArea() {
      return new RelativeRectangle(myToolWindow.getComponent());
    }

    @Override
    public RelativeRectangle getAcceptAreaFallback() {
      return getAcceptArea();
    }

    @NotNull
    @Override
    public ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
      return isTerminalSessionContent(content) ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
    }

    @Override
    public JComponent getContainerComponent() {
      return myToolWindow.getComponent();
    }

    @Override
    public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
      if (isTerminalSessionContent(content)) {
        TerminalSessionVirtualFileImpl terminalFile = (TerminalSessionVirtualFileImpl)content.getKey();
        String name = terminalFile.getName();
        Content newContent = newTab(terminalFile.getTerminalWidget());
        newContent.setDisplayName(name);
      }
    }

    private boolean isTerminalSessionContent(DockableContent content) {
      return content.getKey() instanceof TerminalSessionVirtualFileImpl;
    }

    @Override
    public void closeAll() {

    }

    @Override
    public void addListener(Listener listener, Disposable parent) {

    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Nullable
    @Override
    public Image startDropOver(@NotNull DockableContent content, RelativePoint point) {
      return null;
    }

    @Nullable
    @Override
    public Image processDropOver(@NotNull DockableContent content, RelativePoint point) {
      return null;
    }

    @Override
    public void resetDropOver(@NotNull DockableContent content) {

    }

    @Override
    public boolean isDisposeWhenEmpty() {
      return false;
    }

    @Override
    public void showNotify() {

    }

    @Override
    public void hideNotify() {

    }

    @Override
    public void dispose() {

    }
  }
}


class TerminalToolWindowPanel extends SimpleToolWindowPanel implements UISettingsListener {
  private final PropertiesComponent myPropertiesComponent;
  private final ToolWindow myWindow;

  TerminalToolWindowPanel(PropertiesComponent propertiesComponent, ToolWindow window) {
    super(false, true);
    myPropertiesComponent = propertiesComponent;
    myWindow = window;
  }

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    updateDFState();
  }

  private void updateDFState() {
    if (isDfmSupportEnabled()) {
      setDistractionFree(shouldMakeDistractionFree());
    }
  }

  private void setDistractionFree(boolean isDistractionFree) {
    boolean isVisible = !isDistractionFree;
    setToolbarVisible(isVisible);
    setToolWindowHeaderVisible(isVisible);
  }

  private void setToolbarVisible(boolean isVisible) {
    ToggleToolbarAction.setToolbarVisible(myWindow, myPropertiesComponent, isVisible);
  }

  private void setToolWindowHeaderVisible(boolean isVisible) {
    InternalDecorator decorator = ((ToolWindowEx)myWindow).getDecorator();
    decorator.setHeaderVisible(isVisible);
  }

  private boolean shouldMakeDistractionFree() {
    return !myWindow.getAnchor().isHorizontal() && ToggleDistractionFreeModeAction.isDistractionFreeModeEnabled();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    updateDFState();
  }

  private static boolean isDfmSupportEnabled() {
    return Registry.get("terminal.distraction.free").asBoolean();
  }
}
