// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.usages.*;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.CheckBox;
import com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention;
import com.jetbrains.python.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.inspections.quickfix.PyImplementMethodsQuickFix;
import com.jetbrains.python.inspections.quickfix.PyRenameElementQuickFix;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.ui.PyUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public final class PythonUiServiceImpl extends PythonUiService {
  @Override
  public void showBalloonInfo(Project project, @PopupContent String message) {
    PyUiUtil.showBalloon(project, message, MessageType.INFO);
  }

  @Override
  public void showBalloonWarning(Project project, @PopupContent String message) {
    PyUiUtil.showBalloon(project, message, MessageType.WARNING);
  }


  @Override
  public void showBalloonError(Project project, @PopupContent String message) {
    PyUiUtil.showBalloon(project, message, MessageType.ERROR);
  }

  @Override
  public FileEditor getSelectedEditor(@NotNull Project project, VirtualFile virtualFile) {
    return FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
  }

  @Override
  public Editor openTextEditor(@NotNull Project project, PsiElement anchor) {
    PsiFile file = InjectedLanguageManager.getInstance(project).getTopLevelFile(anchor);
    return openTextEditor(project, file.getVirtualFile());
  }

  @Override
  public Editor openTextEditor(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return FileEditorManager.getInstance(project).openTextEditor(
      new OpenFileDescriptor(project, virtualFile), true);
  }

  @Override
  public Editor openTextEditor(@NotNull Project project, @NotNull VirtualFile virtualFile, int offset) {
    return FileEditorManager.getInstance(project).openTextEditor(
      new OpenFileDescriptor(project, virtualFile, offset), true);
  }

  @Override
  public boolean showYesDialog(Project project, String message, String title) {
    return MessageDialogBuilder.yesNo(title, message).ask(project);
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
    chooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<>() {
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
  public JComponent createSingleCheckboxOptionsPanel(@NlsContexts.Checkbox String label, InspectionProfileEntry inspection, String property) {
    return new SingleCheckboxOptionsPanel(label, inspection, property);
  }

  @Override
  public void annotateTypesIntention(Editor editor, PyFunction function) {
    PyAnnotateTypesIntention.annotateTypes(editor, function);
  }

  @Override
  @NotNull
  public JComponent createEncodingsOptionsPanel(String @ListItem [] possibleEncodings,
                                                @ListItem String defaultEncoding,
                                                String @ListItem [] possibleFormats,
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
  public JCheckBox createInspectionCheckBox(@NlsContexts.Checkbox String message, InspectionProfileEntry inspection, String property) {
    return new CheckBox(message, inspection, property);
  }

  @Override
  public <E> JComboBox<E> createComboBox(E[] items) {
    return new ComboBox<>(items);
  }

  @Override
  public <E> JComboBox<E> createComboBox(E[] items, int width) {
    return new ComboBox<>(items, width);
  }

  @Override
  public JComponent createListEditForm(@NlsContexts.ColumnName String title, List<String> stringList) {
    final ListEditForm form = new ListEditForm(title, stringList);
    return form.getContentPanel();
  }

  @Override
  @NotNull
  public JComponent createComboBoxWithLabel(@NotNull @NlsContexts.Label String label,
                                            String @ListItem [] items,
                                            @ListItem String selectedItem,
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

  @Override
  public void showPopup(Project project, List<String> items, String title, Consumer<String> callback) {
    DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext ->
      JBPopupFactory.getInstance().createPopupChooserBuilder(items)
        .setTitle(title)
        .setItemChosenCallback(callback)
        .setNamerForFiltering(o -> o)
        .createPopup()
        .showInBestPositionFor(dataContext));
  }

  /**
   * Shows a panel with name redefinition conflicts, if needed.
   *
   * @param project
   * @param conflicts what {@link #findDefinitions} would return
   * @param obscured  name or its topmost qualifier that is obscured, used at top of pane.
   * @param name      full name (maybe qualified) to show as obscured and display as qualifier in "would be" chunks.
   * @return true iff conflicts is not empty and the panel is shown.
   */
  @Override
  public boolean showConflicts(Project project,
                               List<? extends Pair<PsiElement, PsiElement>> conflicts,
                               String obscured,
                               @Nullable String name) {
    if (conflicts.size() > 0) {
      Usage[] usages = new Usage[conflicts.size()];
      int i = 0;
      for (Pair<PsiElement, PsiElement> pair : conflicts) {
        usages[i] = new NameUsage(pair.getFirst(), pair.getSecond(), name != null ? name : obscured, name != null);
        i += 1;
      }
      UsageViewPresentation prsnt = new UsageViewPresentation();
      prsnt.setTabText(PyBundle.message("CONFLICT.name.obscured.by.local.definitions", obscured));
      prsnt.setCodeUsagesString(PyBundle.message("CONFLICT.name.obscured.cannot.convert", obscured));
      prsnt.setUsagesString(PyBundle.message("CONFLICT.occurrence.pl"));
      UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, usages, prsnt);
      return true;
    }
    return false;
  }

  /**
   * Simplistic usage object for demonstration of name clashes, etc.
   * User: dcheryasov
   */
  public static class NameUsage implements PsiElementUsage {

    private final PsiElement myElement;
    private final PsiElement myCulprit;

    private static final TextAttributes SLANTED;
    private final String myName;
    private final boolean myIsPrefix;

    static {
      SLANTED = TextAttributes.ERASE_MARKER.clone();
      SLANTED.setFontType(Font.ITALIC);
    }

    /**
     * Creates a conflict search panel usage.
     *
     * @param element where conflict happens.
     * @param culprit where name is redefined; usages with the same culprit are grouped.
     * @param name    redefinition of it is what the conflict is about.
     * @param prefix  if true, show name as a prefix to element's name in "would be" part.
     */
    public NameUsage(PsiElement element, PsiElement culprit, String name, boolean prefix) {
      myElement = element;
      myCulprit = culprit;
      myName = name;
      myIsPrefix = prefix;
    }

    @Override
    public FileEditorLocation getLocation() {
      return null;
    }

    @Override
    @NotNull
    public UsagePresentation getPresentation() {
      return new UsagePresentation() {
        @Override
        @Nullable
        public Icon getIcon() {
          PyPsiUtils.assertValid(myElement);
          return myElement.isValid() ? myElement.getIcon(0) : null;
        }

        @Override
        public TextChunk @NotNull [] getText() {
          PyPsiUtils.assertValid(myElement);
          if (myElement.isValid()) {
            PsiFile file = myElement.getContainingFile();
            String line_id = "...";
            final Document document = file.getViewProvider().getDocument();
            if (document != null) {
              line_id = String.valueOf(document.getLineNumber(myElement.getTextOffset()));
            }
            TextChunk[] chunks = new TextChunk[3];
            chunks[0] = new TextChunk(SLANTED, "(" + line_id + ") ");
            chunks[1] = new TextChunk(TextAttributes.ERASE_MARKER, myElement.getText());
            StringBuilder sb = new StringBuilder(" would become ").append(myName);
            if (myIsPrefix) sb.append(".").append(myElement.getText());
            chunks[2] = new TextChunk(SLANTED, sb.toString());
            return chunks;
          }
          else {
            return new TextChunk[]{new TextChunk(SLANTED, "?")};
          }
        }

        @Override
        @NotNull
        public String getPlainText() {
          return myElement.getText();
        }

        @Override
        public String getTooltipText() {
          return myElement.getText();
        }
      };
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public boolean isReadOnly() {
      return false;
    }

    @Override
    public void selectInEditor() { }

    @Override
    public void highlightInEditor() { }

    @Override
    public void navigate(boolean requestFocus) {
      Navigatable descr = EditSourceUtil.getDescriptor(myElement);
      if (descr != null) descr.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return EditSourceUtil.canNavigate(myElement);
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public PsiElement getElement() {
      return myCulprit;
    }

    @Override
    public boolean isNonCodeUsage() {
      return false;
    }
  }

  @Override
  public @Nullable String showInputDialog(@Nullable Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          @Nullable String initialValue,
                                          @Nullable InputValidator validator) {
    return Messages.showInputDialog(project, message,
                                    title, Messages.getQuestionIcon(), "", validator);
  }

  @Override
  public void showErrorHint(Editor editor, @NotNull @HintText String message) {
    HintManager.getInstance().showErrorHint(editor, message);
  }

  @Override
  public int showChooseDialog(@Nullable Project project,
                              @Nullable Component parentComponent,
                              @DialogMessage String message,
                              @DialogTitle String title,
                              String @ListItem [] values,
                              @ListItem String initialValue,
                              @Nullable Icon icon) {
    return MessagesService.getInstance().showChooseDialog(project, parentComponent, message, title, values, initialValue, icon);
  }

  @Override
  public JPanel createMultipleCheckboxOptionsPanel(final InspectionProfileEntry owner) {
    return new MultipleCheckboxOptionsPanel(owner);
  }

  @Override
  public void addRowToOptionsPanel(JPanel optionsPanel, JComponent label, JComponent component) {
    if (optionsPanel instanceof InspectionOptionsPanel) {
      ((InspectionOptionsPanel) optionsPanel).addRow(label, component);
    }
  }

  @Override
  public void addCheckboxToOptionsPanel(JPanel optionsPanel, String label, String property) {
    if (optionsPanel instanceof MultipleCheckboxOptionsPanel) {
      ((MultipleCheckboxOptionsPanel) optionsPanel).addCheckbox(label, property);
    }
  }
}
