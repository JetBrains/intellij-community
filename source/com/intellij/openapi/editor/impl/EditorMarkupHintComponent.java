package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.ui.Hint;
import com.intellij.ui.HintListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightweightHint;

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
public class EditorMarkupHintComponent extends JPanel {
  private JCheckBox myImportPopupCheckBox = new JCheckBox("Import Popup");
  private JRadioButton myGoByErrorsRadioButton = new JRadioButton("Go to errors first");
  private JRadioButton myGoByBothRadioButton = new JRadioButton("Go to next error/warning");

  private JSlider[] mySliders;
  private PsiFile myFile;

  private boolean myImportPopupOn;
  private boolean myGoByErrors;


  public EditorMarkupHintComponent(PsiFile file) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEtchedBorder());
    myFile = file;
    mySliders = new JSlider[file instanceof JspFile ? file.getPsiRoots().length - 1 : 1];
    for (int i = 0; i < mySliders.length; i++) {

      final Hashtable<Integer, JLabel> sliderLabels = new Hashtable<Integer, JLabel>();
      sliderLabels.put(new Integer(1), new JLabel("None"));
      sliderLabels.put(new Integer(2), new JLabel("Syntax"));
      sliderLabels.put(new Integer(3), new JLabel("Inspections"));

      final JSlider slider = new JSlider(JSlider.VERTICAL, 1, 3, 3);
      slider.setLabelTable(sliderLabels);
      slider.putClientProperty("JSlider.isFilled", Boolean.TRUE);
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

    ButtonGroup group = new ButtonGroup();
    group.add(myGoByErrorsRadioButton);
    group.add(myGoByBothRadioButton);

    myGoByErrors = DaemonCodeAnalyzerSettings.getInstance().NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST;
    final ChangeListener changeListener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myGoByErrors = myGoByErrorsRadioButton.isSelected();
      }
    };
    myGoByErrorsRadioButton.addChangeListener(changeListener);
    myGoByBothRadioButton.addChangeListener(changeListener);
    myGoByErrorsRadioButton.setSelected(myGoByErrors);
    myGoByBothRadioButton.setSelected(!myGoByErrors);

    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1,1, 0,0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0);
    add(myImportPopupCheckBox, gc);

    JPanel navPanel = new JPanel(new BorderLayout());
    navPanel.add(myGoByErrorsRadioButton, BorderLayout.WEST);
    navPanel.add(myGoByBothRadioButton, BorderLayout.EAST);
    navPanel.setBorder(IdeBorderFactory.createTitledBorder("Errors Navigation"));
    add(navPanel, gc);

    JPanel panel = new JPanel(new GridLayout(1, mySliders.length));
    panel.setBorder(IdeBorderFactory.createTitledBorder("Highlighting Level"));
    final boolean addLabel = mySliders.length > 1;
    if (addLabel){
      layoutVertical(panel);
    } else {
      layoutHorizontal(panel);
    }
    gc.gridx = 0;
    gc.gridy = 2;
    gc.gridwidth = 2;
    gc.weightx = 1.0;
    gc.weighty = 1.0;
    gc.fill = GridBagConstraints.BOTH;
    add(panel, gc);
  }

  private void layoutHorizontal(final JPanel panel){
    for (JSlider slider : mySliders) {
      slider.setOrientation(JSlider.HORIZONTAL);
      slider.setPreferredSize(new Dimension(100, 40));
      panel.add(slider);
    }
  }

  private void layoutVertical(final JPanel panel){
    for (int i = 0; i < mySliders.length; i++) {
      JPanel borderPanel = new JPanel(new BorderLayout());
      mySliders[i].setPreferredSize(new Dimension(80, 100));
      borderPanel.add(new JLabel(myFile.getPsiRoots()[i].getLanguage().getID()), BorderLayout.NORTH);
      borderPanel.add(mySliders[i], BorderLayout.CENTER);
      panel.add(borderPanel);
    }
  }

  public void showComponent(Editor editor, Point point){
    final LightweightHint hint = new LightweightHint(this);
    hint.addHintListener(new HintListener() {
      public void hintHidden(EventObject event) {
        onClose();
      }
    });
    final HintManager hintManager = HintManager.getInstance();
    final Hint previousHint = hintManager.findHintByType(EditorMarkupHintComponent.class);
    if (previousHint != null){
      previousHint.hide();
    }
    hintManager.showEditorHint(hint, editor, point, HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_SCROLLING, 0, true);
  }

  private void onClose(){
    if (isModified()){
      forceDaemonRestart();
      ((StatusBarEx) WindowManager.getInstance().getStatusBar(myFile.getProject())).updateEditorHighlightingStatus(false);
    }
  }

  private void forceDaemonRestart(){
    for (int i = 0; i < mySliders.length; i++) {
      PsiElement root = myFile.getPsiRoots()[i];
      int value = mySliders[i].getValue();
      if (value == 1){
        HighlightUtil.forceRootHighlighting(root, false);
      } else if (value == 2){
        HighlightUtil.forceRootInspection(root, false);
      } else {
        HighlightUtil.forceRootInspection(root, true);
      }
    }
    final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(myFile.getProject());
    analyzer.setImportHintsEnabled(myFile, myImportPopupOn);
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = myGoByErrors;
    analyzer.restart();
  }

  private boolean isModified(){
    if (myImportPopupOn != DaemonCodeAnalyzer.getInstance(myFile.getProject()).isImportHintsEnabled(myFile)){
      return true;
    }
    if (myGoByErrors != DaemonCodeAnalyzerSettings.getInstance().NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST){
      return true;
    }
    for (int i = 0; i < mySliders.length; i++) {
      final PsiFile root = myFile.getPsiRoots()[i];
      if (getValue(HighlightUtil.isRootHighlighted(root), HighlightUtil.isRootInspected(root)) != mySliders[i].getValue()){
        return true;
      }
    }
    return false;
  }

  private int getValue(boolean isSyntaxHighlightingEnabled, boolean isInspectionsHighlightingEnabled){
    if (!isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled){
      return 1;
    }
    if (isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled){
      return 2;
    }
    return 3;
  }
}
