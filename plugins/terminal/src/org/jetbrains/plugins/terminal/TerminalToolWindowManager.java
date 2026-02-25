// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.google.common.collect.Sets;
import com.intellij.ide.DataManager;
import com.intellij.idea.AppMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.JBTerminalWidgetListener;
import com.intellij.terminal.TerminalTitle;
import com.intellij.terminal.TerminalTitleListener;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.terminal.ui.TerminalWidgetKt;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.plugins.terminal.action.MoveTerminalToolWindowTabLeftAction;
import org.jetbrains.plugins.terminal.action.MoveTerminalToolWindowTabRightAction;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementState;
import org.jetbrains.plugins.terminal.arrangement.TerminalCommandHistoryManager;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;
import org.jetbrains.plugins.terminal.classic.ClassicTerminalTabCloseListener;
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector;
import org.jetbrains.plugins.terminal.ui.TerminalContainer;

import javax.swing.JComponent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@SuppressWarnings("DeprecatedIsStillUsed")
@Service(Service.Level.PROJECT)
public final class TerminalToolWindowManager implements Disposable {
  private static final Key<TerminalWidget> TERMINAL_WIDGET_KEY = new Key<>("TerminalWidget");
  private static final Logger LOG = Logger.getInstance(TerminalToolWindowManager.class);
  private static final Key<AbstractTerminalRunner<?>> RUNNER_KEY = Key.create("RUNNER_KEY");

  private ToolWindowEx myToolWindow;
  private final Project myProject;
  private final AbstractTerminalRunner<?> myTerminalRunner;
  private final Map<TerminalWidget, TerminalContainer> myContainerByWidgetMap = new HashMap<>();

  public @NotNull AbstractTerminalRunner<?> getTerminalRunner() {
    return myTerminalRunner;
  }


  public ToolWindow getToolWindow() {
    return myToolWindow;
  }

  public TerminalToolWindowManager(@NotNull Project project) {
    myProject = project;
    myTerminalRunner = DefaultTerminalRunnerFactory.getInstance().create(project);
  }

  @Override
  public void dispose() {
  }

  /**
   * @deprecated use {@link #getTerminalWidgets()} instead
   */
  @ApiStatus.Internal
  @Deprecated
  public @Unmodifiable Set<JBTerminalWidget> getWidgets() {
    return ContainerUtil.map2SetNotNull(myContainerByWidgetMap.keySet(),
                                        widget -> JBTerminalWidget.asJediTermWidget(widget));
  }

  public @NotNull Set<TerminalWidget> getTerminalWidgets() {
    return Collections.unmodifiableSet(myContainerByWidgetMap.keySet());
  }

  private final List<Consumer<TerminalWidget>> myTerminalSetupHandlers = new CopyOnWriteArrayList<>();

  public void addNewTerminalSetupHandler(@NotNull Consumer<TerminalWidget> listener, @NotNull Disposable parentDisposable) {
    myTerminalSetupHandlers.add(listener);
    if (!Disposer.tryRegister(parentDisposable, () -> { myTerminalSetupHandlers.remove(listener); })) {
      myTerminalSetupHandlers.remove(listener);
    }
  }

  public static TerminalToolWindowManager getInstance(@NotNull Project project) {
    return project.getService(TerminalToolWindowManager.class);
  }

  void initToolWindow(@NotNull ToolWindowEx toolWindow) {
    if (myToolWindow != null) {
      LOG.error("Terminal tool window already initialized");
      return;
    }
    myToolWindow = toolWindow;
  }

  /** Restores tabs for Classic Terminal and New Terminal Gen1. */
  void restoreTabsLocal(@Nullable TerminalArrangementState arrangementState) {
    ContentManager contentManager = myToolWindow.getContentManager();

    if (arrangementState != null) {
      for (TerminalTabState tabState : arrangementState.myTabStates) {
        TerminalEngine engine = TerminalOptionsProvider.getInstance().getTerminalEngine();
        createNewSession(null, myTerminalRunner, engine, tabState, false, true);
      }

      Content content = contentManager.getContent(arrangementState.mySelectedTabIndex);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }
  }

  //------------ Classic Terminal tab creation API methods start ------------------------------------

