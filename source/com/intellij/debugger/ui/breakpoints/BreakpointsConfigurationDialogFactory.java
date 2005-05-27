package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.ui.breakpoints.actions.*;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * created Jun 18, 2001
 * @author Jeka
 */
public class BreakpointsConfigurationDialogFactory {
  private static final String BREAKPOINT_PANEL = "breakpoint_panel";
  private static final String LINE_BREAKPOINTS_NAME = "Line Breakpoints";
  private static final String EXCEPTION_BREAKPOINTS_NAME = "Exception Breakpoints";
  private static final String FIELD_WATCHPOINTS_NAME = "Field Watchpoints";
  private static final String METHOD_BREAKPOINTS_NAME = "Method Breakpoints";

  private Project myProject;
  private BreakpointPanel myLineBreakpointsPanel;
  private BreakpointPanel myExceptionBreakpointsPanel;
  private BreakpointPanel myFieldBreakpointsPanel;
  private BreakpointPanel myMethodBreakpointsPanel;
  private int myLastSelectedTabIndex = 0;

  public BreakpointsConfigurationDialogFactory(Project project) {
    myProject = project;
  }

  private BreakpointManager getBreakpointManager() {
    return DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
  }

  public DialogWrapper createDialog(Breakpoint initialBreakpoint, String selectComponent) {
    BreakpointsConfigurationDialog dialog = new BreakpointsConfigurationDialog();
    dialog.selectBreakpoint(initialBreakpoint);
    dialog.setPreferredFocusedComponent(selectComponent);
    return dialog;
  }

  private class BreakpointsConfigurationDialog extends DialogWrapper {
    private JPanel myPanel;
    private TabbedPaneWrapper myTabbedPane;
    private JComponent myPreferredComponent;

    public BreakpointsConfigurationDialog() {
      super(myProject, true);
      setTitle("Breakpoints");
      setOKButtonText("&Close");
      init();
      reset();
    }

    protected Action[] createActions(){
      return new Action[]{getOKAction(), getHelpAction()};
    }

    protected void doHelpAction() {
      if (myLineBreakpointsPanel.getPanel().isShowing()) {
        HelpManager.getInstance().invokeHelp(HelpID.LINE_BREAKPOINTS);
      }
      else if (myMethodBreakpointsPanel.getPanel().isShowing()) {
        HelpManager.getInstance().invokeHelp(HelpID.METHOD_BREAKPOINTS);
      }
      else if (myExceptionBreakpointsPanel.getPanel().isShowing()) {
        HelpManager.getInstance().invokeHelp(HelpID.EXCEPTION_BREAKPOINTS);
      }
      else if (myFieldBreakpointsPanel.getPanel().isShowing()) {
        HelpManager.getInstance().invokeHelp(HelpID.FIELD_WATCHPOINTS);
      }
      else {
        super.doHelpAction();
      }
    }

    protected JComponent createCenterPanel() {
      myTabbedPane = new TabbedPaneWrapper();
      myPanel = new JPanel(new BorderLayout());
      myLineBreakpointsPanel = new BreakpointPanel(new LineBreakpointPropertiesPanel(myProject), new BreakpointPanelAction[] {
        new SwitchViewAction(),
        new GotoSourceAction(myProject) {
          public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);
            close(OK_EXIT_CODE);
          }
        },
        new ViewSourceAction(myProject),
        new RemoveAction(myProject),
        new ToggleGroupByMethodsAction(),
        new ToggleGroupByClassesAction(),
        new ToggleFlattenPackagesAction(),
      });
      setupPanelUI(myLineBreakpointsPanel, LineBreakpoint.CATEGORY);
      
      myExceptionBreakpointsPanel = new BreakpointPanel(new ExceptionBreakpointPropertiesPanel(myProject), new BreakpointPanelAction[] {
        new SwitchViewAction(),
        new AddExceptionBreakpointAction(),
        new RemoveAction(myProject) {
          public void update() {
            super.update();
            if(getButton().isEnabled()) {
              Breakpoint[] selectedBreakpoints = getPanel().getSelectedBreakpoints();
              for (int i = 0; i < selectedBreakpoints.length; i++) {
                Breakpoint bp = selectedBreakpoints[i];
                if (bp instanceof AnyExceptionBreakpoint) {
                  getButton().setEnabled(false);
                }
              }
            }
          }
        },
        new ToggleGroupByClassesAction(),
        new ToggleFlattenPackagesAction(),
      });
      setupPanelUI(myExceptionBreakpointsPanel, ExceptionBreakpoint.CATEGORY);
      myExceptionBreakpointsPanel.getTree().setGroupByMethods(false);

