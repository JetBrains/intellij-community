// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.ide.actions.ToggleDistractionFreeModeAction;
import com.intellij.ide.actions.ToggleToolbarAction;
import com.intellij.ide.dnd.DnDDropHandler;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.JBTerminalWidgetListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.action.RenameTerminalSessionAction;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementManager;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementState;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author traff
 */
public class TerminalView {
  private final static Key<JBTerminalWidget> TERMINAL_WIDGET_KEY = new Key<>("TerminalWidget");

  private ToolWindow myToolWindow;
  private final Project myProject;
  private final LocalTerminalDirectRunner myTerminalRunner;
  private TerminalDockContainer myDockContainer;

  @NotNull
  public LocalTerminalDirectRunner getTerminalRunner() {
    return myTerminalRunner;
  }

  public TerminalView(@NotNull Project project) {
    myProject = project;
    myTerminalRunner = LocalTerminalDirectRunner.createTerminalRunner(myProject);
  }

  public static TerminalView getInstance(@NotNull Project project) {
    return project.getComponent(TerminalView.class);
  }

  void initToolWindow(@NotNull ToolWindow toolWindow) {
    if (myToolWindow != null) {
      return;
    }

    myToolWindow = toolWindow;
    ((ToolWindowImpl)myToolWindow).setTabActions(new DumbAwareAction("New Session", "Create new session", AllIcons.General.Add) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        newTab(null);
      }
    });
    ((ToolWindowImpl)myToolWindow).setTabDoubleClickActions(new RenameTerminalSessionAction());

    myToolWindow.setToHideOnEmptyContent(true);

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged() {
        if (toolWindow.isVisible() && myToolWindow.getContentManager().getContentCount() == 0) {
          // open a new session if all tabs were closed manually
          createNewSession(myTerminalRunner, null);
        }
      }
    });

    if (myDockContainer == null) {
      myDockContainer = new TerminalDockContainer(myToolWindow);
      Disposer.register(myProject, myDockContainer);
      DockManager.getInstance(myProject).register(myDockContainer);
    }
  }

  void restoreTabs(@Nullable TerminalArrangementState arrangementState) {
    if (arrangementState != null) {
      for (TerminalTabState tabState : arrangementState.myTabStates) {
        createNewSession(myTerminalRunner, tabState);
      }
      ContentManager contentManager = myToolWindow.getContentManager();
      Content content = contentManager.getContent(arrangementState.mySelectedTabIndex);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }
    if (myToolWindow.getContentManager().getContentCount() == 0) {
      createNewSession(myTerminalRunner, null);
    }
  }

  public void createNewSession(@NotNull AbstractTerminalRunner terminalRunner) {
    createNewSession(terminalRunner, null);
  }

  public void createNewSession(@NotNull AbstractTerminalRunner terminalRunner, @Nullable TerminalTabState tabState) {
    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window != null && window.isAvailable()) {
      // ensure TerminalToolWindowFactory.createToolWindowContent gets called
      ((ToolWindowImpl)window).ensureContentInitialized();
      createNewTab(null, terminalRunner, myToolWindow, tabState);
      window.activate(null);
    }
  }

  private Content newTab(@Nullable JBTerminalWidget terminalWidget) {
    return createNewTab(terminalWidget, myTerminalRunner, myToolWindow, null);
  }

  @NotNull
  private Content createNewTab(@Nullable JBTerminalWidget terminalWidget,
                               @NotNull AbstractTerminalRunner terminalRunner,
                               @NotNull ToolWindow toolWindow,
                               @Nullable TerminalTabState tabState) {
    final Content content = createTerminalContent(terminalRunner, toolWindow, terminalWidget, tabState);
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(content);
    new TerminalTabCloseListener(content, myProject);
    contentManager.setSelectedContent(content);
    return content;
  }

  private static String generateUniqueName(String suggestedName, List<String> tabs) {
    final Set<String> names = Sets.newHashSet(tabs);

    return UniqueNameGenerator.generateUniqueName(suggestedName, "", "", " (", ")", o -> !names.contains(o));
  }

  @NotNull
  private Content createTerminalContent(@NotNull AbstractTerminalRunner terminalRunner,
                                        @NotNull ToolWindow toolWindow,
                                        @Nullable JBTerminalWidget terminalWidget,
                                        @Nullable TerminalTabState tabState) {
    TerminalToolWindowPanel panel = new TerminalToolWindowPanel(PropertiesComponent.getInstance(myProject), toolWindow);

    String tabName = ObjectUtils.notNull(tabState != null ? tabState.myTabName : null,
                                         TerminalOptionsProvider.getInstance().getTabName());

    Content[] contents = myToolWindow.getContentManager().getContents();

    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, tabName, false);
    if (terminalWidget == null) {
      VirtualFile currentWorkingDir = getCurrentWorkingDir(tabState);
      terminalWidget = terminalRunner.createTerminalWidget(content, currentWorkingDir);
      TerminalArrangementManager.getInstance(myProject).register(terminalWidget, tabState);
      TerminalWorkingDirectoryManager.setInitialWorkingDirectory(content, currentWorkingDir);
    }
    else {
      terminalWidget.setVirtualFile(null);
      terminalWidget.moveDisposable(content);
    }

    JBTerminalWidget finalTerminalWidget = terminalWidget;
    terminalWidget.setListener(new JBTerminalWidgetListener() {
      @Override
      public void onNewSession() {
        newTab(null);
      }

      @Override
      public void onTerminalStarted() {
        if (tabState == null || StringUtil.isEmpty(tabState.myTabName)) {
          String name = finalTerminalWidget.getSettingsProvider().tabName(finalTerminalWidget.getTtyConnector(),
                                                                          finalTerminalWidget.getSessionName());

          content.setDisplayName(generateUniqueName(name, Arrays.stream(contents).map(c -> c.getDisplayName()).collect(Collectors.toList())));
        }
      }

      @Override
      public void onPreviousTabSelected() {
        if (toolWindow.getContentManager().getContentCount() > 1) {
          toolWindow.getContentManager().selectPreviousContent();
        }
      }

      @Override
      public void onNextTabSelected() {
        if (toolWindow.getContentManager().getContentCount() > 1) {
          toolWindow.getContentManager().selectNextContent();
        }
      }

      @Override
      public void onSessionClosed() {
        Content content = toolWindow.getContentManager().getSelectedContent();
        if (content != null) {
          removeTab(content, true);
        }
      }

      @Override
      public void showTabs() {
        ShowContentAction action = new ShowContentAction(toolWindow, toolWindow.getComponent(), toolWindow.getContentManager());
        DataContext dataContext = DataManager.getInstance().getDataContext(toolWindow.getComponent());
        AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext);
        action.actionPerformed(event);
      }
    });

    content.setCloseable(true);
    content.putUserData(TERMINAL_WIDGET_KEY, terminalWidget);

    panel.setContent(terminalWidget.getComponent());
    panel.addFocusListener(createFocusListener());

    panel.uiSettingsChanged(null);

    content.setPreferredFocusableComponent(terminalWidget.getPreferredFocusableComponent());

    terminalWidget.addListener(widget -> {
      ApplicationManager.getApplication().invokeLater(() -> removeTab(content, true));
    });

    return content;
  }

  @Nullable
  private static VirtualFile getCurrentWorkingDir(@Nullable TerminalTabState tabState) {
    String dir = tabState != null ? tabState.myWorkingDirectory : null;
    VirtualFile result = null;
    if (dir != null) {
      result = LocalFileSystem.getInstance().findFileByPath(dir);
    }
    return result;
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

  public void openTerminalIn(@Nullable VirtualFile fileToOpen) {
    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window != null && window.isAvailable()) {
      // ensure TerminalToolWindowFactory.createToolWindowContent gets called
      ((ToolWindowImpl)window).ensureContentInitialized();
      TerminalTabState state = new TerminalTabState();
      if (fileToOpen != null) {
        state.myWorkingDirectory = fileToOpen.getPath();
      }
      createNewSession(myTerminalRunner, state);
      window.activate(null);
    }
  }

  @Nullable
  public static JBTerminalWidget getWidgetByContent(@NotNull Content content) {
    return content.getUserData(TERMINAL_WIDGET_KEY);
  }

  public void detachWidgetAndRemoveContent(@NotNull Content content) {
    content.putUserData(TERMINAL_WIDGET_KEY, null);
    myToolWindow.getContentManager().removeContent(content, true);
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
    installDnD(window);
  }

  private static void installDnD(@NotNull ToolWindow window) {
    DnDDropHandler handler = new DnDDropHandler() {
      @Override
      public void drop(DnDEvent event) {
        TransferableWrapper tw = ObjectUtils.tryCast(event.getAttachedObject(), TransferableWrapper.class);
        if (tw != null) {
          PsiDirectory dir = getDirectory(ArrayUtil.getFirstElement(tw.getPsiElements()));
          if (dir != null && tw.getPsiElements().length == 1) {
            TerminalView view = TerminalView.getInstance(dir.getProject());
            TerminalTabState state = new TerminalTabState();
            state.myWorkingDirectory = dir.getVirtualFile().getPath();
            view.createNewSession(view.getTerminalRunner(), state);
          }
        }
      }
    };
    DnDSupport.createBuilder(window.getComponent()).setDropHandler(handler).install();
  }

  @Nullable
  private static PsiDirectory getDirectory(@Nullable PsiElement item) {
    if (item instanceof PsiFile) {
      return ((PsiFile)item).getParent();
    }
    return ObjectUtils.tryCast(item, PsiDirectory.class);
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
