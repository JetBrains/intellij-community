package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ProjectNameWithTypeStep extends ProjectNameStep {
  private JEditorPane myModuleDescriptionPane;
  private JList myTypesList;
  private LabeledComponent<JTextField> myModuleName = new LabeledComponent<JTextField>();
  private boolean myInSync = false;
  private boolean myChangedByUser = false;
  @NonNls private static final String NAME = "NAME";
  @NonNls private static final String EMPTY = "EMPTY";

  public ProjectNameWithTypeStep(WizardContext wizardContext, StepSequence sequence, final WizardMode mode) {
    super(wizardContext, sequence, mode);
    final JTextField component = new JTextField();
    component.setText(myNamePathComponent.getNameValue());
    myModuleName.setComponent(component);
    myModuleName.setText(ProjectBundle.message("project.new.wizard.module.name.title"));
    myModuleName.getLabel().setFont(myModuleName.getFont().deriveFont(Font.BOLD));
    final CardLayout card = new CardLayout();
    final JPanel moduleNamePanel = new JPanel(card);
    final JPanel nonEmpty = new JPanel(new BorderLayout());
    moduleNamePanel.add(NAME, nonEmpty);
    nonEmpty.add(myModuleName, BorderLayout.CENTER);
    moduleNamePanel.add(EMPTY, new JPanel(new BorderLayout()));
    card.show(moduleNamePanel, NAME);
    moduleNamePanel.setVisible(myWizardContext.getProject() == null);
    myAdditionalContentPanel.add(moduleNamePanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTHWEST,
                                                                        GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 0));
    component.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myInSync) return;
        myChangedByUser = true;
      }
    });

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

    ModuleType[] allModuleTypes = ModuleTypeManager.getInstance().getRegisteredTypes();
    if (myWizardContext.getProject() != null) {
      allModuleTypes = ArrayUtil.remove(allModuleTypes, ArrayUtil.find(allModuleTypes, ModuleType.EMPTY));
    }
    myTypesList = new JList(allModuleTypes);
    myTypesList.setSelectionModel(new PermanentSingleSelectionModel());
    myTypesList.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final ModuleType moduleType = (ModuleType)value;
        setIcon(moduleType.getBigIcon());
        setDisabledIcon(moduleType.getBigIcon());
        setText(myWizardContext.getProject() == null ? moduleType.getProjectType() : moduleType.getName());
        return rendererComponent;
      }
    });
    myTypesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }

        final ModuleType typeSelected = (ModuleType)myTypesList.getSelectedValue();
        //noinspection HardCodedStringLiteral
        myModuleDescriptionPane
          .setText("<html><body><font face=\"verdana\" size=\"-1\">" + typeSelected.getDescription() + "</font></body></html>");
        moduleNamePanel.setVisible(myWizardContext.getProject() == null);
        card.show(moduleNamePanel, typeSelected != ModuleType.EMPTY ? NAME : EMPTY);
        fireStateChanged();
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            myTypesList.requestFocusInWindow();
          }
        });
      }
    });
    myTypesList.setSelectedIndex(0);
    myTypesList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          //todo
        }
      }
    });


    myNamePathComponent.getNameComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        myInSync = true;
        if (!myChangedByUser) {
          myModuleName.getComponent().setText(myNamePathComponent.getNameValue());
        }
        myInSync = false;
      }
    });


    final JLabel descriptionLabel = new JLabel(IdeBundle.message("label.description"));
    descriptionLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myAdditionalContentPanel.add(Box.createHorizontalGlue(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    myAdditionalContentPanel.add(descriptionLabel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    final JScrollPane typesListScrollPane = ScrollPaneFactory.createScrollPane(myTypesList);
    final Dimension preferredSize = calcTypeListPreferredSize(allModuleTypes);
    typesListScrollPane.setPreferredSize(preferredSize);
    typesListScrollPane.setMinimumSize(preferredSize);
    myAdditionalContentPanel.add(typesListScrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.2, 1.0 , GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    final JScrollPane descriptionScrollPane = ScrollPaneFactory.createScrollPane(myModuleDescriptionPane);
    descriptionScrollPane.setPreferredSize(new Dimension(preferredSize.width * 3, preferredSize.height));
    myAdditionalContentPanel.add(descriptionScrollPane, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.8, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
  }

  public void updateStep() {
    super.updateStep();
    mySequence.setType(((ModuleType)myTypesList.getSelectedValue()).getId());
  }

  public void updateDataModel() {
    mySequence.setType(((ModuleType)myTypesList.getSelectedValue()).getId());
    super.updateDataModel();
    final ModuleBuilder builder = (ModuleBuilder)myMode.getModuleBuilder();
    assert builder != null;
    builder.setName(myModuleName.getComponent().getText());
  }

  public void disposeUIResources() {
    super.disposeUIResources();
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

  private static class PermanentSingleSelectionModel extends DefaultListSelectionModel {
    public PermanentSingleSelectionModel() {
      super.setSelectionMode(SINGLE_SELECTION);
    }

    public final void setSelectionMode(int selectionMode) {
    }

    public final void removeSelectionInterval(int index0, int index1) {
    }
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch";
  }
}