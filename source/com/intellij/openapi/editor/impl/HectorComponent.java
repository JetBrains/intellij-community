package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.jsp.JspFile;
import com.intellij.ui.Hint;
import com.intellij.ui.HintListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.Hashtable;

/**
 * User: anna
 * Date: Jun 27, 2005
 */
public class HectorComponent extends JPanel {
  private JCheckBox myImportPopupCheckBox = new JCheckBox(EditorBundle.message("hector.import.popup.checkbox"));
  private JSlider[] mySliders;
  private PsiFile myFile;

  private boolean myImportPopupOn;

  private final String myTitle = EditorBundle.message("hector.highlighting.level.title");

  public HectorComponent(PsiFile file) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEtchedBorder());
    myFile = file;
    mySliders = new JSlider[PsiUtil.isInJspFile(file) ? file.getPsiRoots().length - 1 : 1];

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
      final boolean value = true;
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

  private void layoutHorizontal(final JPanel panel) {
    for (JSlider slider : mySliders) {
      slider.setOrientation(JSlider.HORIZONTAL);
      slider.setPreferredSize(new Dimension(200, 40));
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
    final LightweightHint hint = new LightweightHint(this);
    hint.addHintListener(new HintListener() {
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
      hintManager.showEditorHint(hint, editor, point, HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_SCROLLING, 0, true);
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
    return false;
  }

  private static int getValue(boolean isSyntaxHighlightingEnabled, boolean isInspectionsHighlightingEnabled) {
    if (!isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled) {
      return 1;
    }
    if (isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled) {
      return 2;
    }
    return 3;
  }
}