      myFieldBreakpointsPanel = new BreakpointPanel(new FieldBreakpointPropertiesPanel(myProject), new BreakpointPanelAction[] {
        new SwitchViewAction(),
        new AddFieldBreakpointAction(),
        new GotoSourceAction(myProject) {
          public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);
            close(OK_EXIT_CODE);
          }
        },
        new ViewSourceAction(myProject),
        new RemoveAction(myProject),
        new ToggleGroupByClassesAction(),
        new ToggleFlattenPackagesAction(),
      });
      setupPanelUI(myFieldBreakpointsPanel, FieldBreakpoint.CATEGORY);
      myFieldBreakpointsPanel.getTree().setGroupByMethods(false);

      myMethodBreakpointsPanel = new BreakpointPanel(new MethodBreakpointPropertiesPanel(myProject), new BreakpointPanelAction[] {
        new SwitchViewAction(),
        new GotoSourceAction(myProject) {
          public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);
            close(OK_EXIT_CODE);
          }
        },
        new ViewSourceAction(myProject),
        new RemoveAction(myProject),
        new ToggleGroupByClassesAction(),
        new ToggleFlattenPackagesAction(),
      });
      setupPanelUI(myMethodBreakpointsPanel, MethodBreakpoint.CATEGORY);
      myMethodBreakpointsPanel.getTree().setGroupByMethods(false);

      addPanel(myLineBreakpointsPanel, LINE_BREAKPOINTS_NAME);
      addPanel(myExceptionBreakpointsPanel, EXCEPTION_BREAKPOINTS_NAME);
      addPanel(myFieldBreakpointsPanel, FIELD_WATCHPOINTS_NAME);
      addPanel(myMethodBreakpointsPanel, METHOD_BREAKPOINTS_NAME);

      myTabbedPane.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          BreakpointPanel panel = getSelectedPanel();
          panel.ensureSelectionExists();
        }
      });
      myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

      myTabbedPane.installKeyboardNavigation();

      // "Enter" and "Esc" keys work like "Close" button.
      ActionListener closeAction = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          close(CANCEL_EXIT_CODE);
        }
      };
      myPanel.registerKeyboardAction(
        closeAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
      myPanel.setPreferredSize(new Dimension(600, 500));
      return myPanel;
    }

    private void setupPanelUI(BreakpointPanel panel, String category) {
      final BreakpointManager breakpointManager = getBreakpointManager();

      final BreakpointTree tree = panel.getTree();
      final String flattenPackages = breakpointManager.getProperty(category + "_flattenPackages");
      if (flattenPackages != null) {
        tree.setFlattenPackages("true".equalsIgnoreCase(flattenPackages));
      }
      final String groupByClasses = breakpointManager.getProperty(category + "_groupByClasses");
      if (groupByClasses != null) {
        tree.setGroupByClasses("true".equalsIgnoreCase(groupByClasses));
      }
      final String groupByMethods = breakpointManager.getProperty(category + "_groupByMethods");
      if (groupByMethods != null) {
        tree.setGroupByMethods("true".equalsIgnoreCase(groupByMethods));
      }

      final String viewId = breakpointManager.getProperty(category + "_viewId");
      if (viewId != null) {
        panel.showView(viewId);
      }
    }

    private void savePanelSettings(BreakpointPanel panel, String category) {
      final BreakpointManager breakpointManager = getBreakpointManager();
      
      final BreakpointTree tree = panel.getTree();
      breakpointManager.setProperty(category + "_flattenPackages", tree.isFlattenPackages()? "true" : "false");
      breakpointManager.setProperty(category + "_groupByClasses", tree.isGroupByClasses()? "true" : "false");
      breakpointManager.setProperty(category + "_groupByMethods", tree.isGroupByMethods()? "true" : "false");
      breakpointManager.setProperty(category + "_viewId", panel.getCurrentViewId());
    }

    private void addPanel(BreakpointPanel panel, final String title) {
      JPanel jpanel = panel.getPanel();
      jpanel.putClientProperty(BREAKPOINT_PANEL, panel);
      myTabbedPane.addTab(title, jpanel);
      final int tabIndex = myTabbedPane.getTabCount() - 1;
      panel.addChangesListener(new BreakpointPanel.ChangesListener() {
        public void breakpointsChanged() {
          updateTabTitle(tabIndex);
        }
      });
    }

    private BreakpointPanel getSelectedPanel() {
      JComponent selectedComponent = myTabbedPane.getSelectedComponent();
      return selectedComponent != null ? (BreakpointPanel)selectedComponent.getClientProperty(BREAKPOINT_PANEL) : null;
    }

    public JComponent getPreferredFocusedComponent() {
      return myPreferredComponent != null ? myPreferredComponent : IdeFocusTraversalPolicy.getPreferredFocusedComponent(myTabbedPane.getComponent());
    }

    public void setPreferredFocusedComponent(String control) {
      BreakpointPanel selectedPanel = getSelectedPanel();
      myPreferredComponent = selectedPanel.getControl(control);
    }

    protected void dispose() {
      apply();
      if (myPanel != null) {
        if (myLineBreakpointsPanel != null) {
          myLineBreakpointsPanel.dispose();
          savePanelSettings(myLineBreakpointsPanel, LineBreakpoint.CATEGORY);
        }
        if (myExceptionBreakpointsPanel != null) {
          myExceptionBreakpointsPanel.dispose();
          savePanelSettings(myExceptionBreakpointsPanel, ExceptionBreakpoint.CATEGORY);
        }
        if (myFieldBreakpointsPanel != null) {
          myFieldBreakpointsPanel.dispose();
          savePanelSettings(myFieldBreakpointsPanel, FieldBreakpoint.CATEGORY);
        }
        if (myMethodBreakpointsPanel != null) {
          myMethodBreakpointsPanel.dispose();
          savePanelSettings(myMethodBreakpointsPanel, MethodBreakpoint.CATEGORY);
        }
        myTabbedPane.uninstallKeyboardNavigation();
        myLastSelectedTabIndex = myTabbedPane.getSelectedIndex();
        myPanel.removeAll();
        myPanel = null;
        myTabbedPane = null;
      }
      super.dispose();
    }

    private void apply() {
      if (myLineBreakpointsPanel != null) {
        myLineBreakpointsPanel.saveChanges();
      }
      if (myExceptionBreakpointsPanel != null) {
        myExceptionBreakpointsPanel.saveChanges();
      }
      if (myFieldBreakpointsPanel != null) {
        myFieldBreakpointsPanel.saveChanges();
      }
      if (myMethodBreakpointsPanel != null) {
        myMethodBreakpointsPanel.saveChanges();
      }
      BreakpointManager breakpointManager = getBreakpointManager();
      breakpointManager.updateAllRequests();
    }

    private void reset() {
      BreakpointManager breakpointManager = getBreakpointManager();
      myLineBreakpointsPanel.setBreakpoints(breakpointManager.getBreakpoints(LineBreakpoint.CATEGORY));
      myExceptionBreakpointsPanel.setBreakpoints(breakpointManager.getBreakpoints(ExceptionBreakpoint.CATEGORY));
      myExceptionBreakpointsPanel.insertBreakpointAt(breakpointManager.getAnyExceptionBreakpoint(), 0);
      myFieldBreakpointsPanel.setBreakpoints(breakpointManager.getBreakpoints(FieldBreakpoint.CATEGORY));
      myMethodBreakpointsPanel.setBreakpoints(breakpointManager.getBreakpoints(MethodBreakpoint.CATEGORY));
      updateAllTabTitles();
      if (myLastSelectedTabIndex >= myTabbedPane.getTabCount() && myLastSelectedTabIndex < 0) {
        myLastSelectedTabIndex = 0;
      }
      myTabbedPane.setSelectedIndex(myLastSelectedTabIndex);
    }

    private void selectBreakpoint(Breakpoint breakpoint) {
      if (breakpoint == null) return;
      if (breakpoint instanceof LineBreakpoint) {
        myTabbedPane.setSelectedComponent(myLineBreakpointsPanel.getPanel());
        myLineBreakpointsPanel.selectBreakpoint(breakpoint);
      }
      else if (breakpoint instanceof ExceptionBreakpoint) {
        myTabbedPane.setSelectedComponent(myExceptionBreakpointsPanel.getPanel());
        myExceptionBreakpointsPanel.selectBreakpoint(breakpoint);
      }
      else if (breakpoint instanceof FieldBreakpoint) {
        myTabbedPane.setSelectedComponent(myFieldBreakpointsPanel.getPanel());
        myFieldBreakpointsPanel.selectBreakpoint(breakpoint);
      }
      else if (breakpoint instanceof MethodBreakpoint) {
        myTabbedPane.setSelectedComponent(myMethodBreakpointsPanel.getPanel());
        myMethodBreakpointsPanel.selectBreakpoint(breakpoint);
      }
    }

    private void updateAllTabTitles() {
      for (int idx = 0; idx < myTabbedPane.getTabCount(); idx++) {
        updateTabTitle(idx);
      }
    }

    private void updateTabTitle(final int idx) {
      JComponent component = myTabbedPane.getComponentAt(idx);
      if (component == myLineBreakpointsPanel.getPanel()) {
        myTabbedPane.setIconAt(idx, hasEnabledBreakpoints(myLineBreakpointsPanel)? LineBreakpoint.ICON : LineBreakpoint.DISABLED_ICON);
      }
      else if (component == myExceptionBreakpointsPanel.getPanel()) {
        myTabbedPane.setIconAt(idx, hasEnabledBreakpoints(myExceptionBreakpointsPanel)? ExceptionBreakpoint.ICON : ExceptionBreakpoint.DISABLED_ICON);
      }
      else if (component == myFieldBreakpointsPanel.getPanel()) {
        myTabbedPane.setIconAt(idx, hasEnabledBreakpoints(myFieldBreakpointsPanel)? FieldBreakpoint.ICON : FieldBreakpoint.DISABLED_ICON);
      }
      else if (component == myMethodBreakpointsPanel.getPanel()) {
        myTabbedPane.setIconAt(idx, hasEnabledBreakpoints(myMethodBreakpointsPanel)? MethodBreakpoint.ICON : MethodBreakpoint.DISABLED_ICON);
      }
    }

    private boolean hasEnabledBreakpoints(BreakpointPanel panel) {
      final java.util.List<Breakpoint> breakpoints = panel.getBreakpoints();
      for (Breakpoint breakpoint : breakpoints) {
        if (breakpoint.ENABLED) {
          return true;
        }
      }
      return false;
    }

    private class AddExceptionBreakpointAction extends AddAction {
      public void actionPerformed(ActionEvent e) {
        final PsiClass throwableClass = PsiManager.getInstance(myProject).findClass("java.lang.Throwable", GlobalSearchScope.allScope(myProject));
        TreeClassChooser chooser =
          TreeClassChooserFactory.getInstance(myProject).createInheritanceClassChooser("Enter Exception Class", GlobalSearchScope.allScope(myProject),
                                                                                       throwableClass, true, true, null);
        chooser.showDialog();
        PsiClass selectedClass = chooser.getSelectedClass();
        String qName = (selectedClass != null)? selectedClass.getQualifiedName() : null;

        if (qName != null && qName.length() > 0) {
          ExceptionBreakpoint breakpoint = getBreakpointManager().addExceptionBreakpoint(qName);
          myExceptionBreakpointsPanel.addBreakpoint(breakpoint);
        }
        myExceptionBreakpointsPanel.ensureSelectionExists();
        updateAllTabTitles();
      }
    }

    private class AddFieldBreakpointAction extends AddAction {
      public void actionPerformed(ActionEvent e) {
        AddFieldBreakpointDialog dialog = new AddFieldBreakpointDialog(myProject) {
          protected boolean validateData() {
            String className = getClassName();
            if (className.length() == 0) {
              Messages.showMessageDialog(myProject, "Cannot add watchpoint: a class name is not specified", "Add Field Watchpoint", Messages.getErrorIcon());
              return false;
            }
            String fieldName = getFieldName();
            if (fieldName.length() == 0) {
              Messages.showMessageDialog(myProject, "Cannot add watchpoint: a field name is not specified", "Add Field Watchpoint", Messages.getErrorIcon());
              return false;
            }
            PsiClass psiClass = PsiManager.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
            if (psiClass != null) {
              PsiFile  psiFile  = psiClass.getContainingFile();
              Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
              if(document != null) {
                PsiField field = psiClass.findFieldByName(fieldName, true);
                if(field != null) {
                  int line = document.getLineNumber(field.getTextOffset());
                  FieldBreakpoint fieldBreakpoint = getBreakpointManager().addFieldBreakpoint(document, line, fieldName);
                  if (fieldBreakpoint != null) {
                    myFieldBreakpointsPanel.addBreakpoint(fieldBreakpoint);
                    myFieldBreakpointsPanel.ensureSelectionExists();
                    updateAllTabTitles();
                    return true;
                  }
                } else {
                  Messages.showMessageDialog(
                    myProject,
                    "Cannot create a field watchpoint for \"" + className + "." + fieldName + "\".\nField \"" + fieldName + "\" not found",
                    "Error",
                    Messages.getErrorIcon()
                  );

                }
              }
            } else {
              Messages.showMessageDialog(
                myProject,
                "Cannot create a field watchpoint for \"" + className + "." + fieldName + "\".\nNo sources for class \"" + className + "\"",
                "Error",
                Messages.getErrorIcon()
              );
            }
            return false;
          }
        };
        dialog.show();
      }
    }

  }

}
