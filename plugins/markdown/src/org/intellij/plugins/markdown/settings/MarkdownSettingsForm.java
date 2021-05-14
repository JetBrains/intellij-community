// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider;
import org.intellij.plugins.markdown.ui.split.SplitFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MarkdownSettingsForm implements MarkdownCssSettings.Holder, MarkdownPreviewSettings.Holder, Disposable {
  private JPanel myMainPanel;
  private JBCheckBox myCustomCssFromPathEnabled;
  private TextFieldWithBrowseButton myCustomCssPath;
  private JBCheckBox myApplyCustomCssText;
  private JPanel myEditorPanel;
  private JPanel myCssTitledSeparator;
  private ComboBox myPreviewProvider;
  private ComboBox myDefaultSplitLayout;
  private JPanel myPreviewTitledSeparator;
  private JBCheckBox myAutoScrollCheckBox;
  private JPanel myMultipleProvidersPreviewPanel;
  private JBRadioButton myVerticalLayout;
  private JBRadioButton myHorizontalLayout;
  private JBLabel myVerticalSplitLabel;
  private JBCheckBox myDisableInjections;
  private JBCheckBox myHideErrorsCheckbox;
  private MarkdownScriptsTable myScriptsTable;
  private JBLabel myExtensionsHelpMessage;

  @Nullable
  private EditorEx myEditor;
  @NotNull
  private final ActionListener myCustomCssPathListener;
  @NotNull
  private final ActionListener myCustomCssTextListener;

  private Object myLastItem;
  private EnumComboBoxModel<SplitFileEditor.SplitEditorLayout> mySplitLayoutModel;
  private CollectionComboBoxModel<MarkdownHtmlPanelProvider.ProviderInfo> myPreviewPanelModel;

  public MarkdownSettingsForm() {
    myCustomCssPathListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCustomCssPath.setEnabled(myCustomCssFromPathEnabled.isSelected());
      }
    };

    myCustomCssTextListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        adjustCSSRulesAvailability();
      }
    };

    adjustCSSRulesAvailability();

    myCustomCssFromPathEnabled.addActionListener(myCustomCssPathListener);
    myApplyCustomCssText.addActionListener(myCustomCssTextListener);
    myCustomCssPath.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("css")) {
      @NotNull
      @Override
      protected String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
        return chosenFile.getPath();
      }
    });

    myMultipleProvidersPreviewPanel.setVisible(isMultipleProviders());

    myDefaultSplitLayout.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        adjustAutoScroll();
        adjustSplitOption();
      }
    });

    adjustAutoScroll();
  }

  private void adjustSplitOption() {
    boolean isSplitted = myDefaultSplitLayout.getSelectedItem() == SplitFileEditor.SplitEditorLayout.SPLIT;
    myVerticalLayout.setEnabled(isSplitted);
    myHorizontalLayout.setEnabled(isSplitted);
    myVerticalSplitLabel.setEnabled(isSplitted);
  }

  private void adjustAutoScroll() {
    myAutoScrollCheckBox.setEnabled(myDefaultSplitLayout.getSelectedItem() == SplitFileEditor.SplitEditorLayout.SPLIT);
  }

  private void adjustCSSRulesAvailability() {
    if (myEditor != null) {
      boolean enabled = myApplyCustomCssText.isSelected();
      myEditor.getDocument().setReadOnly(!enabled);
      myEditor.getContentComponent().setEnabled(enabled);
      myEditor.setCaretEnabled(enabled);
    }
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myEditorPanel = new JPanel(new BorderLayout());

    myEditor = createEditor();
    myEditorPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    myCssTitledSeparator = new TitledSeparator(MarkdownBundle.message("markdown.settings.css.title.name"));

    myExtensionsHelpMessage = ContextHelpLabel.create(
      MarkdownBundle.message("markdown.settings.download.extension.download.notice")
    );

    myScriptsTable = new MarkdownScriptsTable();

    createPreviewUIComponents();
  }

  private static boolean isMultipleProviders() {
    return MarkdownHtmlPanelProvider.getProviders().length > 1;
  }

  public void validate() throws ConfigurationException {
    if (!myCustomCssFromPathEnabled.isSelected()) return;
    if (!new File(myCustomCssPath.getText()).exists()) {
      throw new ConfigurationException(MarkdownBundle.message("dialog.message.path.error", myCustomCssPath.getText()));
    }
  }

  @NotNull
  private static EditorEx createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument("");
    EditorEx editor = (EditorEx)editorFactory.createEditor(editorDocument);
    fillEditorSettings(editor.getSettings());
    setHighlighting(editor);
    return editor;
  }

  private static void setHighlighting(EditorEx editor) {
    final FileType cssFileType = FileTypeManager.getInstance().getFileTypeByExtension("css");
    if (cssFileType == UnknownFileType.INSTANCE) {
      return;
    }
    final EditorHighlighter editorHighlighter =
      HighlighterFactory.createHighlighter(cssFileType, EditorColorsManager.getInstance().getGlobalScheme(), null);
    editor.setHighlighter(editorHighlighter);
  }

  private static void fillEditorSettings(final EditorSettings editorSettings) {
    editorSettings.setWhitespacesShown(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(true);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(1);
    editorSettings.setAdditionalLinesCount(1);
    editorSettings.setUseSoftWraps(false);
  }

  @Override
  public void setMarkdownCssSettings(@NotNull MarkdownCssSettings settings) {
    myCustomCssFromPathEnabled.setSelected(settings.isCustomStylesheetEnabled());
    myCustomCssPath.setText(settings.getCustomStylesheetPath());
    myApplyCustomCssText.setSelected(settings.isTextEnabled());
    resetEditor(settings.getCustomStylesheetText());

    myCustomCssPathListener.actionPerformed(null);
    myCustomCssTextListener.actionPerformed(null);
  }

  void resetEditor(@NotNull String cssText) {
    if (myEditor != null && !myEditor.isDisposed()) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        boolean writable = myEditor.getDocument().isWritable();
        myEditor.getDocument().setReadOnly(false);
        myEditor.getDocument().setText(cssText);
        myEditor.getDocument().setReadOnly(!writable);
      });
    }
  }

  @NotNull
  @Override
  public MarkdownCssSettings getMarkdownCssSettings() {
    String customCssText = myEditor != null && !myEditor.isDisposed() ? ReadAction.compute(() -> myEditor.getDocument().getText()) : "";
    //font change available only from the preview
    Integer fontSize = MarkdownApplicationSettings.getInstance().getMarkdownCssSettings().getFontSize();
    String fontFamily = MarkdownApplicationSettings.getInstance().getMarkdownCssSettings().getFontFamily();

    return new MarkdownCssSettings(myCustomCssFromPathEnabled.isSelected(),
                                   myCustomCssPath.getText(),
                                   myApplyCustomCssText.isSelected(),
                                   customCssText,
                                   fontSize,
                                   fontFamily);
  }

  @NotNull
  public Map<String, Boolean> getExtensionsEnabledState() {
    return new HashMap<>(myScriptsTable.getState());
  }

  public void setExtensionsEnabledState(@NotNull Map<String, Boolean> state) {
    myScriptsTable.setState(state, (MarkdownHtmlPanelProvider.ProviderInfo)myPreviewProvider.getSelectedItem());
  }

  @Override
  public void dispose() {
    if (myEditor != null && !myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
    myEditor = null;
  }

  private void createPreviewUIComponents() {
    myPreviewTitledSeparator = new TitledSeparator(MarkdownBundle.message("markdown.settings.preview.name"));
    mySplitLayoutModel = new EnumComboBoxModel<>(SplitFileEditor.SplitEditorLayout.class);
    myDefaultSplitLayout = new ComboBox<>(mySplitLayoutModel);
    myDefaultSplitLayout.setRenderer(SimpleListCellRenderer.create("", SplitFileEditor.SplitEditorLayout::getPresentationText));

    createMultipleProvidersSettings();
  }

  private void createMultipleProvidersSettings() {
    final List<MarkdownHtmlPanelProvider.ProviderInfo> providerInfos =
      ContainerUtil.mapNotNull(MarkdownHtmlPanelProvider.getProviders(),
                               provider -> {
                                 if (provider.isAvailable() == MarkdownHtmlPanelProvider.AvailabilityInfo.UNAVAILABLE) {
                                   return null;
                                 }
                                 return provider.getProviderInfo();
                               });
    myPreviewPanelModel = new CollectionComboBoxModel<>(providerInfos, providerInfos.get(0));
    myPreviewProvider = new ComboBox<>(myPreviewPanelModel);

    myLastItem = myPreviewProvider.getSelectedItem();
    myPreviewProvider.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        final Object item = e.getItem();
        if (e.getStateChange() != ItemEvent.SELECTED || !(item instanceof MarkdownHtmlPanelProvider.ProviderInfo)) {
          return;
        }

        final MarkdownHtmlPanelProvider provider = MarkdownHtmlPanelProvider.createFromInfo((MarkdownHtmlPanelProvider.ProviderInfo)item);
        final MarkdownHtmlPanelProvider.AvailabilityInfo availability = provider.isAvailable();

        if (!availability.checkAvailability(myMainPanel)) {
          myPreviewProvider.setSelectedItem(myLastItem);
        }
        else {
          myLastItem = item;
          myScriptsTable.setState(MarkdownApplicationSettings.getInstance().getExtensionsEnabledState(), provider.getProviderInfo());
        }
      }
    });
    myScriptsTable.setState(
      MarkdownApplicationSettings.getInstance().getExtensionsEnabledState(),
      (MarkdownHtmlPanelProvider.ProviderInfo)myPreviewProvider.getSelectedItem()
    );
  }

  @NotNull
  private static MarkdownHtmlPanelProvider getDefaultProvider() {
    MarkdownHtmlPanelProvider[] providers = MarkdownHtmlPanelProvider.getProviders();
    if (providers.length > 0) return providers[0];
    throw new RuntimeException("No providers are defined");
  }

  @NotNull
  private MarkdownHtmlPanelProvider.ProviderInfo getSelectedProvider() {
    if (isMultipleProviders()) {
      return Objects.requireNonNull(myPreviewPanelModel.getSelected());
    }
    else {
      return getDefaultProvider().getProviderInfo();
    }
  }

  @Override
  public void setMarkdownPreviewSettings(@NotNull MarkdownPreviewSettings settings) {
    if (isMultipleProviders() && myPreviewPanelModel.contains(settings.getHtmlPanelProviderInfo())) {
      myPreviewPanelModel.setSelectedItem(settings.getHtmlPanelProviderInfo());
    }

    mySplitLayoutModel.setSelectedItem(settings.getSplitEditorLayout());
    myAutoScrollCheckBox.setSelected(settings.isAutoScrollPreview());
    myVerticalLayout.setSelected(settings.isVerticalSplit());
    myHorizontalLayout.setSelected(!settings.isVerticalSplit());
  }

  @NotNull
  @Override
  public MarkdownPreviewSettings getMarkdownPreviewSettings() {
    MarkdownHtmlPanelProvider.ProviderInfo provider = getSelectedProvider();

    Objects.requireNonNull(provider);
    return new MarkdownPreviewSettings(mySplitLayoutModel.getSelectedItem(),
                                       provider,
                                       myAutoScrollCheckBox.isSelected(),
                                       myVerticalLayout.isSelected());
  }

  public void setDisableInjections(boolean disableInjections) {
    myDisableInjections.setSelected(disableInjections);
  }

  public boolean isDisableInjections() {
    return myDisableInjections.isSelected();
  }

  public void setHideErrors(boolean hideErrors) {
    myHideErrorsCheckbox.setSelected(hideErrors);
  }

  public boolean isHideErrors() {
    return myHideErrorsCheckbox.isSelected();
  }
}