  /**
   * Creates the <b>Classic</b> terminal tab regardless of the {@link TerminalEngine} state in the {@link TerminalOptionsProvider}
   *
   * @deprecated please use the Reworked Terminal API instead: {@link com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager}
   * For example:
   * <pre>{@code
   * TerminalToolWindowTabsManager.getInstance(project)
   *   .createTabBuilder()
   *   .workingDirectory(workingDirectory)
   *   .tabName(tabName)
   *   .createTab()
   * }</pre>
   */
  @Deprecated
  public @NotNull TerminalWidget createNewSession() {
    return createNewSession(null, myTerminalRunner, TerminalEngine.CLASSIC, null, true, true);
  }

  /** Creates the <b>Classic</b> terminal tab regardless of the {@link TerminalEngine} state in the {@link TerminalOptionsProvider} */
  public void createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner) {
    createNewSession(null, terminalRunner, TerminalEngine.CLASSIC, null, true, true);
  }

  /** Creates the <b>Classic</b> terminal tab regardless of the {@link TerminalEngine} state in the {@link TerminalOptionsProvider} */
  public void createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner, @Nullable TerminalTabState tabState) {
    createNewSession(null, terminalRunner, TerminalEngine.CLASSIC, tabState, true, true);
  }

  @ApiStatus.Experimental
  public @NotNull TerminalWidget createNewSession(@Nullable AbstractTerminalRunner<?> terminalRunner,
                                                  @Nullable TerminalTabState tabState,
                                                  @Nullable ContentManager contentManager) {
    var runner = terminalRunner != null ? terminalRunner : myTerminalRunner;
    return createNewSession(contentManager, runner, TerminalEngine.CLASSIC, tabState, true, true);
  }

  /**
   * Creates the <b>Classic</b> terminal tab regardless of the {@link TerminalEngine} state in the {@link TerminalOptionsProvider}
   *
   * @deprecated please use the Reworked Terminal API instead: {@link com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager}
   * For example:
   * <pre>{@code
   * TerminalToolWindowTabsManager.getInstance(project)
   *   .createTabBuilder()
   *   .workingDirectory(workingDirectory)
   *   .tabName(tabName)
   *   .createTab()
   * }</pre>
   */
  @Deprecated
  public @NotNull TerminalWidget createShellWidget(@Nullable String workingDirectory,
                                                   @Nullable @Nls String tabName,
                                                   boolean requestFocus,
                                                   boolean deferSessionStartUntilUiShown) {
    return createNewSession(workingDirectory, tabName, null, requestFocus, deferSessionStartUntilUiShown);
  }

  /** Creates the <b>Classic</b> terminal tab regardless of the {@link TerminalEngine} state in the {@link TerminalOptionsProvider} */
  public @NotNull Content newTab(@NotNull ToolWindow toolWindow, @Nullable TerminalWidget terminalWidget) {
    return createNewTab(null, terminalWidget, myTerminalRunner, TerminalEngine.CLASSIC, null, true, true);
  }

  /**
   * Creates the <b>Classic</b> terminal tab regardless of the {@link TerminalEngine} state in the {@link TerminalOptionsProvider}
   *
   * @deprecated please use the Reworked Terminal API instead: {@link com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager}
   * For example:
   * <pre>{@code
   * TerminalToolWindowTabsManager.getInstance(project)
   *   .createTabBuilder()
   *   .workingDirectory(fileToOpen.path)
   *   .createTab()
   * }</pre>
   */
  @Deprecated
  public void openTerminalIn(@Nullable VirtualFile fileToOpen) {
    TerminalTabState state = new TerminalTabState();
    if (fileToOpen != null) {
      state.myWorkingDirectory = fileToOpen.getPath();
    }
    createNewSession(null, myTerminalRunner, TerminalEngine.CLASSIC, state, true, true);
  }

  //------------ Classic Terminal tab creation API methods end --------------------------------------

  /** Creates the <b>Classic</b> terminal tab regardless of the {@link TerminalEngine} state in the {@link TerminalOptionsProvider} */
  @ApiStatus.Internal
  public @NotNull TerminalWidget createNewSession(@Nullable String workingDirectory,
                                                  @Nullable @Nls String tabName,
                                                  @Nullable List<String> shellCommand,
                                                  boolean requestFocus,
                                                  boolean deferSessionStartUntilUiShown) {
    TerminalTabState tabState = new TerminalTabState();
    tabState.myTabName = tabName;
    tabState.myWorkingDirectory = workingDirectory;
    tabState.myShellCommand = shellCommand;
    return createNewSession(null, myTerminalRunner, TerminalEngine.CLASSIC, tabState,
                            requestFocus, deferSessionStartUntilUiShown);
  }

  /**
   * Creates the new tab with the terminal implementation of the specified {@link TerminalEngine}.
   * Note, that for {@link TerminalEngine#NEW_TERMINAL} and {@link TerminalEngine#REWORKED} some additional checks may be performed.
   * For example, for New UI or RemDev.
   * If these checks are not satisfied, the effective engine will be {@link TerminalEngine#CLASSIC}.
   */
  @ApiStatus.Internal
  public @NotNull TerminalWidget createNewTab(@NotNull TerminalEngine preferredEngine,
                                              @Nullable TerminalTabState tabState,
                                              @Nullable ContentManager contentManager) {
    return createNewSession(contentManager, myTerminalRunner, preferredEngine, tabState, true, true);
  }

  /**
   * @param contentManager pass child content manager of the Terminal tool window to open the tab in the specific split area.
   * If null is provided, the tab will be opened in the top-left splitter.
   * If there are no splits, the tab will be opened in the main tool window area.
   */
  private @NotNull TerminalWidget createNewSession(@Nullable ContentManager contentManager,
                                                   @NotNull AbstractTerminalRunner<?> terminalRunner,
                                                   @NotNull TerminalEngine preferredEngine,
                                                   @Nullable TerminalTabState tabState,
                                                   boolean requestFocus,
                                                   boolean deferSessionStartUntilUiShown) {
    Content content = createNewTab(contentManager, null, terminalRunner, preferredEngine,
                                   tabState, requestFocus, deferSessionStartUntilUiShown);
    return Objects.requireNonNull(content.getUserData(TERMINAL_WIDGET_KEY));
  }

  private @NotNull ToolWindow getOrInitToolWindow() {
    ToolWindow toolWindow = myToolWindow;
    if (toolWindow == null) {
      toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
      Objects.requireNonNull(toolWindow).getContentManager(); // to call #initToolWindow
      LOG.assertTrue(toolWindow == myToolWindow);
    }
    return toolWindow;
  }

  @ApiStatus.Internal
  public @NotNull Content createNewTab(@Nullable ContentManager contentManager,
                                       @Nullable TerminalWidget terminalWidget,
                                       @NotNull AbstractTerminalRunner<?> terminalRunner,
                                       @NotNull TerminalEngine preferredEngine,
                                       @Nullable TerminalTabState tabState,
                                       boolean requestFocus,
                                       boolean deferSessionStartUntilUiShown) {
    ToolWindow toolWindow = getOrInitToolWindow();
    TerminalStartupMoment startupMoment = requestFocus && deferSessionStartUntilUiShown ? new TerminalStartupMoment() : null;
    Content content = createTerminalContent(terminalRunner, preferredEngine, terminalWidget, tabState,
                                            deferSessionStartUntilUiShown, startupMoment);

    ContentManager manager = contentManager != null ? contentManager : toolWindow.getContentManager();
    manager.addContent(content);
    Runnable selectRunnable = () -> {
      manager.setSelectedContent(content, requestFocus);
    };
    if (requestFocus && !toolWindow.isActive()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Activating " + toolWindow.getId() + " tool window");
      }
      toolWindow.activate(selectRunnable, true, true);
    }
    else {
      selectRunnable.run();
    }

    int tabsCount = toolWindow.getContentManager().getContentsRecursively().size();
    ReworkedTerminalUsageCollector.logTabOpened(myProject, tabsCount);

    return content;
  }

  private static @Nls String generateUniqueName(@Nls String suggestedName, List<@Nls String> tabs) {
    final Set<String> names = Sets.newHashSet(tabs);

    return UniqueNameGenerator.generateUniqueName(suggestedName, "", "", " (", ")", o -> !names.contains(o));
  }

  /**
   * Creates the {@link Content} with the terminal implementation of the specified {@link TerminalEngine}.
   * Note that the created content is not added to the tool window's {@link ContentManager} yet.
   */
  @ApiStatus.Internal
  public @NotNull Content createTerminalContent(@NotNull AbstractTerminalRunner<?> terminalRunner,
                                                @NotNull TerminalEngine preferredEngine,
                                                @Nullable TerminalWidget terminalWidget,
                                                @Nullable TerminalTabState tabState,
                                                boolean deferSessionStartUntilUiShown,
                                                @Nullable TerminalStartupMoment startupMoment) {
    ToolWindow toolWindow = getOrInitToolWindow();
    TerminalToolWindowPanel panel = new TerminalToolWindowPanel();

    Content content = ContentFactory.getInstance().createContent(panel, null, false);

    TerminalWidget widget = terminalWidget;
    if (widget == null) {
      String currentWorkingDir = terminalRunner.getCurrentWorkingDir(tabState);
      NullableLazyValue<Path> commandHistoryFileLazyValue = NullableLazyValue.atomicLazyNullable(() -> {
        return TerminalCommandHistoryManager.getInstance().getOrCreateCommandHistoryFile(
          tabState != null ? tabState.myCommandHistoryFileName : null,
          myProject
        );
      });
      ShellStartupOptions startupOptions = new ShellStartupOptions.Builder()
        .workingDirectory(currentWorkingDir)
        .shellCommand(tabState != null ? tabState.myShellCommand : null)
        .commandHistoryFileProvider(() -> commandHistoryFileLazyValue.getValue())
        .startupMoment(startupMoment)
        .build();
      widget = startShellTerminalWidget(terminalRunner, startupOptions, preferredEngine, deferSessionStartUntilUiShown, content);
      widget.getTerminalTitle().change(state -> {
        if (state.getDefaultTitle() == null) {
          state.setDefaultTitle(terminalRunner.getDefaultTabTitle());
        }
        return Unit.INSTANCE;
      });
      TerminalWorkingDirectoryManager.setInitialWorkingDirectory(content, currentWorkingDir);
    }
    else {
      TerminalWidgetKt.setNewParentDisposable(terminalWidget, content);
    }

    if (tabState != null && tabState.myTabName != null) {
      widget.getTerminalTitle().change(state -> {
        if (tabState.myIsUserDefinedTabTitle) {
          state.setUserDefinedTitle(tabState.myTabName);
        }
        else {
          state.setDefaultTitle(tabState.myTabName);
        }
        return null;
      });
    }
    updateTabTitle(widget, toolWindow, content);
    setupTerminalWidget(toolWindow, terminalRunner, widget, content);

    content.setCloseable(true);
    content.putUserData(TERMINAL_WIDGET_KEY, widget);
    content.putUserData(RUNNER_KEY, terminalRunner);

    TerminalContainer container = new TerminalContainer(myProject, content, widget, this);
    panel.setContent(container.getWrapperPanel());
    panel.addFocusListener(createFocusListener(toolWindow));

    TerminalWidget finalWidget = widget;
    myTerminalSetupHandlers.forEach(consumer -> consumer.accept(finalWidget));

    content.setPreferredFocusedComponent(() -> finalWidget.getPreferredFocusableComponent());
    ClassicTerminalTabCloseListener.install(content, myProject, content);

    return content;
  }

  private void setupTerminalWidget(@NotNull ToolWindow toolWindow,
                                   @NotNull AbstractTerminalRunner<?> runner,
                                   @NotNull TerminalWidget widget,
                                   @NotNull Content content) {
    MoveTerminalToolWindowTabLeftAction moveTabLeftAction = new MoveTerminalToolWindowTabLeftAction();
    MoveTerminalToolWindowTabRightAction moveTabRightAction = new MoveTerminalToolWindowTabRightAction();

    widget.getTerminalTitle().addTitleListener(new TerminalTitleListener() {
      @Override
      public void onTitleChanged(@NotNull TerminalTitle terminalTitle) {
        ApplicationManager.getApplication().invokeLater(() -> {
          updateTabTitle(widget, toolWindow, content);
        }, myProject.getDisposed());
      }
    }, content);
    JBTerminalWidget terminalWidget = JBTerminalWidget.asJediTermWidget(widget);
    if (terminalWidget == null) return;

    terminalWidget.setListener(new JBTerminalWidgetListener() {
      @Override
      public void onNewSession() {
        createNewSession(content.getManager(), myTerminalRunner, TerminalEngine.CLASSIC, null, true, true);
      }

      @Override
      public void onTerminalStarted() {
      }

      @Override
      public void onPreviousTabSelected() {
        var contentManager = content.getManager();
        if (contentManager != null && contentManager.getContentCount() > 1) {
          contentManager.selectPreviousContent();
        }
      }

      @Override
      public void onNextTabSelected() {
        var contentManager = content.getManager();
        if (contentManager != null && contentManager.getContentCount() > 1) {
          contentManager.selectNextContent();
        }
      }

      @Override
      public void onSessionClosed() {
        TerminalContainer container = getContainer(widget);
        if (container != null) {
          container.closeAndHide();
        }
      }

      @Override
      public void showTabs() {
        performAction("ShowContent");
      }

      @Override
      public void moveTabRight() {
        moveTabRightAction.move(content, myProject);
      }

      @Override
      public void moveTabLeft() {
        moveTabLeftAction.move(content, myProject);
      }

      @Override
      public boolean canMoveTabRight() {
        return moveTabRightAction.isAvailable(content);
      }

      @Override
      public boolean canMoveTabLeft() {
        return moveTabLeftAction.isAvailable(content);
      }

      @Override
      public boolean canSplit(boolean vertically) {
        var actionId = vertically ? "TW.SplitRight" : "TW.SplitDown";
        return isActionEnabled(actionId);
      }

      @Override
      public void split(boolean vertically) {
        var actionId = vertically ? "TW.SplitRight" : "TW.SplitDown";
        performAction(actionId);
      }

      @Override
      public boolean isGotoNextSplitTerminalAvailable() {
        return isActionEnabled("TW.MoveToNextSplitter");
      }

      @Override
      public void gotoNextSplitTerminal(boolean forward) {
        var actionId = forward ? "TW.MoveToNextSplitter" : "TW.MoveToPreviousSplitter";
        performAction(actionId);
      }

      private boolean isActionEnabled(@NotNull String actionId) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action == null) return false;

        var event = createActionEvent(action);
        AnActionResult result = ActionUtil.updateAction(action, event);
        if (!result.isPerformed()) return false;

        return event.getPresentation().isEnabled();
      }

      private void performAction(@NotNull String actionId) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action == null) return;

        var event = createActionEvent(action);
        ActionUtil.performAction(action, event);
      }

      private @NotNull AnActionEvent createActionEvent(@NotNull AnAction action) {
        var dataContext = DataManager.getInstance().getDataContext(widget.getComponent());
        return AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
      }
    });
  }

  private static void updateTabTitle(@NotNull TerminalWidget widget, @NotNull ToolWindow toolWindow, @NotNull Content content) {
    TerminalTitle title = widget.getTerminalTitle();
    String titleString = title.buildTitle();
    List<String> tabs = toolWindow.getContentManager().getContentsRecursively().stream()
      .filter(c -> c != content)
      .map(c -> c.getDisplayName()).toList();
    String generatedName = generateUniqueName(titleString, tabs);

    content.setDisplayName(generatedName);
    title.change((state) -> {
      state.setDefaultTitle(generatedName);
      return Unit.INSTANCE;
    });
  }

  public void register(@NotNull TerminalContainer terminalContainer) {
    myContainerByWidgetMap.put(terminalContainer.getTerminalWidget(), terminalContainer);
  }

  public void unregister(@NotNull TerminalContainer terminalContainer) {
    myContainerByWidgetMap.remove(terminalContainer.getTerminalWidget());
    if (terminalContainer.getContent().getUserData(TERMINAL_WIDGET_KEY) == terminalContainer.getTerminalWidget()) {
      terminalContainer.getContent().putUserData(TERMINAL_WIDGET_KEY, findWidgetForContent(terminalContainer.getContent()));
    }
  }

  private @Nullable TerminalWidget findWidgetForContent(@NotNull Content content) {
    TerminalWidget any = null;
    for (Map.Entry<TerminalWidget, TerminalContainer> entry : myContainerByWidgetMap.entrySet()) {
      if (entry.getValue().getContent() == content) {
        TerminalWidget terminalWidget = entry.getKey();
        any = terminalWidget;
        if (terminalWidget.hasFocus()) {
          return terminalWidget;
        }
      }
    }
    return any;
  }

  public @Nullable TerminalContainer getContainer(@NotNull TerminalWidget terminalWidget) {
    return myContainerByWidgetMap.get(terminalWidget);
  }

  public void closeTab(@NotNull Content content) {
    var manager = content.getManager();
    if (manager != null) {
      manager.removeContent(content, true, true, true);
    }
  }

  private static @NotNull FocusListener createFocusListener(@NotNull ToolWindow toolWindow) {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus(toolWindow);
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    };
  }

  private static @Nullable JComponent getComponentToFocus(@NotNull ToolWindow toolWindow) {
    Content selectedContent = toolWindow.getContentManager().getSelectedContent();
    if (selectedContent != null) {
      return selectedContent.getPreferredFocusableComponent();
    }
    else {
      return toolWindow.getComponent();
    }
  }

  private @NotNull TerminalWidget startShellTerminalWidget(@NotNull AbstractTerminalRunner<?> terminalRunner,
                                                           @NotNull ShellStartupOptions startupOptions,
                                                           @NotNull TerminalEngine preferredEngine,
                                                           boolean deferSessionStartUntilUiShown,
                                                           @NotNull Disposable parentDisposable) {
    TerminalWidget widget;

    boolean isAnyRemoteDev = PlatformUtils.isJetBrainsClient() || AppMode.isRemoteDevHost();
    // Run New Terminal (Gen1) only if the default terminal runner was specified.
    // Do not enable it in remote dev since it is not adapted to this mode.
    if (preferredEngine == TerminalEngine.NEW_TERMINAL &&
        ExperimentalUI.isNewUI() &&
        terminalRunner == myTerminalRunner &&
        !isAnyRemoteDev) {
      // Use the specific runner that will start the terminal with the corresponding shell integration.
      var runner = new LocalBlockTerminalRunner(myProject);
      widget = runner.startShellTerminalWidget(parentDisposable, startupOptions, deferSessionStartUntilUiShown);
    }
    // Otherwise start the Classic Terminal with the provided runner.
    else {
      widget = terminalRunner.startShellTerminalWidget(parentDisposable, startupOptions, deferSessionStartUntilUiShown);
    }

    return widget;
  }

  public static @Nullable JBTerminalWidget getWidgetByContent(@NotNull Content content) {
    TerminalWidget data = content.getUserData(TERMINAL_WIDGET_KEY);
    return data != null ? JBTerminalWidget.asJediTermWidget(data) : null;
  }

  public static @Nullable TerminalWidget findWidgetByContent(@NotNull Content content) {
    return content.getUserData(TERMINAL_WIDGET_KEY);
  }

  public static @Nullable AbstractTerminalRunner<?> getRunnerByContent(@NotNull Content content) {
    return content.getUserData(RUNNER_KEY);
  }

  public void detachWidgetAndRemoveContent(@NotNull Content content) {
    ContentManager contentManager = content.getManager();
    if (contentManager == null) {
      throw new IllegalStateException("Content manager is null for " + content);
    }
    TerminalTabCloseListener.Companion.executeContentOperationSilently(content, () -> {
      contentManager.removeContent(content, true);
      return Unit.INSTANCE;
    });
    content.putUserData(TERMINAL_WIDGET_KEY, null);

    if (myToolWindow != null && myToolWindow.getContentManager().isEmpty()) {
      myToolWindow.hide();
    }
  }

  public static boolean isInTerminalToolWindow(@NotNull JBTerminalWidget widget) {
    DataContext dataContext = DataManager.getInstance().getDataContext(widget.getTerminalPanel());
    ToolWindow toolWindow = dataContext.getData(PlatformDataKeys.TOOL_WINDOW);
    return isTerminalToolWindow(toolWindow);
  }

  public static boolean isTerminalToolWindow(@Nullable ToolWindow toolWindow) {
    return toolWindow != null && TerminalToolWindowFactory.TOOL_WINDOW_ID.equals(toolWindow.getId());
  }

  /**
   * @deprecated use {@link #createShellWidget(String, String, boolean, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull ShellTerminalWidget createLocalShellWidget(@Nullable String workingDirectory, @Nullable @Nls String tabName) {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(createShellWidget(workingDirectory, tabName, true, true));
  }

  /**
   * @deprecated use {@link #createShellWidget(String, String, boolean, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull ShellTerminalWidget createLocalShellWidget(@Nullable String workingDirectory,
                                                             @Nullable @Nls String tabName,
                                                             boolean requestFocus) {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(createShellWidget(workingDirectory, tabName, requestFocus, true));
  }

  /**
   * @deprecated use {@link #createShellWidget(String, String, boolean, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull ShellTerminalWidget createLocalShellWidget(@Nullable String workingDirectory,
                                                             @Nullable @Nls String tabName,
                                                             boolean requestFocus,
                                                             boolean deferSessionStartUntilUiShown) {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(
      createShellWidget(workingDirectory, tabName, requestFocus, deferSessionStartUntilUiShown));
  }
}