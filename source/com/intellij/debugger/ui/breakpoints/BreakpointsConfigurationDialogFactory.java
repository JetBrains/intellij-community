package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.HelpID;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import javax.swing.event.*;
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
      myLineBreakpointsPanel = new BreakpointPanel(new BreakpointTableModel(), new LineBreakpointPropertiesPanel(myProject));
      myLineBreakpointsPanel.getAddBreakpointButton().setVisible(false);
      initPanel(myLineBreakpointsPanel);

      myExceptionBreakpointsPanel = new BreakpointPanel(new BreakpointTableModel(), new ExceptionBreakpointPropertiesPanel(myProject)) {
        public void updateButtons() {
          super.updateButtons();
          if(getRemoveBreakpointButton().isEnabled()) {
            Breakpoint[] selectedBreakpoints = getSelectedBreakpoints();
            for (int i = 0; i < selectedBreakpoints.length; i++) {
              Breakpoint bp = selectedBreakpoints[i];
              if (bp instanceof AnyExceptionBreakpoint) {
                getRemoveBreakpointButton().setEnabled(false);
              }
            }
          }
        }
      };
      AddExceptionBreakpointAction addExceptionBreakpointAction = new AddExceptionBreakpointAction();
      myExceptionBreakpointsPanel.getAddBreakpointButton().addActionListener(addExceptionBreakpointAction);
      myExceptionBreakpointsPanel.getTable().registerKeyboardAction(
        addExceptionBreakpointAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
      myExceptionBreakpointsPanel.getViewSourceButton().setVisible(false);
      myExceptionBreakpointsPanel.getGotoSourceButton().setVisible(false);
      RemoveBreakpointAction removeExceptionBreakpointAction = new RemoveBreakpointAction(myExceptionBreakpointsPanel);
      final JButton removeExceptionBreakpointButton = myExceptionBreakpointsPanel.getRemoveBreakpointButton();
      removeExceptionBreakpointButton.addActionListener(removeExceptionBreakpointAction);
      myExceptionBreakpointsPanel.getTable().registerKeyboardAction(
        removeExceptionBreakpointAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
      myExceptionBreakpointsPanel.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          JTable table = myExceptionBreakpointsPanel.getTable();
          boolean shouldEnable = (table.getModel().getRowCount() > 0) && (table.getSelectedRow() >= 0);
          if (shouldEnable) {
            BreakpointTableModel model = (BreakpointTableModel)table.getModel();
            int[] rows = table.getSelectedRows();
            for (int idx = 0; idx < rows.length; idx++) {
              Breakpoint breakpoint = model.getBreakpoint(rows[idx]);
              if (breakpoint instanceof AnyExceptionBreakpoint) {
                shouldEnable = false;
                break;
              }
            }
          }
          removeExceptionBreakpointButton.setEnabled(shouldEnable);
        }
      });
      removeExceptionBreakpointButton.setEnabled(false);

      myFieldBreakpointsPanel = new BreakpointPanel(new BreakpointTableModel(), new FieldBreakpointPropertiesPanel(myProject));
      AddFieldBreakpointAction addFieldBreakpointAction = new AddFieldBreakpointAction();
      myFieldBreakpointsPanel.getAddBreakpointButton().addActionListener(addFieldBreakpointAction);
      myFieldBreakpointsPanel.getTable().registerKeyboardAction(
        addFieldBreakpointAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
      initPanel(myFieldBreakpointsPanel);

      myMethodBreakpointsPanel = new BreakpointPanel(new BreakpointTableModel(), new MethodBreakpointPropertiesPanel(myProject));
      myMethodBreakpointsPanel.getAddBreakpointButton().setVisible(false);
      initPanel(myMethodBreakpointsPanel);

      addPanel(myLineBreakpointsPanel, LINE_BREAKPOINTS_NAME);
      addPanel(myExceptionBreakpointsPanel, EXCEPTION_BREAKPOINTS_NAME);
      addPanel(myFieldBreakpointsPanel, FIELD_WATCHPOINTS_NAME);
      addPanel(myMethodBreakpointsPanel, METHOD_BREAKPOINTS_NAME);

      ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet();
      new AnAction() {
        public void actionPerformed(AnActionEvent e){
          gotoSource();
        }
      }.registerCustomShortcutSet(shortcutSet, myLineBreakpointsPanel.getPanel());
      new AnAction() {
        public void actionPerformed(AnActionEvent e){
          gotoSource();
        }
      }.registerCustomShortcutSet(shortcutSet, myMethodBreakpointsPanel.getPanel());

      myTabbedPane.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          BreakpointPanel panel = getSelectedPanel();
          JTable table = panel.getTable();
          if (table.getRowCount() > 0 && table.getSelectedRow() < 0) {
            ListSelectionModel model = table.getSelectionModel();
            model.clearSelection();
            model.addSelectionInterval(0, 0);
            table.requestFocus();
          }
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

    private void addPanel(BreakpointPanel panel, final String title) {
      JPanel jpanel = panel.getPanel();
      jpanel.putClientProperty(BREAKPOINT_PANEL, panel);
      myTabbedPane.addTab(title, jpanel);
      final int tabIndex = myTabbedPane.getTabCount() - 1;
      panel.getTable().getModel().addTableModelListener(new TableModelListener() {
        public void tableChanged(TableModelEvent e) {
          updateTabTitle(tabIndex);
        }
      });
    }

    private BreakpointPanel getSelectedPanel() {
      JComponent selectedComponent = myTabbedPane.getSelectedComponent();
      return selectedComponent != null ? (BreakpointPanel)selectedComponent.getClientProperty(BREAKPOINT_PANEL) : null;
    }

    private void initPanel(final BreakpointPanel panel) {
      panel.getGotoSourceButton().addActionListener(myGotoSourceAction);
      panel.getViewSourceButton().addActionListener(myViewSourceAction);
      RemoveBreakpointAction removeBreakpointAction = new RemoveBreakpointAction(panel);
      panel.getRemoveBreakpointButton().addActionListener(removeBreakpointAction);
      panel.getTable().registerKeyboardAction(
        removeBreakpointAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
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
      myLineBreakpointsPanel.setBreakpoints(breakpointManager.getBreakpoints(BreakpointManager.LINE_BREAKPOINTS));
      myExceptionBreakpointsPanel.setBreakpoints(breakpointManager.getBreakpoints(BreakpointManager.EXCEPTION_BREAKPOINTS));
      myExceptionBreakpointsPanel.insertBreakpointAt(breakpointManager.getAnyExceptionBreakpoint(), 0);
      myFieldBreakpointsPanel.setBreakpoints(breakpointManager.getBreakpoints(BreakpointManager.FIELD_BREAKPOINTS));
      myMethodBreakpointsPanel.setBreakpoints(breakpointManager.getBreakpoints(BreakpointManager.METHOD_BREAKPOINTS));
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

    private AbstractAction myGotoSourceAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        gotoSource();
      }
    };

    private void gotoSource(){
      OpenFileDescriptor editSourceDescriptor = createEditSourceDescriptor();
      if (editSourceDescriptor == null) return;
      FileEditorManager.getInstance(myProject).openTextEditor(editSourceDescriptor, true);
      close(OK_EXIT_CODE);
    }

    private AbstractAction myViewSourceAction = new AbstractAction () {
      public void actionPerformed(ActionEvent e) {
        OpenFileDescriptor editSourceDescriptor = createEditSourceDescriptor();
        if (editSourceDescriptor == null) return;
        FileEditorManager.getInstance(myProject).openTextEditor(editSourceDescriptor, false);
      }
    };

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
      final int rowCount = panel.getBreakpointCount();
      for (int idx = 0; idx < rowCount; idx++) {
        if (panel.isBreakpointEnabled(idx)) {
          return true;
        }
      }
      return false;
    }

    private class AddExceptionBreakpointAction implements ActionListener {
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
        myExceptionBreakpointsPanel.getTable().requestFocus();
        updateAllTabTitles();
      }
    }

    private class AddFieldBreakpointAction implements ActionListener {
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
                    myFieldBreakpointsPanel.getTable().requestFocus();
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

    private class RemoveBreakpointAction implements ActionListener {
      private BreakpointPanel myPanel;

      public RemoveBreakpointAction(BreakpointPanel panel) {
        myPanel = panel;
      }

      public void actionPerformed(ActionEvent e) {
        Breakpoint[] breakpoints = myPanel.getSelectedBreakpoints();
        if (breakpoints != null) {
          for (int idx = 0; idx < breakpoints.length; idx++) {
            if (breakpoints[idx] instanceof AnyExceptionBreakpoint) {
              return;
            }
          }
          myPanel.removeSelectedBreakpoints();
          BreakpointManager manager = getBreakpointManager();
          for (int idx = 0; idx < breakpoints.length; idx++) {
            manager.removeBreakpoint(breakpoints[idx]);
          }
        }
        myPanel.getTable().requestFocus();
        updateAllTabTitles();
      }
    }

    private OpenFileDescriptor createEditSourceDescriptor() {
      BreakpointPanel breakpointsPanel = getSelectedPanel();
      if (breakpointsPanel == null) return null;
      Breakpoint[] breakpoints = breakpointsPanel.getSelectedBreakpoints();
      if (breakpoints == null || breakpoints.length == 0) return null;
      Breakpoint br = breakpoints[0];
      int line;
      Document doc;
      if (br instanceof BreakpointWithHighlighter) {
        BreakpointWithHighlighter breakpoint = (BreakpointWithHighlighter)br;
        doc = breakpoint.getDocument();
        line = breakpoint.getLineIndex();
      }
      else {
        return null;
      }
      if (line < 0 || line >= doc.getLineCount()) return null;
      int offset = doc.getLineStartOffset(line);
      if(br instanceof FieldBreakpoint) {
        PsiField field = ((FieldBreakpoint) br).getPsiField();
        if(field != null) {
          offset = field.getTextOffset();
        }
      }
      VirtualFile vFile = FileDocumentManager.getInstance().getFile(doc);
      return new OpenFileDescriptor(myProject, vFile, offset);
    }
  }

}
