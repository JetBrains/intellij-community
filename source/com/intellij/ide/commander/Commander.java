package com.intellij.ide.commander;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.Disposable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.ui.AutoScrollToSourceHandler;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;

/**
 * @author Eugene Belyaev
 */
public class Commander extends JPanel implements JDOMExternalizable, DataProvider, ProjectComponent {
  private Project myProject;
  private CommanderPanel myLeftPanel;
  private CommanderPanel myRightPanel;
  private Splitter mySplitter;
  private ListSelectionListener mySelectionListener;
  private ListDataListener myListDataListener;
  public boolean MOVE_FOCUS = true; // internal option: move focus to editor when class/file/...etc. is created
  private Element myElement;
  private FocusWatcher myFocusWatcher;
  private CommanderHistory myHistory;
  private boolean myAutoScrollMode = false;
  private final ToolWindowManager myToolWindowManager;
  private final java.util.List<Disposable> myDisposables = new ArrayList<Disposable>();
  @NonNls private static final String ACTION_BACKCOMMAND = "backCommand";
  @NonNls private static final String ACTION_FORWARDCOMMAND = "forwardCommand";
  @NonNls private static final String ELEMENT_LEFTPANEL = "leftPanel";
  @NonNls private static final String ATTRIBUTE_MOVE_FOCUS = "MOVE_FOCUS";
  @NonNls private static final String ELEMENT_OPTION = "OPTION";
  @NonNls private static final String ATTRIBUTE_PROPORTION = "proportion";
  @NonNls private static final String ELEMENT_SPLITTER = "splitter";
  @NonNls private static final String ELEMENT_RIGHTPANEL = "rightPanel";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";

  /**
   * FOR USE IN TESTS ONLY!!!
   * @param project
   * @param keymapManager
   */
  public Commander(final Project project, KeymapManager keymapManager) {
    this(project, keymapManager, null);
  }

