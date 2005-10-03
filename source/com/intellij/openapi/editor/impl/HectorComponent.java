package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileManager;
import com.intellij.codeInspection.ex.InspectionToolsPanel;
import com.intellij.codeInspection.ui.ApplyAction;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.ui.*;
import com.intellij.CommonBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.Hashtable;

/**
 * User: anna
 * Date: Jun 27, 2005
 */
public class HectorComponent extends JPanel {
  private JCheckBox myImportPopupCheckBox = new JCheckBox(EditorBundle.message("hector.import.popup.checkbox"));
  private ComboboxWithBrowseButton myProfilesCombo = new ComboboxWithBrowseButton();
  private JCheckBox myUsePerFileProfile = new JCheckBox(EditorBundle.message("hector.use.custom.profile.for.this.file.checkbox"));

  private static final Icon GC_ICON = IconLoader.getIcon("/actions/gc.png");
  private JButton myClearSettingsButton = new JButton(GC_ICON);

  private JSlider[] mySliders;
  private PsiFile myFile;

  private boolean myImportPopupOn;
  private String myProfile;
  private boolean myUseProfile;
  private LightweightHint myHint;

  private final String myTitle = EditorBundle.message("hector.highlighting.level.title");

  public HectorComponent(PsiFile file) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEtchedBorder());
    myFile = file;
    mySliders = new JSlider[file instanceof JspFile ? file.getPsiRoots().length - 1 : 1];

    final Project project = myFile.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = myFile.getContainingFile().getVirtualFile();
    final boolean notInLibrary = (!fileIndex.isInLibrarySource(virtualFile) && !fileIndex.isInLibraryClasses(virtualFile)) ||
                                  fileIndex.isInContent(virtualFile);
    for (int i = 0; i < mySliders.length; i++) {

      final Hashtable<Integer, JLabel> sliderLabels = new Hashtable<Integer, JLabel>();
      sliderLabels.put(new Integer(1), new JLabel(EditorBundle.message("hector.none.slider.label")));
      sliderLabels.put(new Integer(2), new JLabel(EditorBundle.message("hector.syntax.slider.label")));
      if (notInLibrary) {
        sliderLabels.put(new Integer(3), new JLabel(EditorBundle.message("hector.inspections.slider.label")));
      }

      final JSlider slider = new JSlider(JSlider.VERTICAL, 1, notInLibrary ? 3 : 2, 1);
      slider.setLabelTable(sliderLabels);
      final boolean value = Boolean.TRUE;
      UIUtil.setSliderIsFilled(slider, value);
      slider.setPaintLabels(true);
      slider.setSnapToTicks(true);
      slider.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          int value = slider.getValue();
          for (Enumeration<Integer> enumeration = sliderLabels.keys(); enumeration.hasMoreElements();) {
            Integer key = enumeration.nextElement();
            sliderLabels.get(key).setForeground(key.intValue() <= value ? Color.black : new Color(100, 100, 100));
          }
        }
      });
      final PsiFile psiRoot = myFile.getPsiRoots()[i];
      slider.setValue(getValue(HighlightUtil.isRootHighlighted(psiRoot), HighlightUtil.isRootInspected(psiRoot)));
      mySliders[i] = slider;
    }

    final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(myFile.getProject());
    myImportPopupOn = analyzer.isImportHintsEnabled(myFile);
    myImportPopupCheckBox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myImportPopupOn = myImportPopupCheckBox.isSelected();
      }
    });
    myImportPopupCheckBox.setSelected(myImportPopupOn);
    myImportPopupCheckBox.setEnabled(analyzer.isAutohintsAvailable(myFile));
    myImportPopupCheckBox.setVisible(notInLibrary);

    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    add(myImportPopupCheckBox, gc);
    myClearSettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = myFile.getProject();
        HighlightingSettingsPerFile.getInstance(project).resetAllFilesToUseGlobalSettings();
        final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
        analyzer.resetImportHintsEnabledForProject();
        analyzer.restart();
        myProfilesCombo.getComboBox().setSelectedItem(DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().getName());
        myProfilesCombo.setEnabled(false);
        myUsePerFileProfile.setSelected(false);
        for (int i = 0; i < mySliders.length; i++) {
          final PsiFile psiRoot = myFile.getPsiRoots()[i];
          mySliders[i].setValue(getValue(HighlightUtil.isRootHighlighted(psiRoot), HighlightUtil.isRootInspected(psiRoot)));
        }
      }
    });
    myClearSettingsButton.setToolTipText(EditorBundle.message("hector.clear.settings.button.tooltip"));
    myClearSettingsButton.setPreferredSize(new Dimension(GC_ICON.getIconWidth() + 4, GC_ICON.getIconHeight() + 4));
    add(myClearSettingsButton,
        new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(2, 2, 0, 2), 0, 0));

    gc.gridwidth = 2;
    gc.weightx = 1.0;
    gc.gridx = 0;
    gc.gridy = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.anchor = GridBagConstraints.WEST;
    final JPanel inspectionProfilePanel = createInspectionProfilePanel();
    add(inspectionProfilePanel, gc);
    inspectionProfilePanel.setVisible(notInLibrary);

    JPanel panel = new JPanel(new GridBagLayout());

    panel.setBorder(IdeBorderFactory.createTitledBorder(myTitle));
    final boolean addLabel = mySliders.length > 1;
    if (addLabel) {
      layoutVertical(panel);
    }
    else {
      layoutHorizontal(panel);
    }
    gc.gridx = 0;
    gc.gridy = 2;
    gc.weighty = 1.0;
    gc.fill = GridBagConstraints.BOTH;
    add(panel, gc);
  }

  public Dimension getPreferredSize() {
    final Dimension preferredSize = super.getPreferredSize();
    final int width = getFontMetrics(getFont()).stringWidth(myTitle) + 60;
    if (preferredSize.width < width){
      preferredSize.width = width;
    }
    return preferredSize;
  }

  private JPanel createInspectionProfilePanel() {
    JPanel profilePanel = new JPanel(new GridBagLayout());
    myUsePerFileProfile.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myProfilesCombo.setEnabled(myUsePerFileProfile.isSelected());
      }
    });
    final Pair<String, Boolean> inspectionProfile = HighlightingSettingsPerFile.getInstance(myFile.getProject()).getInspectionProfile(myFile);
    myUseProfile = inspectionProfile != null && inspectionProfile.second;
    myUsePerFileProfile.setSelected(myUseProfile);
    profilePanel.add(myUsePerFileProfile, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                                 new Insets(0, 0, 0, 0), 0, 0));
    final InspectionProfileManager inspectionManager = InspectionProfileManager.getInstance();

    myProfilesCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myProfile = (String)myProfilesCombo.getComboBox().getSelectedItem();
      }
    });
    reloadProfiles(inspectionManager,
                   inspectionProfile == null ? DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(myFile).getName() : inspectionProfile.first);
    myProfilesCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myHint != null && myHint.isVisible()) myHint.hide();
        ErrorsDialog errorsDialog = new ErrorsDialog((String)myProfilesCombo.getComboBox().getSelectedItem(),
                                                     myFile.getProject());
        errorsDialog.show();
      }
    });
    myProfilesCombo.setEnabled(myUseProfile);
    profilePanel.add(myProfilesCombo, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                             new Insets(0, 20, 0, 2), 0, 0));
    return profilePanel;
  }

  private void reloadProfiles(final InspectionProfileManager inspectionManager, final String selectedProfile) {
    final String[] avaliableProfileNames = inspectionManager.getAvaliableProfileNames();
    final DefaultComboBoxModel model = (DefaultComboBoxModel)myProfilesCombo.getComboBox().getModel();
    model.removeAllElements();
    for (String profile : avaliableProfileNames) {
      model.addElement(profile);
    }
    myProfilesCombo.getComboBox().setSelectedItem(selectedProfile);
  }

  private void layoutHorizontal(final JPanel panel) {
    for (JSlider slider : mySliders) {
      slider.setOrientation(JSlider.HORIZONTAL);
      slider.setPreferredSize(new Dimension(100, 40));
      panel.add(slider, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                               new Insets(0, 0, 0, 0), 0, 0));
    }
  }

  private void layoutVertical(final JPanel panel) {
    for (int i = 0; i < mySliders.length; i++) {
      JPanel borderPanel = new JPanel(new BorderLayout());
      mySliders[i].setPreferredSize(new Dimension(80, 100));
      borderPanel.add(new JLabel(myFile.getPsiRoots()[i].getLanguage().getID()), BorderLayout.NORTH);
      borderPanel.add(mySliders[i], BorderLayout.CENTER);
      panel.add(borderPanel, new GridBagConstraints(i, 1, 1, 1, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
                                                    new Insets(0, 0, 0, 0), 0, 0));
    }
  }

  public void showComponent(Editor editor, Point point) {
    ToolWindowManager.getInstance(myFile.getProject()).activateEditorComponent();
    myHint = new LightweightHint(this);
    myHint.addHintListener(new HintListener() {
      public void hintHidden(EventObject event) {
        onClose();
      }
    });
    final HintManager hintManager = HintManager.getInstance();
    final Hint previousHint = hintManager.findHintByType(HectorComponent.class);
    if (previousHint != null) {
      previousHint.hide();
    }
    else {
      hintManager
        .showEditorHint(myHint, editor, point, HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_SCROLLING,
                        0,
                        true);
    }
  }

  private void onClose() {
    if (isModified()) {
      forceDaemonRestart();
      ((StatusBarEx)WindowManager.getInstance().getStatusBar(myFile.getProject())).updateEditorHighlightingStatus(false);
    }
  }

  private void forceDaemonRestart() {
    for (int i = 0; i < mySliders.length; i++) {
      PsiElement root = myFile.getPsiRoots()[i];
      int value = mySliders[i].getValue();
      if (value == 1) {
        HighlightUtil.forceRootHighlighting(root, false);
      }
      else if (value == 2) {
        HighlightUtil.forceRootInspection(root, false);
      }
      else {
        HighlightUtil.forceRootInspection(root, true);
      }
    }
    HighlightingSettingsPerFile.getInstance(myFile.getProject())
      .setInspectionProfile((String)myProfilesCombo.getComboBox().getSelectedItem(), myUsePerFileProfile.isSelected(), myFile);
    final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(myFile.getProject());
    analyzer.setImportHintsEnabled(myFile, myImportPopupOn);
    analyzer.restart();
  }

  private boolean isModified() {
    if (myImportPopupOn != DaemonCodeAnalyzer.getInstance(myFile.getProject()).isImportHintsEnabled(myFile)) {
      return true;
    }
    for (int i = 0; i < mySliders.length; i++) {
      final PsiFile root = myFile.getPsiRoots()[i];
      if (getValue(HighlightUtil.isRootHighlighted(root), HighlightUtil.isRootInspected(root)) != mySliders[i].getValue()) {
        return true;
      }
    }
    if (myUseProfile != myUsePerFileProfile.isSelected()) return true;
    if (myUseProfile) {
      return !Comparing.equal(myProfile, DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(myFile));
    }
    return false;
  }

  private int getValue(boolean isSyntaxHighlightingEnabled, boolean isInspectionsHighlightingEnabled) {
    if (!isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled) {
      return 1;
    }
    if (isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled) {
      return 2;
    }
    return 3;
  }

  private static class ErrorsDialog extends DialogWrapper {
    private final InspectionToolsPanel myPanel;
    private ApplyAction myApplyAction;

    public ErrorsDialog(String initialProfileName, Project project) {
      super(true);
      myPanel = new InspectionToolsPanel(initialProfileName, project) {
        //just panel
      };
      setTitle(EditorBundle.message("hector.inspection.profiles.title"));
      init();
    }

    protected JComponent createCenterPanel() {
      return myPanel;
    }

    protected void doOKAction() {
      try {
        myPanel.apply();
      }
      catch (ConfigurationException e) {
      }
      super.doOKAction();
    }

    protected void dispose() {
      if (myPanel != null) myPanel.saveVisibleState();
      super.dispose();
    }

    public void doCancelAction() {
      myApplyAction.dispose();
      super.doCancelAction();
    }

    protected void doHelpAction() {
      HelpManager.getInstance().invokeHelp(InspectionResultsView.HELP_ID); //todo correct help id 
    }

    protected Action[] createActions() {
      myApplyAction = new ApplyAction(myPanel, this);
      return new Action[]{getOKAction(), myApplyAction, getCancelAction(), getHelpAction()};
    }

    @Nullable
    public String getSelectedProfile() {
      final InspectionProfile.ModifiableModel selectedProfile = myPanel.getSelectedProfile();
      return selectedProfile != null ? selectedProfile.getName() : null;
    }
  }
}
