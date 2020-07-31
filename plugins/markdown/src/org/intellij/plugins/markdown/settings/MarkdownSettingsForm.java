package org.intellij.plugins.markdown.settings;

import com.intellij.icons.AllIcons;
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
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.ui.UIUtil;
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
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MarkdownSettingsForm implements MarkdownCssSettings.Holder, MarkdownPreviewSettings.Holder, Disposable {
  private static final String JAVA_FX_HTML_PANEL_PROVIDER = "JavaFxHtmlPanelProvider";
  private JPanel myMainPanel;
  private JBCheckBox myCssFromURIEnabled;
  private TextFieldWithBrowseButton myCssURI;
  private JBCheckBox myApplyCustomCssText;
  private JPanel myEditorPanel;
  private JPanel myCssTitledSeparator;
  private ComboBox myPreviewProvider;
  private ComboBox myDefaultSplitLayout;
  private JBCheckBox myUseGrayscaleRenderingForJBCheckBox;
  private JPanel myPreviewTitledSeparator;
  private JBCheckBox myAutoScrollCheckBox;
  private JPanel myMultipleProvidersPreviewPanel;
  private LinkLabel myPlantUMLDownload;
  private JBLabel myPlantUMLStatusLabel;
  private JBRadioButton myVerticalLayout;
  private JBRadioButton myHorizontalLayout;
  private JBLabel myVerticalSplitLabel;
  private JBCheckBox myDisableInjections;
  private JBCheckBox myHideErrorsCheckbox;
  private JBLabel myPlantUMLLocationHelp;

  private static final Color SUCCESS_COLOR = new JBColor(0x008000, 0x6A8759);

  @Nullable
  private EditorEx myEditor;
  @NotNull
  private final ActionListener myCssURIListener;
  @NotNull
  private final ActionListener myCustomCssTextListener;

  private Object myLastItem;
  private EnumComboBoxModel<SplitFileEditor.SplitEditorLayout> mySplitLayoutModel;
  private CollectionComboBoxModel<MarkdownHtmlPanelProvider.ProviderInfo> myPreviewPanelModel;

  public MarkdownSettingsForm() {
    myCssURIListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCssURI.setEnabled(myCssFromURIEnabled.isSelected());
      }
    };

    myCustomCssTextListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        adjustCSSRulesAvailability();
      }
    };

    adjustCSSRulesAvailability();

    myCssFromURIEnabled.addActionListener(myCssURIListener);
    myApplyCustomCssText.addActionListener(myCustomCssTextListener);
    myCssURI.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("css")) {
      @NotNull
      @Override
      protected String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
        return chosenFile.getUrl();
      }
    });

    myMultipleProvidersPreviewPanel.setVisible(isMultipleProviders());
    updateUseGrayscaleEnabled();

    myDefaultSplitLayout.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        adjustAutoScroll();
        adjustSplitOption();
      }
    });

    adjustAutoScroll();

    updatePlantUMLLabel(false);
    myPlantUMLStatusLabel.setFontColor(UIUtil.FontColor.BRIGHTER);

    myPlantUMLDownload.setListener((source, data) -> {
      DownloadableFileService downloader = DownloadableFileService.getInstance();
      DownloadableFileDescription description =
        downloader.createFileDescription(MarkdownSettingsConfigurable.PLANTUML_JAR_URL, MarkdownSettingsConfigurable.PLANTUML_JAR);

      downloader.createDownloader(Collections.singletonList(description), MarkdownSettingsConfigurable.PLANT_UML_DIRECTORY + ".jar")
        .downloadFilesWithProgress(MarkdownSettingsConfigurable.getDirectoryToDownload().getAbsolutePath(), null, myMainPanel);

      updatePlantUMLLabel(true);
    }, null);
  }

  private void adjustSplitOption() {
    boolean isSplitted = myDefaultSplitLayout.getSelectedItem() == SplitFileEditor.SplitEditorLayout.SPLIT;
    myVerticalLayout.setEnabled(isSplitted);
    myHorizontalLayout.setEnabled(isSplitted);
    myVerticalSplitLabel.setEnabled(isSplitted);
  }

  public void updatePlantUMLLabel(boolean isJustInstalled) {
    myPlantUMLStatusLabel.setForeground(JBColor.foreground());
    myPlantUMLStatusLabel.setIcon(null);

    if (MarkdownSettingsConfigurable.isPlantUMLAvailable()) {
      if (isJustInstalled) {
        myPlantUMLStatusLabel.setForeground(SUCCESS_COLOR);
        myPlantUMLStatusLabel.setText(MarkdownBundle.message("markdown.settings.preview.plantUML.download.success"));
      }
      else {
        myPlantUMLStatusLabel.setText(MarkdownBundle.message("markdown.settings.preview.plantUML.installed"));
      }
      myPlantUMLDownload.setVisible(false);
    }
    else {
      if (isJustInstalled) {
        myPlantUMLStatusLabel.setForeground(JBColor.RED);
        myPlantUMLStatusLabel.setIcon(AllIcons.General.Warning);
        myPlantUMLStatusLabel.setText(MarkdownBundle.message("markdown.settings.preview.plantUML.download.failed"));
        myPlantUMLDownload.setText(MarkdownBundle.message("markdown.settings.preview.plantUML.download.retry"));
      }
      else {
        myPlantUMLStatusLabel.setText(MarkdownBundle.message("markdown.settings.preview.plantUML.download.isnt.installed"));
        myPlantUMLDownload.setText(MarkdownBundle.message("markdown.settings.preview.plantUML.download"));
      }
      myPlantUMLDownload.setVisible(true);
    }
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

    myPlantUMLLocationHelp = ContextHelpLabel.create(
      MarkdownBundle.message(
        "markdown.settings.preview.plantUML.jar.location.description",
        MarkdownSettingsConfigurable.getExpectedJarPath().getAbsolutePath()
      )
    );

    createPreviewUIComponents();
  }

  private static boolean isMultipleProviders() {
    return MarkdownHtmlPanelProvider.getProviders().length > 1;
  }

  public void validate() throws ConfigurationException {
    if (!myCssFromURIEnabled.isSelected()) return;

    try {
      new URL(myCssURI.getText()).toURI();
    }
    catch (URISyntaxException | MalformedURLException e) {
      throw new ConfigurationException(
        MarkdownBundle.message("dialog.message.uri.parsing.reports.error", myCssURI.getText(), e.getMessage()));
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
    myCssFromURIEnabled.setSelected(settings.isUriEnabled());
    myCssURI.setText(settings.getStylesheetUri());
    myApplyCustomCssText.setSelected(settings.isTextEnabled());
    resetEditor(settings.getStylesheetText());

    myCssURIListener.actionPerformed(null);
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
    return new MarkdownCssSettings(myCssFromURIEnabled.isSelected(),
                                   myCssURI.getText(),
                                   myApplyCustomCssText.isSelected(),
                                   myEditor != null && !myEditor.isDisposed() ?
                                   ReadAction.compute(() -> myEditor.getDocument().getText()) : "");
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
          updateUseGrayscaleEnabled();
        }
      }
    });
  }

  private void updateUseGrayscaleEnabled() {
    final MarkdownHtmlPanelProvider.ProviderInfo selected = getSelectedProvider();
    myUseGrayscaleRenderingForJBCheckBox.setEnabled(isProviderOf(selected, JAVA_FX_HTML_PANEL_PROVIDER));
  }

  private static boolean isProviderOf(@NotNull MarkdownHtmlPanelProvider.ProviderInfo selected, @NotNull String provider) {
    return selected.getClassName().contains(provider);
  }

  @NotNull
  private static MarkdownHtmlPanelProvider getProvider(@SuppressWarnings("SameParameterValue") @NotNull String providerClass) {
    for (MarkdownHtmlPanelProvider provider : MarkdownHtmlPanelProvider.getProviders()) {
      if (isProviderOf(provider.getProviderInfo(), providerClass)) return provider;
    }

    throw new RuntimeException("Cannot find " + providerClass);
  }

  @NotNull
  private MarkdownHtmlPanelProvider.ProviderInfo getSelectedProvider() {
    if (isMultipleProviders()) {
      return Objects.requireNonNull(myPreviewPanelModel.getSelected());
    }
    else {
      return getProvider(JAVA_FX_HTML_PANEL_PROVIDER).getProviderInfo();
    }
  }

  @Override
  public void setMarkdownPreviewSettings(@NotNull MarkdownPreviewSettings settings) {
    if (isMultipleProviders() && myPreviewPanelModel.contains(settings.getHtmlPanelProviderInfo())) {
      myPreviewPanelModel.setSelectedItem(settings.getHtmlPanelProviderInfo());
    }

    mySplitLayoutModel.setSelectedItem(settings.getSplitEditorLayout());
    myUseGrayscaleRenderingForJBCheckBox.setSelected(settings.isUseGrayscaleRendering());
    myAutoScrollCheckBox.setSelected(settings.isAutoScrollPreview());
    myVerticalLayout.setSelected(settings.isVerticalSplit());
    myHorizontalLayout.setSelected(!settings.isVerticalSplit());

    updateUseGrayscaleEnabled();
  }

  @NotNull
  @Override
  public MarkdownPreviewSettings getMarkdownPreviewSettings() {
    MarkdownHtmlPanelProvider.ProviderInfo provider = getSelectedProvider();

    Objects.requireNonNull(provider);
    return new MarkdownPreviewSettings(mySplitLayoutModel.getSelectedItem(),
                                       provider,
                                       myUseGrayscaleRenderingForJBCheckBox.isSelected(),
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
