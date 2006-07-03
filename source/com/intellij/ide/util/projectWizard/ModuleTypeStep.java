package com.intellij.ide.util.projectWizard;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 29, 2003
 */
public class ModuleTypeStep extends ModuleWizardStep {
  private JPanel myPanel;
  private JRadioButton myRbCreateNewModule;
  private JRadioButton myRbImportModule;
  private FieldPanel myModulePathFieldPanel;
  private JList myTypesList;
  private JEditorPane myModuleDescriptionPane;

  private ModuleType myModuleType = ModuleType.JAVA;
  private Runnable myDoubleClickAction = null;

  final EventDispatcher<UpdateListener> myEventDispatcher = EventDispatcher.create(UpdateListener.class);
  private final ButtonGroup myButtonGroup;

  public static interface UpdateListener extends EventListener{
    void moduleTypeSelected(ModuleType type);
    void importModuleOptionSelected(boolean selected);
  }

  public ModuleTypeStep(boolean createNewProject) {
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    myModuleDescriptionPane = new JEditorPane();
    myModuleDescriptionPane.setContentType(UIUtil.HTML_MIME);
    myModuleDescriptionPane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          try {
            BrowserUtil.launchBrowser(e.getURL().toString());
          }
          catch (IllegalThreadStateException ex) {
            // it's nnot a problem
          }
        }
      }
    });
    myModuleDescriptionPane.setEditable(false);

    final ModuleType[] allModuleTypes = ModuleTypeManager.getInstance().getRegisteredTypes();

    myTypesList = new JList(allModuleTypes);
    myTypesList.setSelectionModel(new PermanentSingleSelectionModel());
    myTypesList.setCellRenderer(new ModuleTypesListCellRenderer());
    myTypesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        final ModuleType typeSelected = (ModuleType)myTypesList.getSelectedValue();
        myModuleType = typeSelected;
        //noinspection HardCodedStringLiteral
        myModuleDescriptionPane.setText("<html><body><font face=\"verdana\" size=\"-1\">"+typeSelected.getDescription()+"</font></body></html>");
        myEventDispatcher.getMulticaster().moduleTypeSelected(typeSelected);
      }
    });
    myTypesList.setSelectedIndex(0);
    myTypesList.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2){
            if (myDoubleClickAction != null) {
              if (myTypesList.getSelectedValue() != null) {
                myDoubleClickAction.run();
              }
            }
          }
        }
      }
    );

    myRbCreateNewModule = new JRadioButton(IdeBundle.message("radio.create.new.module"), true);
    myRbImportModule = new JRadioButton(IdeBundle.message("radio.import.existing.module"));
    myButtonGroup = new ButtonGroup();
    myButtonGroup.add(myRbCreateNewModule);
    myButtonGroup.add(myRbImportModule);
    ModulesRbListener listener = new ModulesRbListener();
    myRbCreateNewModule.addItemListener(listener);
    myRbImportModule.addItemListener(listener);

    JTextField tfModuleFilePath = new JTextField();
    final String productName = ApplicationNamesInfo.getInstance().getProductName();
    myModulePathFieldPanel = createFieldPanel(tfModuleFilePath, IdeBundle.message("label.path.to.module.file", productName),
                                              new BrowseFilesListener( tfModuleFilePath,
                                                                       IdeBundle.message("prompt.select.module.file.to.import", productName), null,
                                                                       new ModuleFileChooserDescriptor()));
    myModulePathFieldPanel.setEnabled(false);

    if (createNewProject) {
      final JLabel moduleTypeLabel = new JLabel(IdeBundle.message("label.select.module.type"));
      moduleTypeLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
      myPanel.add(moduleTypeLabel, LABEL_CONSTRAINT);
    }
    else {
      myPanel.add(myRbCreateNewModule, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 8, 10), 0, 0));
    }
    final JLabel descriptionLabel = new JLabel(IdeBundle.message("label.description"));
    descriptionLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myPanel.add(descriptionLabel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    final JScrollPane typesListScrollPane = ScrollPaneFactory.createScrollPane(myTypesList);
    final Dimension preferredSize = calcTypeListPreferredSize(allModuleTypes);
    typesListScrollPane.setPreferredSize(preferredSize);
    typesListScrollPane.setMinimumSize(preferredSize);
    myPanel.add(typesListScrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.2, (createNewProject? 1.0 : 0.0), GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, createNewProject? 10 : 30, 0, 10), 0, 0));

    final JScrollPane descriptionScrollPane = ScrollPaneFactory.createScrollPane(myModuleDescriptionPane);
    descriptionScrollPane.setPreferredSize(new Dimension(preferredSize.width * 3, preferredSize.height));
    myPanel.add(descriptionScrollPane, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.8, (createNewProject? 1.0 : 0.0), GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 10), 0, 0));

    if (!createNewProject) {
      myPanel.add(myRbImportModule, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(16, 10, 0, 10), 0, 0));
      myPanel.add(myModulePathFieldPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 30, 0, 10), 0, 0));

    }
  }

  private Dimension calcTypeListPreferredSize(final ModuleType[] allModuleTypes) {
    int width = 0;
    int height = 0;
    final FontMetrics fontMetrics = myTypesList.getFontMetrics(myTypesList.getFont());
    final int fontHeight = fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent();
    for (final ModuleType type : allModuleTypes) {
      final Icon icon = type.getBigIcon();
      final int iconHeight = icon != null ? icon.getIconHeight() : 0;
      final int iconWidth = icon != null ? icon.getIconWidth() : 0;
      height += Math.max(iconHeight, fontHeight) + 6;
      width = Math.max(width, iconWidth + fontMetrics.stringWidth(type.getName()) + 10);
    }
    return new Dimension(width, height);
  }

  public String getHelpId() {
    return "project.creatingModules.page1";
  }

  public void setModuleListDoubleClickAction(Runnable runnable) {
    myDoubleClickAction = runnable;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public Icon getIcon() {
    return ICON;
  }

  public boolean validate() {
    if (myRbImportModule.isSelected()) {
      final String path = myModulePathFieldPanel.getText().trim();
      if (path.length() == 0) {
        Messages.showErrorDialog(
          IdeBundle.message("error.please.specify.path.to.module.file", ApplicationNamesInfo.getInstance().getProductName()),
          IdeBundle.message("title.module.file.path.not.specified"));
        myModulePathFieldPanel.getTextField().requestFocus();
        return false;
      }
      final File file = new File(path);
      if (!file.exists()) {
        Messages.showErrorDialog(IdeBundle.message("error.module.file.does.not.exist"), IdeBundle.message("title.module.file.does.not.exist"));
        myModulePathFieldPanel.getTextField().requestFocus();
        return false;
      }
      if (!StdFileTypes.IDEA_MODULE.equals(FileTypeManager.getInstance().getFileTypeByFileName(file.getName()))) {
        Messages.showErrorDialog(IdeBundle.message("error.module.not.iml", path, ApplicationNamesInfo.getInstance().getProductName()),
                                 IdeBundle.message("title.incorrect.file.type"));
        myModulePathFieldPanel.getTextField().requestFocus();
        return false;
      }
    }
    return true;
  }

  public boolean isNextButtonEnabled() {
    return !myRbImportModule.isSelected();
  }
  public boolean isCreateNewModule() {
    return myRbCreateNewModule.isSelected();
  }
  public boolean isImportExistingModule() {
    return myRbImportModule.isSelected();
  }

  public String getModuleFilePath() {
    return myModulePathFieldPanel.getText().trim().replace(File.separatorChar, '/');
  }

  public ModuleType getModuleType() {
    return myModuleType;
  }

  public void addUpdateListener(UpdateListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeUpdateListener(UpdateListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public void updateDataModel() {
  }

  public JComponent getPreferredFocusedComponent() {
    return myTypesList;
  }

  private class ModulesRbListener implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      final JComponent toFocus;
      ButtonModel selection = myButtonGroup.getSelection();
      setControlsEnabled(selection);
      if (selection == myRbCreateNewModule.getModel()) {
        toFocus = myTypesList;
        myEventDispatcher.getMulticaster().importModuleOptionSelected(false);
      }
      else if (selection == myRbImportModule.getModel()) { // import existing
        toFocus = myModulePathFieldPanel.getTextField();
        myEventDispatcher.getMulticaster().importModuleOptionSelected(true);
      }
      else {
        toFocus = null;
      }

      if (toFocus != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            toFocus.requestFocus();
          }
        });
      }
    }
  }

  private void setControlsEnabled(ButtonModel selection) {
    boolean newModuleEnabled = selection == myRbCreateNewModule.getModel();
    myTypesList.setEnabled(newModuleEnabled);
    myModuleDescriptionPane.setEnabled(newModuleEnabled);

    boolean importModuleEnabled = selection == myRbImportModule.getModel();
    myModulePathFieldPanel.setEnabled(importModuleEnabled);
  }

  private static class ModuleFileChooserDescriptor extends FileChooserDescriptor {
    public ModuleFileChooserDescriptor() {
      super(true, false, false, false, false, false);
      setHideIgnored(false);
    }

    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      final boolean isVisible = super.isFileVisible(file, showHiddenFiles);
      if (!isVisible || file.isDirectory()) {
        return isVisible;
      }
      return StdFileTypes.IDEA_MODULE.equals(FileTypeManager.getInstance().getFileTypeByFile(file));
    }
  }

  private static class ModuleTypesListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      final JLabel rendererComponent = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      final ModuleType moduleType = (ModuleType)value;
      rendererComponent.setIcon(moduleType.getBigIcon());
      rendererComponent.setText(moduleType.getName());
      return rendererComponent;
    }
  }

  private static class PermanentSingleSelectionModel extends DefaultListSelectionModel {
    public PermanentSingleSelectionModel() {
      super.setSelectionMode(SINGLE_SELECTION);
    }

    public final void setSelectionMode(int selectionMode) {
    }

    public final void removeSelectionInterval(int index0, int index1) {
    }
  }

}
