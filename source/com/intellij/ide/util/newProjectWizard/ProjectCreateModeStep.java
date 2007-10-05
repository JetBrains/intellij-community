/*
 * User: anna
 * Date: 08-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.newProjectWizard.modes.CreateFromScratchMode;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromSourcesMode;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class ProjectCreateModeStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");


  private JPanel myWholePanel;

  private WizardMode myMode;
  private final List<WizardMode> myModes = new ArrayList<WizardMode>();
  private final WizardContext myWizardContext;

  public ProjectCreateModeStep(final String defaultPath, final WizardContext wizardContext) {
    final StringBuffer buf = new StringBuffer();
    for (WizardMode mode : Extensions.getExtensions(WizardMode.MODES)) {
      if (mode.isAvailable(wizardContext)) {
        myModes.add(mode);
        if (defaultPath != null) {
          if (mode instanceof CreateFromSourcesMode) {
            myMode = mode;
          }
        } else if (mode instanceof CreateFromScratchMode) {
          myMode = mode;
        }
      }
      final String footnote = mode.getFootnote(wizardContext);
      if (footnote != null) {
        if (buf.length() > 0) buf.append("<br>");
        buf.append(footnote);
      }
    }
    myWizardContext = wizardContext;
    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(BorderFactory.createEtchedBorder());

    final Insets insets = new Insets(0, 0, 0, 5);
    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.HORIZONTAL, insets, 0, 0);
    final ButtonGroup group = new ButtonGroup();
    for (final WizardMode mode : myModes) {
      insets.top = 15;
      insets.left = 5;
      final JRadioButton rb = new JRadioButton(mode.getDisplayName(wizardContext), mode == myMode);
      rb.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
      rb.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myMode.onChosen(false);
          myMode = mode;
          myMode.onChosen(true);
          update();
        }
      });

      myWholePanel.add(rb, gc);
      group.add(rb);
      insets.top = 5;
      insets.left = 20;
      final JLabel description = new JLabel(mode.getDescription(wizardContext));
      myWholePanel.add(description, gc);
      final JComponent settings = mode.getAdditionalSettings();
      if (settings != null) {
        myWholePanel.add(settings, gc);
      }
    }
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    myWholePanel.add(Box.createVerticalBox(), gc);
    final JLabel note = new JLabel( "<html>" + buf.toString() + "</html>", IconLoader.getIcon("/nodes/warningIntroduction.png"), SwingUtilities.LEFT);
    note.setVisible(buf.length() > 0);
    gc.weighty = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.insets.bottom = 5;
    myWholePanel.add(note, gc);
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  public void updateDataModel() {
    myWizardContext.setProjectBuilder(myMode.getModuleBuilder());
  }

  public Icon getIcon() {
    return myWizardContext.getProject() == null ? NEW_PROJECT_ICON : ICON;
  }

  public WizardMode getMode() {
    return myMode;
  }

  public void disposeUIResources() {
    super.disposeUIResources();
    for (WizardMode mode : myModes) {
      Disposer.dispose(mode);
    }
  }

  protected void update() {
  }

  @NonNls
  public String getHelpId() {
    return myWizardContext.getProject() == null ? "reference.dialogs.new.project" : "reference.dialogs.new.module";
  }

  public List<WizardMode> getModes() {
    return myModes;
  }
}