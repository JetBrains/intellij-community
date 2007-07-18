package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 21, 2004
 */
public class ProjectNameStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  @NonNls private static final String PROJECT_FILE_EXTENSION = ".ipr";
  private NamePathComponent myNamePathComponent;
  private JPanel myPanel;
  private final WizardContext myWizardContext;
  private final StepSequence mySequence;
  private final WizardMode myMode;
  private JEditorPane myModuleDescriptionPane;
  private JList myTypesList;

  public ProjectNameStep(WizardContext wizardContext, StepSequence sequence, final WizardMode mode) {
    myWizardContext = wizardContext;
    mySequence = sequence;
    myMode = mode;
    myNamePathComponent = new NamePathComponent(IdeBundle.message("label.project.name"),
                                                IdeBundle.message("label.component.file.location",
                                                                  StringUtil.capitalize(wizardContext.getPresentationName())),
                                                'a', 'l', IdeBundle.message("title.select.project.file.directory", wizardContext.getPresentationName()),
                                                IdeBundle.message("description.select.project.file.directory",
                                                                  StringUtil.capitalize(wizardContext.getPresentationName())));
    //noinspection HardCodedStringLiteral
    final String initialProjectName = ProjectWizardUtil.findNonExistingFileName(myWizardContext.getProjectFileDirectory(), "untitled", "");
    myNamePathComponent.setPath(wizardContext.getProjectFileDirectory() + File.separator + initialProjectName);
    myNamePathComponent.setNameValue(initialProjectName);

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());
    final ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
    String appName = info.getVersionName();
    myPanel.add(new JLabel(IdeBundle.message("label.please.enter.project.name", appName, wizardContext.getPresentationName())), new GridBagConstraints(0,
                                                                                                                  GridBagConstraints.RELATIVE,
                                                                                                                  2, 1, 1.0, 0.0,
                                                                                                                  GridBagConstraints.NORTHWEST,
                                                                                                                  GridBagConstraints.HORIZONTAL,
                                                                                                                  new Insets(8, 10, 8, 10),
                                                                                                                  0, 0));

    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                            GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));


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
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
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
        fireStateChanged();
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
    final JLabel descriptionLabel = new JLabel(IdeBundle.message("label.description"));
    descriptionLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myPanel.add(descriptionLabel, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.SOUTHWEST,
                                                         GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    final JScrollPane typesListScrollPane = ScrollPaneFactory.createScrollPane(myTypesList);
    final Dimension preferredSize = calcTypeListPreferredSize(allModuleTypes);
    typesListScrollPane.setPreferredSize(preferredSize);
    typesListScrollPane.setMinimumSize(preferredSize);
    myPanel.add(typesListScrollPane, new GridBagConstraints(0, 3, 1, 1, 0.2, 1.0 ,
                                                            GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                            new Insets(0, 10, 0, 10), 0, 0));

    final JScrollPane descriptionScrollPane = ScrollPaneFactory.createScrollPane(myModuleDescriptionPane);
    descriptionScrollPane.setPreferredSize(new Dimension(preferredSize.width * 3, preferredSize.height));
    myPanel.add(descriptionScrollPane, new GridBagConstraints(1, 3, 1, 1, 0.8, 1.0,
                                                              GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 10),
                                                              0, 0));

  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  public String getHelpId() {
    return "project.new.page1";
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateStep() {
    super.updateStep();
    mySequence.setType(((ModuleType)myTypesList.getSelectedValue()).getId());
  }

  public void updateDataModel() {
    mySequence.setType(((ModuleType)myTypesList.getSelectedValue()).getId());
    myWizardContext.setProjectName(getProjectName());
    myWizardContext.setProjectFileDirectory(getProjectFileDirectory());
    myWizardContext.setProjectBuilder(myMode.getModuleBuilder());
  }

  public Icon getIcon() {
    return myWizardContext.getProject() == null ? NEW_PROJECT_ICON : ICON;
  }

  public void disposeUIResources() {
    super.disposeUIResources();
  }

  public boolean validate() throws ConfigurationException {
    String name = myNamePathComponent.getNameValue();
    if (name.length() == 0) {
      final ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
      throw new ConfigurationException(IdeBundle.message("prompt.new.project.file.name", info.getVersionName(), myWizardContext.getPresentationName()));
    }

    final String projectFileDirectory = getProjectFileDirectory();
    if (projectFileDirectory.length() == 0) {
      throw new ConfigurationException(IdeBundle.message("prompt.enter.project.file.location", myWizardContext.getPresentationName()));
    }

    final boolean shouldPromptCreation = myNamePathComponent.isPathChangedByUser();
    if (!ProjectWizardUtil
      .createDirectoryIfNotExists(IdeBundle.message("directory.project.file.directory", myWizardContext.getPresentationName()), projectFileDirectory, shouldPromptCreation)) {
      return false;
    }

    boolean shouldContinue = true;
    final File projectFile = new File(getProjectFilePath());
    if (projectFile.exists()) {
      int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", projectFile.getAbsolutePath(), myWizardContext.getPresentationName()),
                                            IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
      shouldContinue = (answer == 0);
    }

    return shouldContinue;
  }

  public String getProjectFilePath() {
    return getProjectFileDirectory() + "/" + myNamePathComponent.getNameValue()/*myTfProjectName.getText().trim()*/ +
           PROJECT_FILE_EXTENSION;
  }

  public String getProjectFileDirectory() {
    return myNamePathComponent.getPath();
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
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
}