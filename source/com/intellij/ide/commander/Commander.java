package com.intellij.ide.commander;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
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
import com.intellij.psi.*;
import com.intellij.usageView.UsageViewUtil;
import org.jdom.Element;

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

  public Commander(final Project project, KeymapManager keymapManager) {
    super(new BorderLayout());
    myProject = project;

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
    actionMap.put("backCommand", backAction);
    actionMap.put("forwardCommand", fwdAction);
    final KeyStroke[] backStrokes = getKeyStrokes(IdeActions.ACTION_GOTO_BACK, keymapManager);
    for (int idx = 0; idx < backStrokes.length; idx++) {
      KeyStroke stroke = backStrokes[idx];
      //getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "backCommand");
      //getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, "backCommand");
      registerKeyboardAction(backAction, "backCommand", stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
      registerKeyboardAction(backAction, "backCommand", stroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    final KeyStroke[] fwdStrokes = getKeyStrokes(IdeActions.ACTION_GOTO_FORWARD, keymapManager);
    for (int idx = 0; idx < fwdStrokes.length; idx++) {
      KeyStroke stroke = fwdStrokes[idx];
      //getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "forwardCommand");
      //getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, "forwardCommand");
      registerKeyboardAction(fwdAction, "forwardCommand", stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
      registerKeyboardAction(fwdAction, "forwardCommand", stroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
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

    element = myElement.getChild("leftPanel");
    if (element != null) {
      final PsiElement parentElement = readParentElement(element);
      if (parentElement != null) {
        myLeftPanel.getBuilder().enterElement(parentElement, BasePsiNode.getVirtualFile(parentElement));
      }
    }

    element = myElement.getChild("rightPanel");
    if (element != null) {
      final PsiElement parentElement = readParentElement(element);
      if (parentElement != null) {
        myRightPanel.getBuilder().enterElement(parentElement, BasePsiNode.getVirtualFile(parentElement));
      }
    }

    element = myElement.getChild("splitter");
    if (element != null) {
      final String attribute = element.getAttributeValue("proportion");
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

    element = myElement.getChild("OPTION");
    if (element != null) {
      MOVE_FOCUS = !"false".equals(element.getAttributeValue("MOVE_FOCUS"));
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
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.COMMANDER);
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

    processConfigurationElement();
    myElement = null;

    myFocusWatcher.install(this);

    setupToolWindow();
  }

  protected void setupToolWindow() {
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(ToolWindowId.COMMANDER, this, ToolWindowAnchor.RIGHT);
    toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowCommander.png"));
    SelectInManager.getInstance(myProject).addTarget(new CommanderSelectInTarget(myProject));
  }


  private CommanderPanel createPanel() {
    final CommanderPanel panel = new CommanderPanel(myProject, this);
    final ProjectAbstractTreeStructureBase treeStructure = createProjectTreeStructure();
    final ProjectListBuilder builder = new ProjectListBuilder(myProject, panel, treeStructure, AlphaComparator.INSTANCE, true);
    panel.setBuilder(builder);

    panel.getList().addFocusListener(new FocusAdapter() {
      public void focusGained(final FocusEvent e) {
        updateToolWindowTitle(panel);
      }
    });
    panel.getList().getSelectionModel().addListSelectionListener(mySelectionListener);
    panel.getList().getModel().addListDataListener(myListDataListener);

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
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.COMMANDER);
    if (toolWindow == null) return;

    String title = null;
    final PsiElement element = activePanel.getSelectedElement();
    if (element != null) {
      // todo!!!
      if (!element.isValid()) return;
      if (element instanceof PsiDirectory) {
        final PsiDirectory directory = (PsiDirectory) element;
        title = UsageViewUtil.getPackageName(directory, true);
      } else if (element instanceof PsiFile) {
        final PsiFile file = (PsiFile) element;
        title = file.getVirtualFile().getPresentableUrl();
      } else if (element instanceof PsiClass) {
        final PsiClass psiClass = (PsiClass) element;
        title = psiClass.getQualifiedName();
      } else if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod) element;
        final PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          title = aClass.getQualifiedName();
        } else {
          title = method.toString();
        }
      } else if (element instanceof PsiField) {
        final PsiField field = (PsiField) element;
        final PsiClass aClass = field.getContainingClass();
        if (aClass != null) {
          title = aClass.getQualifiedName();
        } else {
          title = field.toString();
        }
      } else {
        final PsiFile file = element.getContainingFile();
        if (file != null) {
          title = file.getVirtualFile().getPresentableUrl();
        } else {
          title = element.toString();
        }
      }
    }

    toolWindow.setTitle(title);
  }

  public boolean isLeftPanelActive() {
    return isPanelActive(myLeftPanel);
  }

  boolean isPanelActive(final CommanderPanel panel) {
    return panel.getList() == myFocusWatcher.getFocusedComponent();
  }

  public void selectElementInLeftPanel(final PsiElement element) {
    myLeftPanel.getBuilder().selectElement(element);
    if (!isPanelActive(myLeftPanel)) {
      switchActivePanel();
    }
  }

  public void selectElementInRightPanel(final PsiElement element) {
    myRightPanel.getBuilder().selectElement(element);
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
    activePanel.getBuilder().enterElement(element, BasePsiNode.getVirtualFile(element));
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
    ProjectViewNode element = (ProjectViewNode)activePanel.getBuilder().getParentElement();
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
      final Object element = getInactivePanel().getBuilder().getParentElement();
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
    Element e = new Element("leftPanel");
    element.addContent(e);
    writePanel(myLeftPanel, e);
    e = new Element("rightPanel");
    element.addContent(e);
    writePanel(myRightPanel, e);
    e = new Element("splitter");
    element.addContent(e);
    e.setAttribute("proportion", Float.toString(mySplitter.getProportion()));
    if (!MOVE_FOCUS) {
      e = new Element("OPTION");
      element.addContent(e);
      e.setAttribute("MOVE_FOCUS", "false");
    }
  }

  private static void writePanel(final CommanderPanel panel, final Element element) {
    /*TODO[anton,vova]: it's a patch!!!*/
    final AbstractListBuilder builder = panel.getBuilder();
    if (builder == null) return;

    final Object parentElement = builder.getParentElement();
    if (parentElement instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory) parentElement;
      element.setAttribute("url", directory.getVirtualFile().getUrl());
    }
    else if (parentElement instanceof PsiClass) {
      for (PsiElement e = (PsiElement) parentElement; e != null; e = e.getParent()) {
        if (e instanceof PsiClass) {
          final String qualifiedName = ((PsiClass) e).getQualifiedName();
          if (qualifiedName != null) {
            element.setAttribute("class", qualifiedName);
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
    if (element.getAttributeValue("url") != null) {
      final String url = element.getAttributeValue("url");
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      return file != null ? PsiManager.getInstance(myProject).findDirectory(file) : null;
    } else if (element.getAttributeValue("class") != null) {
      final String className = element.getAttributeValue("class");
      return className != null ? PsiManager.getInstance(myProject).findClass(className) : null;
    }
    return null;
  }

  public void disposeComponent() {
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
}