  public Commander(final Project project, KeymapManager keymapManager, final ToolWindowManager toolWindowManager) {
    super(new BorderLayout());
    myProject = project;
    myToolWindowManager = toolWindowManager;

    final AbstractAction backAction = new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        myHistory.back();
      }
    };
    final AbstractAction fwdAction = new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        myHistory.forward();
      }
    };
    final ActionMap actionMap = getActionMap();
    actionMap.put(ACTION_BACKCOMMAND, backAction);
    actionMap.put(ACTION_FORWARDCOMMAND, fwdAction);
    final KeyStroke[] backStrokes = getKeyStrokes(IdeActions.ACTION_GOTO_BACK, keymapManager);
    for (int idx = 0; idx < backStrokes.length; idx++) {
      KeyStroke stroke = backStrokes[idx];
      //getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "backCommand");
      //getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, "backCommand");
      registerKeyboardAction(backAction, ACTION_BACKCOMMAND, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
      registerKeyboardAction(backAction, ACTION_BACKCOMMAND, stroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    final KeyStroke[] fwdStrokes = getKeyStrokes(IdeActions.ACTION_GOTO_FORWARD, keymapManager);
    for (int idx = 0; idx < fwdStrokes.length; idx++) {
      KeyStroke stroke = fwdStrokes[idx];
      //getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "forwardCommand");
      //getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, "forwardCommand");
      registerKeyboardAction(fwdAction, ACTION_FORWARDCOMMAND, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
      registerKeyboardAction(fwdAction, ACTION_FORWARDCOMMAND, stroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    myHistory = new CommanderHistory(this);
  }

  public static Commander getInstance(final Project project) {
    return project.getComponent(Commander.class);
  }

  public CommanderHistory getCommandHistory() {
    return myHistory;
  }

  public void initComponent() {
  }

  private void processConfigurationElement() {
    if (myElement == null) return;

    Element element;

    element = myElement.getChild(ELEMENT_LEFTPANEL);
    if (element != null) {
      final PsiElement parentElement = readParentElement(element);
      if (parentElement != null) {
        myLeftPanel.getBuilder().enterElement(parentElement, PsiUtil.getVirtualFile(parentElement));
      }
    }

    element = myElement.getChild(ELEMENT_RIGHTPANEL);
    if (element != null) {
      final PsiElement parentElement = readParentElement(element);
      if (parentElement != null) {
        myRightPanel.getBuilder().enterElement(parentElement, PsiUtil.getVirtualFile(parentElement));
      }
    }

    element = myElement.getChild(ELEMENT_SPLITTER);
    if (element != null) {
      final String attribute = element.getAttributeValue(ATTRIBUTE_PROPORTION);
      if (attribute != null) {
        try {
          final float proportion = Float.valueOf(attribute).floatValue();
          if (proportion >= 0 && proportion <= 1) {
            mySplitter.setProportion(proportion);
          }
        } catch (NumberFormatException e) {
        }
      }
    }

    element = myElement.getChild(ELEMENT_OPTION);
    if (element != null) {
      //noinspection HardCodedStringLiteral
      MOVE_FOCUS = !"false".equals(element.getAttributeValue(ATTRIBUTE_MOVE_FOCUS));
    }

    myLeftPanel.setActive(false);
    myRightPanel.setActive(false);

    myElement = null;
  }

  private KeyStroke[] getKeyStrokes(String actionId, KeymapManager keymapManager) {
    final Shortcut[] shortcuts = keymapManager.getActiveKeymap().getShortcuts(actionId);
    final java.util.List<KeyStroke> strokes = new ArrayList<KeyStroke>();
    for (int i = 0; i < shortcuts.length; i++) {
      final Shortcut shortcut = shortcuts[i];
      if (shortcut instanceof KeyboardShortcut) {
        strokes.add(((KeyboardShortcut)shortcut).getFirstKeyStroke());
      }
    }
    return strokes.toArray(new KeyStroke[strokes.size()]);
  }

  public void projectClosed() {
    myToolWindowManager.unregisterToolWindow(ToolWindowId.COMMANDER);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        setupImpl();
      }
    });
  }

  public void setupImpl() {
    mySelectionListener = new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateToolWindowTitle();
      }
    };
    myListDataListener = new ListDataListener() {
      public void intervalAdded(final ListDataEvent e) {
        updateToolWindowTitle();
      }

      public void intervalRemoved(final ListDataEvent e) {
        updateToolWindowTitle();
      }

      public void contentsChanged(final ListDataEvent e) {
        updateToolWindowTitle();
      }
    };
    myFocusWatcher = new FocusWatcher();

    myLeftPanel = createPanel();
    myRightPanel = createPanel();

    mySplitter = new Splitter();
    mySplitter.setFirstComponent(myLeftPanel);
    mySplitter.setSecondComponent(myRightPanel);

    add(mySplitter, BorderLayout.CENTER);

    final AutoScrollToSourceHandler handler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return myAutoScrollMode;
      }

      protected void setAutoScrollMode(boolean state) {
        myAutoScrollMode = state;
      }
    };
    handler.install(myLeftPanel.myList);
    handler.install(myRightPanel.myList);

    final boolean shouldAddToolbar = !ApplicationManager.getApplication().isUnitTestMode();
    if (shouldAddToolbar) {
      final DefaultActionGroup toolbarActions = createToolbarActions();
      toolbarActions.add(handler.createToggleAction());
      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.COMMANDER_TOOLBAR, toolbarActions, true);
      add(toolbar.getComponent(), BorderLayout.NORTH);
    }

    processConfigurationElement();
    myElement = null;

    myFocusWatcher.install(this);

    setupToolWindow();
  }

  private DefaultActionGroup createToolbarActions() {
    final ActionManager actionManager = ActionManager.getInstance();
    final DefaultActionGroup group = new DefaultActionGroup();

    final AnAction backAction = new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        myHistory.back();
      }

      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(myHistory.canGoBack());
      }
    };
    backAction.copyFrom(actionManager.getAction(IdeActions.ACTION_GOTO_BACK));
    group.add(backAction);

    final AnAction forwardAction = new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        myHistory.forward();
      }

      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(myHistory.canGoForward());
      }
    };
    forwardAction.copyFrom(actionManager.getAction(IdeActions.ACTION_GOTO_FORWARD));
    group.add(forwardAction);

    group.add(actionManager.getAction(IdeActions.ACTION_COMMANDER_SWAP_PANELS));
    group.add(actionManager.getAction(IdeActions.ACTION_COMMANDER_SYNC_VIEWS));

    return group;
  }

  protected void setupToolWindow() {
    final ToolWindow toolWindow = myToolWindowManager.registerToolWindow(ToolWindowId.COMMANDER, this, ToolWindowAnchor.RIGHT);
    toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowCommander.png"));
    SelectInManager.getInstance(myProject).addTarget(new CommanderSelectInTarget(myProject));
  }


  private CommanderPanel createPanel() {
    final CommanderPanel panel = new CommanderPanel(myProject, this);
    final ProjectAbstractTreeStructureBase treeStructure = createProjectTreeStructure();
    panel.setBuilder(new ProjectListBuilder(myProject, panel, treeStructure, AlphaComparator.INSTANCE, true));
    panel.setProjectTreeStructure(treeStructure);

    final FocusAdapter focusListener = new FocusAdapter() {
      public void focusGained(final FocusEvent e) {
        updateToolWindowTitle(panel);
      }
    };
    final JList list = panel.getList();
    list.addFocusListener(focusListener);
    list.getSelectionModel().addListSelectionListener(mySelectionListener);
    list.getModel().addListDataListener(myListDataListener);

    myDisposables.add(new Disposable() {
      public void dispose() {
        list.removeFocusListener(focusListener);
        list.getSelectionModel().removeListSelectionListener(mySelectionListener);
        list.getModel().removeListDataListener(myListDataListener);
      }
    });
    return panel;
  }

  protected AbstractProjectTreeStructure createProjectTreeStructure() {
    return new AbstractProjectTreeStructure(myProject) {
      public boolean isShowMembers() {
        return true;
      }

      public boolean isHideEmptyMiddlePackages() {
        return false;
      }

      public boolean isFlattenPackages() {
        return false;
      }

      public boolean isAbbreviatePackageNames() {
        return false;
      }

      public boolean isShowLibraryContents() {
        return false;
      }

      public boolean isShowModules() {
        return false;
      }
    };
  }

  /**
   * invoked in AWT thread
   */
  private void updateToolWindowTitle() {
    final CommanderPanel panel = getActivePanel();
    updateToolWindowTitle(panel);
  }

  protected void updateToolWindowTitle(final CommanderPanel activePanel) {
    final ToolWindow toolWindow = myToolWindowManager.getToolWindow(ToolWindowId.COMMANDER);
    if (toolWindow != null) {
      final PsiElement element = activePanel.getSelectedElement();
      toolWindow.setTitle(getTitle(element));
    }
  }

  public boolean isLeftPanelActive() {
    return isPanelActive(myLeftPanel);
  }

  boolean isPanelActive(final CommanderPanel panel) {
    return panel.getList() == myFocusWatcher.getFocusedComponent();
  }

  public void selectElementInLeftPanel(final Object element, VirtualFile virtualFile) {
    myLeftPanel.getBuilder().selectElement(element, virtualFile);
    if (!isPanelActive(myLeftPanel)) {
      switchActivePanel();
    }
  }

  public void selectElementInRightPanel(final Object element, VirtualFile virtualFile) {
    myRightPanel.getBuilder().selectElement(element, virtualFile);
    if (!isPanelActive(myRightPanel)) {
      switchActivePanel();
    }
  }

  public void switchActivePanel() {
    final CommanderPanel activePanel = getActivePanel();
    final CommanderPanel inactivePanel = getInactivePanel();
    inactivePanel.setActive(true);
    activePanel.setActive(false);
    IdeFocusTraversalPolicy.getPreferredFocusedComponent(inactivePanel).requestFocus();
  }

  public void enterElementInActivePanel(final PsiElement element) {
    final CommanderPanel activePanel;
    if (isLeftPanelActive()) {
      activePanel = myLeftPanel;
    } else {
      activePanel = myRightPanel;
    }
    activePanel.getBuilder().enterElement(element, PsiUtil.getVirtualFile(element));
  }

  public void swapPanels() {
    mySplitter.swapComponents();

    final CommanderPanel tmpPanel = myLeftPanel;
    myLeftPanel = myRightPanel;
    myRightPanel = tmpPanel;
  }

  public void syncViews() {
    final CommanderPanel activePanel;
    final CommanderPanel passivePanel;
    if (isLeftPanelActive()) {
      activePanel = myLeftPanel;
      passivePanel = myRightPanel;
    } else {
      activePanel = myRightPanel;
      passivePanel = myLeftPanel;
    }
    ProjectViewNode element = (ProjectViewNode)activePanel.getBuilder().getParentNode();
    passivePanel.getBuilder().enterElement(element);
  }

  public CommanderPanel getActivePanel() {
    return isLeftPanelActive() ? myLeftPanel : myRightPanel;
  }

  public CommanderPanel getInactivePanel() {
    return !isLeftPanelActive() ? myLeftPanel : myRightPanel;
  }

  public Object getData(final String dataId) {
    if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return HelpID.COMMANDER;
    } else if (DataConstantsEx.PROJECT.equals(dataId)) {
      return myProject;
    } else if (DataConstantsEx.TARGET_PSI_ELEMENT.equals(dataId)) {
      final AbstractTreeNode parentElement = getInactivePanel().getBuilder().getParentNode();
      if (parentElement == null) return null;
      final Object element = parentElement.getValue();
      return (element instanceof PsiElement) && ((PsiElement)element).isValid()? element : null;
    } else if (DataConstantsEx.SECONDARY_PSI_ELEMENT.equals(dataId)) {
      final PsiElement selectedElement = getInactivePanel().getSelectedElement();
      return selectedElement != null && selectedElement.isValid() ? selectedElement : null;
    } else {
      return getActivePanel().getDataImpl(dataId);
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    if (myLeftPanel == null || myRightPanel == null) {
      return;
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    Element e = new Element(ELEMENT_LEFTPANEL);
    element.addContent(e);
    writePanel(myLeftPanel, e);
    e = new Element(ELEMENT_RIGHTPANEL);
    element.addContent(e);
    writePanel(myRightPanel, e);
    e = new Element(ELEMENT_SPLITTER);
    element.addContent(e);
    e.setAttribute(ATTRIBUTE_PROPORTION, Float.toString(mySplitter.getProportion()));
    if (!MOVE_FOCUS) {
      e = new Element(ELEMENT_OPTION);
      element.addContent(e);
      //noinspection HardCodedStringLiteral
      e.setAttribute(ATTRIBUTE_MOVE_FOCUS, "false");
    }
  }

  private static void writePanel(final CommanderPanel panel, final Element element) {
    /*TODO[anton,vova]: it's a patch!!!*/
    final AbstractListBuilder builder = panel.getBuilder();
    if (builder == null) return;

    final AbstractTreeNode parentNode = builder.getParentNode();
    final Object parentElement = parentNode != null? parentNode.getValue() : null;
    if (parentElement instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory) parentElement;
      element.setAttribute(ATTRIBUTE_URL, directory.getVirtualFile().getUrl());
    }
    else if (parentElement instanceof PsiClass) {
      for (PsiElement e = (PsiElement) parentElement; e != null; e = e.getParent()) {
        if (e instanceof PsiClass) {
          final String qualifiedName = ((PsiClass) e).getQualifiedName();
          if (qualifiedName != null) {
            element.setAttribute(ATTRIBUTE_CLASS, qualifiedName);
            break;
          }
        }
      }
    }
  }

  public void readExternal(final Element element) throws InvalidDataException {
    myElement = element;
  }

  private PsiElement readParentElement(final Element element) {
    if (element.getAttributeValue(ATTRIBUTE_URL) != null) {
      final String url = element.getAttributeValue(ATTRIBUTE_URL);
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      return file != null ? PsiManager.getInstance(myProject).findDirectory(file) : null;
    } else if (element.getAttributeValue(ATTRIBUTE_CLASS) != null) {
      final String className = element.getAttributeValue(ATTRIBUTE_CLASS);
      return className != null ? PsiManager.getInstance(myProject).findClass(className) : null;
    }
    return null;
  }

  public void disposeComponent() {
    for (Disposable disposable : myDisposables) {
      disposable.dispose();
    }
    myDisposables.clear();
    if (myLeftPanel == null) {
      // not opened project (default?)
      return;
    }
    myLeftPanel.dispose();
    myRightPanel.dispose();
    myHistory.clearHistory();
    myProject = null;
  }

  public String getComponentName() {
    return "Commander";
  }

  public CommanderPanel getRightPanel() {
    return myRightPanel;
  }

  public CommanderPanel getLeftPanel() {
    return myLeftPanel;
  }

  public static String getTitle(final PsiElement element) {
    String title = null;
    if (element == null || !element.isValid()) {
      title = null;
    }
    else if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory) element;
      title = UsageViewUtil.getPackageName(directory, true);
    }
    else if (element instanceof PsiFile) {
      final PsiFile file = (PsiFile) element;
      title = file.getVirtualFile().getPresentableUrl();
    }
    else if (element instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass) element;
      title = psiClass.getQualifiedName();
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) element;
      final PsiClass aClass = method.getContainingClass();
      title = aClass != null? aClass.getQualifiedName() : method.toString();
    }
    else if (element instanceof PsiField) {
      final PsiField field = (PsiField) element;
      final PsiClass aClass = field.getContainingClass();
      title = aClass != null? aClass.getQualifiedName() : field.toString();
    }
    else {
      final PsiFile file = element.getContainingFile();
      title = file != null? file.getVirtualFile().getPresentableUrl() : element.toString();
    }
    return title;
  }
}

