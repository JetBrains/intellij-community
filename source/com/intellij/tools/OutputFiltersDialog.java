/**
 * @author Yura Cangea
 */
package com.intellij.tools;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.*;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class OutputFiltersDialog extends DialogWrapper {
  private final DefaultListModel myFiltersModel = new DefaultListModel();
  private final JList myFiltersList = new JList(myFiltersModel);
  private final JButton myAddButton = new JButton("Add...");
  private final JButton myEditButton = new JButton("Edit...");
  private final JButton myRemoveButton = new JButton("Remove");
  private final JButton myMoveUpButton = new JButton("Move Up");
  private final JButton myMoveDownButton = new JButton("Move Down");
  private final CommandButtonGroup myButtonGroup = new CommandButtonGroup(BoxLayout.Y_AXIS);
  private boolean myModified = false;
  private FilterInfo[] myFilters;

  public OutputFiltersDialog(Component parent, FilterInfo[] filters) {
    super(parent, true);
    myFilters = filters;

    setTitle("Output Filters");
    init();
    initGui();

    myAddButton.setMnemonic('A');
    myEditButton.setMnemonic('d');
    myRemoveButton.setMnemonic('R');
    myMoveUpButton.setMnemonic('U');
    myMoveDownButton.setMnemonic('o');
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("preferences.externalToolsFilters");
  }

  private void initGui() {
    myFiltersList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        FilterInfo info = (FilterInfo)value;
        append(info.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });

    myButtonGroup.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0));

    myButtonGroup.addButton(myAddButton);
    myButtonGroup.addButton(myEditButton);
    myButtonGroup.addButton(myRemoveButton);
    myButtonGroup.addButton(myMoveUpButton);
    myButtonGroup.addButton(myMoveDownButton);

    myEditButton.setEnabled(false);
    myRemoveButton.setEnabled(false);
    myMoveUpButton.setEnabled(false);
    myMoveDownButton.setEnabled(false);

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FilterInfo filterInfo = new FilterInfo();
        filterInfo.setName(suggestFilterName());
        boolean wasCreated = FilterDialog.editFilter(filterInfo, myAddButton, "Add Filter");
        if (wasCreated) {
          myFiltersModel.addElement(filterInfo);
          setModified(true);
          enableButtons();
        }
        myFiltersList.requestFocus();
      }
    });

    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int index = myFiltersList.getSelectedIndex();
        FilterInfo filterInfo = (FilterInfo)myFiltersModel.getElementAt(index);
        boolean wasEdited = FilterDialog.editFilter(filterInfo, myEditButton, "Edit filter");
        if (wasEdited) {
          setModified(true);
          enableButtons();
        }
        myFiltersList.requestFocus();
      }
    });


    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myFiltersList.getSelectedIndex() >= 0) {
          myFiltersModel.removeElementAt(myFiltersList.getSelectedIndex());
          setModified(true);
        }
        enableButtons();
        myFiltersList.requestFocus();
      }
    });
    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int movedCount = ListUtil.moveSelectedItemsUp(myFiltersList);
        if (movedCount > 0) {
          setModified(true);
        }
        myFiltersList.requestFocus();
      }
    });
    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int movedCount = ListUtil.moveSelectedItemsDown(myFiltersList);
        if (movedCount > 0) {
          setModified(true);
        }
        myFiltersList.requestFocus();
      }
    });

    myFiltersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myFiltersList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        enableButtons();
      }
    });

    ListScrollingUtil.ensureSelectionExists(myFiltersList);
  }

  private String suggestFilterName(){
    String prefix = "Filter ";

    int number = 1;
    for (int i=0; i < myFiltersModel.getSize(); i++) {
      FilterInfo wrapper = (FilterInfo)myFiltersModel.getElementAt(i);
      String name = wrapper.getName();
      if (name.startsWith(prefix)) {
        try {
          int n = Integer.valueOf(name.substring(prefix.length()).trim()).intValue();
          number = Math.max(number, n + 1);
        }
        catch (NumberFormatException e) {
        }
      }
    }

    return prefix + number;
  }

  protected void doOKAction() {
    if (myModified) {
      myFilters = new FilterInfo[myFiltersModel.getSize()];
      for (int i = 0; i < myFiltersModel.getSize(); i++) {
        myFilters[i] = (FilterInfo)myFiltersModel.get(i);
      }
    }
    super.doOKAction();
  }

  protected JComponent createCenterPanel() {
    for (int i = 0; i < myFilters.length; i++) {
      myFiltersModel.addElement(myFilters[i].createCopy());
    }

    JPanel panel = new JPanel(new BorderLayout());

    panel.add(new JScrollPane(myFiltersList), BorderLayout.CENTER);
    panel.add(myButtonGroup, BorderLayout.EAST);

    panel.setPreferredSize(new Dimension(400, 200));

    return panel;
  }

  private void enableButtons() {
    int size = myFiltersModel.getSize();
    int index = myFiltersList.getSelectedIndex();
    myEditButton.setEnabled(size != 0 && index != -1);
    myRemoveButton.setEnabled(size != 0 & index != -1);
    myMoveUpButton.setEnabled(ListUtil.canMoveSelectedItemsUp(myFiltersList));
    myMoveDownButton.setEnabled(ListUtil.canMoveSelectedItemsDown(myFiltersList));
  }

  public JComponent getPreferredFocusedComponent() {
    return myFiltersList;
  }

  private void setModified(boolean modified) {
    myModified = modified;
  }

  public FilterInfo[] getData() {
    return myFilters;
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.tools.OutputFiltersDialog";
  }
}