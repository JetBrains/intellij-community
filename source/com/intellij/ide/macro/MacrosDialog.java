package com.intellij.ide.macro;

import com.intellij.ant.impl.MapDataContext;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public final class MacrosDialog extends DialogWrapper {
  private final DefaultListModel myMacrosModel;
  private final JList myMacrosList;
  private final JTextArea myPreviewTextarea;

  public MacrosDialog(Project project) {
    super(project, true);
    MacroManager.getInstance().cacheMacrosPreview(MapDataContext.singleData(DataConstants.PROJECT, project));
    setTitle(IdeBundle.message("title.macros"));
    setOKButtonText(IdeBundle.message("button.insert"));

    myMacrosModel = new DefaultListModel();
    myMacrosList = new JList(myMacrosModel);
    myPreviewTextarea = new JTextArea();

    init();
  }

  public MacrosDialog(Component parent) {
    super(parent, true);
    MacroManager.getInstance().cacheMacrosPreview(DataManager.getInstance().getDataContext(parent));
    setTitle(IdeBundle.message("title.macros"));
    setOKButtonText(IdeBundle.message("button.insert"));

    myMacrosModel = new DefaultListModel();
    myMacrosList = new JList(myMacrosModel);
    myPreviewTextarea = new JTextArea();

    init();
  }

  protected void init() {
    super.init();

    java.util.List<Macro> macros = new ArrayList<Macro>(MacroManager.getInstance().getMacros());
    Collections.sort(macros, new Comparator<Macro>() {
      public int compare(Macro macro1, Macro macro2) {
        String name1 = macro1.getName();
        String name2 = macro2.getName();
        if (!StringUtil.startsWithChar(name1, '/')) {
          name1 = ZERO + name1;
        }
        if (!StringUtil.startsWithChar(name2, '/')) {
          name2 = ZERO + name2;
        }
        return name1.compareToIgnoreCase(name2);
      }
      private final String ZERO = new String(new char[] {0});
    });
    for (Macro macro : macros) {
      myMacrosModel.addElement(new MacroWrapper(macro));
    }

    addListeners();
    if (myMacrosModel.size() > 0){
      myMacrosList.setSelectedIndex(0);
    }
    else{
      setOKActionEnabled(false);
    }
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("preferences.externalToolsMacro");
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.ide.macro.MacrosDialog";
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints constr;

    // list label
    constr = new GridBagConstraints();
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 5, 0, 5);
    panel.add(new JLabel(IdeBundle.message("label.macros")), constr);

    // macros list
    constr = new GridBagConstraints();
    constr.gridy = 1;
    constr.weightx = 1;
    constr.weighty = 1;
    constr.insets = new Insets(0, 5, 0, 5);
    constr.fill = GridBagConstraints.BOTH;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(new JScrollPane(myMacrosList), constr);
    myMacrosList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myMacrosList.setPreferredSize(null);

    // preview label
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 2;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 5, 0, 5);
    panel.add(new JLabel(IdeBundle.message("label.macro.preview")), constr);

    // preview
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 3;
    constr.weightx = 1;
    constr.weighty = 1;
    constr.fill = GridBagConstraints.BOTH;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(0, 5, 5, 5);
    panel.add(new JScrollPane(myPreviewTextarea), constr);
    myPreviewTextarea.setEditable(false);
    myPreviewTextarea.setLineWrap(true);
    myPreviewTextarea.setPreferredSize(null);

    panel.setPreferredSize(new Dimension(400, 500));

    return panel;
  }

  protected JComponent createNorthPanel() {
    return null;
  }

  /**
   * Macro info shown in list
   */
  private static final class MacroWrapper {
    private final Macro myMacro;

    public MacroWrapper(Macro macro) {
      myMacro = macro;
    }

    public String toString() {
      return myMacro.getName() + " - " + myMacro.getDescription();
    }
  }

  private void addListeners() {
    myMacrosList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          Macro macro = getSelectedMacro();
          if (macro == null){
            myPreviewTextarea.setText("");
            setOKActionEnabled(false);
          }
          else{
            myPreviewTextarea.setText(macro.preview());
            setOKActionEnabled(true);
          }
        }
      }
    );

    // doubleclick support
    myMacrosList.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if ((e.getClickCount() == 2) && (getSelectedMacro() != null)){
            close(OK_EXIT_CODE);
          }
        }
      }
    );

  }

  public Macro getSelectedMacro() {
    MacroWrapper macroWrapper = (MacroWrapper)myMacrosList.getSelectedValue();
    if (macroWrapper != null){
      return macroWrapper.myMacro;
    }
    return null;
  }

  public JComponent getPreferredFocusedComponent() {
    return myMacrosList;
  }
}