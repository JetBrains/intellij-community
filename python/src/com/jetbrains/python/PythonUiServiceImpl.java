// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.CheckBox;
import com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention;
import com.jetbrains.python.inspections.PyMandatoryEncodingInspection;
import com.jetbrains.python.inspections.PyPep8NamingInspection;
import com.jetbrains.python.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.inspections.quickfix.PyImplementMethodsQuickFix;
import com.jetbrains.python.inspections.quickfix.PyRenameElementQuickFix;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.ui.PyUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class PythonUiServiceImpl extends PythonUiService {
  @Override
  public void showBalloonInfo(Project project, String message) {
    PyUiUtil.showBalloon(project, message, MessageType.INFO);
  }

  @Override
  public void showBalloonError(Project project, String message) {
    PyUiUtil.showBalloon(project, message, MessageType.ERROR);
  }

  @Override
  public Editor openTextEditor(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return FileEditorManager.getInstance(project).openTextEditor(
      new OpenFileDescriptor(project, virtualFile), true);
  }

  @Override
  public boolean showYesDialog(Project project, String message, String title) {
    return Messages.showYesNoDialog(message, title, Messages.getQuestionIcon()) == Messages.YES;
  }

  @Override
  public @Nullable LocalQuickFix createPyRenameElementQuickFix(@NotNull PsiElement element) {
    return new PyRenameElementQuickFix(element);
  }

  //TODO: rewrite in dsl
  @Override
  public JComponent createCompatibilityInspectionOptionsPanel(@NotNull List<String> supportedInSettings,
                                                              JDOMExternalizableStringList ourVersions) {
    final ElementsChooser<String> chooser = new ElementsChooser<>(true);
    chooser.setElements(supportedInSettings, false);
    chooser.markElements(ContainerUtil.filter(ourVersions, supportedInSettings::contains));
    chooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<String>() {
      @Override
      public void elementMarkChanged(String element, boolean isMarked) {
        ourVersions.clear();
        ourVersions.addAll(chooser.getMarkedElements());
      }
    });
    final JPanel versionPanel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(PyPsiBundle.message("INSP.compatibility.check.for.compatibility.with.python.versions"));
    label.setLabelFor(chooser);
    versionPanel.add(label, BorderLayout.PAGE_START);
    versionPanel.add(chooser);
    return versionPanel;
  }

  //TODO: find a better place or, even better, port it to analysis module
  @Override
  public void runRenameProcessor(Project project,
                                 PsiElement element,
                                 String newName,
                                 boolean searchInComments,
                                 boolean searchTextOccurrences) {
    new RenameProcessor(project, element, newName, searchInComments, searchTextOccurrences).run();
  }

  @Override
  public LocalQuickFix createPyChangeSignatureQuickFixForMismatchingMethods(PyFunction function, PyFunction method) {
    return PyChangeSignatureQuickFix.forMismatchingMethods(function, method);
  }

  @Override
  public LocalQuickFix createPyChangeSignatureQuickFixForMismatchedCall(PyCallExpression.@NotNull PyArgumentsMapping mapping) {
    return PyChangeSignatureQuickFix.forMismatchedCall(mapping);
  }

  @Override
  public LocalQuickFix createPyImplementMethodsQuickFix(PyClass aClass, List<PyFunction> toImplement) {
    return new PyImplementMethodsQuickFix(aClass, toImplement);
  }

  @Override
  public JComponent createSingleCheckboxOptionsPanel(String label, InspectionProfileEntry inspection, String property) {
    return new SingleCheckboxOptionsPanel(label, inspection, property);
  }

  @Override
  public void annotateTypesIntention(Editor editor, PyFunction function) {
    PyAnnotateTypesIntention.annotateTypes(editor, function);
  }

  @Override
  @NotNull
  public JComponent createEncodingsOptionsPanel(String[] possibleEncodings,
                                                   final String defaultEncoding,
                                                   String[] possibleFormats,
                                                   final int formatIndex,
                                                   Consumer<String> encodingChanged,
                                                   Consumer<Integer> formatIndexChanged) {
    final JComboBox defaultEncodingCombo = new ComboBox(possibleEncodings);
    defaultEncodingCombo.setSelectedItem(defaultEncoding);

    defaultEncodingCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        encodingChanged.consume((String)cb.getSelectedItem());
      }
    });

    final ComboBox encodingFormatCombo = new ComboBox(possibleFormats);

    encodingFormatCombo.setSelectedIndex(formatIndex);
    encodingFormatCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        formatIndexChanged.consume(cb.getSelectedIndex());
      }
    });

    return createEncodingOptionsPanel(defaultEncodingCombo, encodingFormatCombo);
  }

  public static JComponent createEncodingOptionsPanel(JComboBox defaultEncoding, JComboBox encodingFormat) {
    final JPanel optionsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();

    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTH;
    c.gridx = 0;
    c.gridy = 0;
    final JLabel encodingLabel = new JLabel(PyBundle.message("code.insight.select.default.encoding"));
    final JPanel encodingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    encodingPanel.add(encodingLabel);
    optionsPanel.add(encodingPanel, c);

    c.gridx = 1;
    c.gridy = 0;
    optionsPanel.add(defaultEncoding, c);

    c.gridx = 0;
    c.gridy = 1;
    c.weighty = 1;
    final JLabel formatLabel = new JLabel(PyBundle.message("code.insight.encoding.comment.format"));
    final JPanel formatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    formatPanel.add(formatLabel);
    optionsPanel.add(formatPanel, c);

    c.gridx = 1;
    c.gridy = 1;
    optionsPanel.add(encodingFormat, c);

    return optionsPanel;
  }

  @Override
  public JCheckBox createInspectionCheckBox(String message, InspectionProfileEntry inspection, String property) {
    return new CheckBox(message, inspection, property);
  }

  @Override
  public <E> JComboBox<E> createComboBox(E[] items) {
    return new ComboBox<E>(items);
  }

  @Override
  public <E> JComboBox<E> createComboBox(E[] items, int width) {
    return new ComboBox<E>(items, width);
  }

  @Override
  public JComponent createListEditForm(String title, List<String> stringList) {
    final ListEditForm form = new ListEditForm(title, stringList);
    return form.getContentPanel();
  }

  @NotNull
  public JComponent createComboBoxWithLabel(@NotNull String label,
                                             String[] items,
                                             final String selectedItem,
                                             Consumer<Object> selectedItemChanged) {
    ComboBox comboBox = new ComboBox<>(items);
    comboBox.setSelectedItem(selectedItem);
    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        selectedItemChanged.consume(cb.getSelectedItem());
      }
    });

    JPanel option = new JPanel(new BorderLayout());
    option.add(new JLabel(label), BorderLayout.WEST);
    option.add(comboBox, BorderLayout.EAST);

    final JPanel root = new JPanel(new BorderLayout());
    root.add(option, BorderLayout.PAGE_START);
    return root;
  }

  @Override
  public JComponent onePixelSplitter(boolean vertical, JComponent first, JComponent second) {
    final OnePixelSplitter splitter = new OnePixelSplitter(vertical);

    splitter.setFirstComponent(first);
    splitter.setSecondComponent(second);

    return splitter;
  }

  public void showPopup(Project project, List<String> items, String title, Consumer<String> callback) {
    DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext ->
      JBPopupFactory.getInstance().createPopupChooserBuilder(items)
        .setTitle(title)
        .setItemChosenCallback(callback)
        .setNamerForFiltering(o -> o)
        .createPopup()
        .showInBestPositionFor(dataContext));
  }
}
