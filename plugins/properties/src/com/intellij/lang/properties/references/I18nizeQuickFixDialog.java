// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.references;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.ide.util.TreeFileChooserFactory;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.LastSelectedPropertiesFileStore;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

public class I18nizeQuickFixDialog extends DialogWrapper implements I18nizeQuickFixModel {
  protected static final Logger LOG = Logger.getInstance(I18nizeQuickFixDialog.class);

  private static final Pattern PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private final JTextField myValue;
  private final JTextField myKey;
  private final TextFieldWithHistory myPropertiesFile;
  protected final JPanel myPanel;
  private final JCheckBox myUseResourceBundle;
  protected final Project myProject;
  protected final PsiFile myContext;
  protected final Set<Module> myContextModules;

  private final JPanel myPropertiesFilePanel;
  protected final JPanel myExtensibilityPanel;
  private final ComboBox<IProperty> myExistingProperties;
  private final JBRadioButton myCreateNewPropertyRb;
  private final JPanel myNewPanel;
  private final JBRadioButton myUseExistingPropertyRb;

  protected final String myDefaultPropertyValue;
  protected final DialogCustomization myCustomization;

  public static class DialogCustomization {
    private final @NlsContexts.DialogTitle String title;
    private final boolean suggestExistingProperties;
    private final boolean focusValueComponent;
    private final List<PropertiesFile> propertiesFiles;
    private final String suggestedName;

    public DialogCustomization(@NlsContexts.DialogTitle String title, boolean suggestExistingProperties, boolean focusValueComponent,
                               List<PropertiesFile> propertiesFiles,
                               String suggestedName) {
      this.title = title;
      this.suggestExistingProperties = suggestExistingProperties;
      this.focusValueComponent = focusValueComponent;
      this.propertiesFiles = propertiesFiles;
      this.suggestedName = suggestedName;
    }

    public DialogCustomization() {
      this(null, true, false, null, null);
    }

    public String getSuggestedName() {
      return suggestedName;
    }
  }

  public I18nizeQuickFixDialog(@NotNull Project project,
                               final @NotNull PsiFile context,
                               @NotNull String defaultPropertyValue,
                               DialogCustomization customization
  ) {
    this(project, context, defaultPropertyValue, customization, false);
  }

  protected I18nizeQuickFixDialog(@NotNull Project project,
                                  final @NotNull PsiFile context,
                                  @NotNull String defaultPropertyValue,
                                  DialogCustomization customization,
                                  boolean ancestorResponsible) {
    super(false);
    myProject = project;
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.setMinimumSize(new Dimension(-1, -1));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(7, 2, new Insets(5, 5, 5, 5), 4, -1));
      panel1.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
      myPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      panel1.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(),
                                                                                this.$$$getMessageFromBundle$$$("messages/PropertiesBundle",
                                                                                                                "i18n.quickfix.property.panel.title"),
                                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                TitledBorder.DEFAULT_POSITION, null, null));
      myNewPanel = new JPanel();
      myNewPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
      panel1.add(myNewPanel, new GridConstraints(1, 0, 4, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 2, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/PropertiesBundle",
                                                                       "i18n.quickfix.property.panel.property.key.label"));
      myNewPanel.add(label1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
      final JLabel label2 = new JLabel();
      this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/PropertiesBundle",
                                                                       "i18n.quickfix.property.panel.property.value.label"));
      myNewPanel.add(label2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
      myValue = new JTextField();
      myNewPanel.add(myValue, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
      final JLabel label3 = new JLabel();
      this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/PropertiesBundle",
                                                                       "i18n.quickfix.property.panel.properties.file.label"));
      myNewPanel.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
      myPropertiesFilePanel = new JPanel();
      myPropertiesFilePanel.setLayout(new BorderLayout(0, 0));
      myNewPanel.add(myPropertiesFilePanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myUseResourceBundle = new JCheckBox();
      this.$$$loadButtonText$$$(myUseResourceBundle, this.$$$getMessageFromBundle$$$("messages/PropertiesBundle",
                                                                                     "i18n.quickfix.property.panel.update.all.files.in.bundle.checkbox"));
      myNewPanel.add(myUseResourceBundle, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myKey = new JTextField();
      myKey.setEditable(true);
      myNewPanel.add(myKey, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                GridConstraints.SIZEPOLICY_FIXED, new Dimension(100, -1), null, null, 0, false));
      myCreateNewPropertyRb = new JBRadioButton();
      myCreateNewPropertyRb.setSelected(true);
      this.$$$loadButtonText$$$(myCreateNewPropertyRb,
                                this.$$$getMessageFromBundle$$$("messages/PropertiesBundle", "radio.button.create.new.property"));
      panel1.add(myCreateNewPropertyRb, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myUseExistingPropertyRb = new JBRadioButton();
      this.$$$loadButtonText$$$(myUseExistingPropertyRb,
                                this.$$$getMessageFromBundle$$$("messages/PropertiesBundle", "radio.button.use.existing.property"));
      panel1.add(myUseExistingPropertyRb, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myExistingProperties = new ComboBox();
      panel1.add(myExistingProperties, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myExtensibilityPanel = new JPanel();
      myExtensibilityPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(myExtensibilityPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                            null, null, null, 0, false));
      label1.setLabelFor(myKey);
      label2.setLabelFor(myValue);
      label3.setLabelFor(myPropertiesFilePanel);
    }
    myContext = FileContextUtil.getContextFile(context);
    myContextModules = ContainerUtil.createMaybeSingletonSet(ModuleUtilCore.findModuleForFile(myContext));

    myDefaultPropertyValue = escapeValue(defaultPropertyValue, context);
    myCustomization = customization != null ? customization : new DialogCustomization();
    setTitle(myCustomization.title != null ? myCustomization.title : PropertiesBundle.message("i18nize.dialog.title"));

    myPropertiesFile = new TextFieldWithHistory();
    myPropertiesFile.setHistorySize(-1);
    myPropertiesFile.setEditable(false);
    myPropertiesFile.setSwingPopup(false);
    ComboboxSpeedSearch.installSpeedSearch(myPropertiesFile, p -> (String)p);
    myPropertiesFilePanel.add(GuiUtils.constructFieldWithBrowseButton(myPropertiesFile, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TreeFileChooserFactory chooserFactory = TreeFileChooserFactory.getInstance(myProject);
        final PropertiesFile propertiesFile = getPropertiesFile();
        TreeFileChooser fileChooser = chooserFactory.createFileChooser(
          PropertiesBundle.message("i18nize.dialog.property.file.chooser.title"),
          propertiesFile != null ? propertiesFile.getContainingFile() : null, PropertiesFileType.INSTANCE, null);
        fileChooser.showDialog();
        PsiFile selectedFile = fileChooser.getSelectedFile();
        if (selectedFile == null) return;
        String selectedPath = selectedFile.getVirtualFile().getPath();
        myPropertiesFile.setText(FileUtil.toSystemDependentName(selectedPath));
        myPropertiesFile.setSelectedItem(FileUtil.toSystemDependentName(selectedPath));
      }
    }), BorderLayout.CENTER);

    myPropertiesFile.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        propertiesFileChanged();
        somethingChanged();
      }
    });

    getKeyTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        somethingChanged();
      }
    });

    myValue.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        somethingChanged();
      }
    });


    final @NonNls String KEY = "I18NIZE_DIALOG_USE_RESOURCE_BUNDLE";
    final boolean useBundleByDefault =
      !PropertiesComponent.getInstance().isValueSet(KEY) || PropertiesComponent.getInstance().isTrueValue(KEY);
    myUseResourceBundle.setSelected(useBundleByDefault);
    myUseResourceBundle.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(KEY, Boolean.valueOf(myUseResourceBundle.isSelected()).toString());
      }
    });

    myExistingProperties.setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends IProperty> list,
                                           IProperty value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value != null) {
          append(Objects.requireNonNull(value.getUnescapedKey()));
          append(" (");
          append(value.getPropertiesFile().getName());
          append(")");
        }
      }
    });

    myExistingProperties.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        somethingChanged();
      }
    });

    ButtonGroup bg = new ButtonGroup();
    bg.add(myCreateNewPropertyRb);
    bg.add(myUseExistingPropertyRb);
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myExistingProperties.setEnabled(myUseExistingPropertyRb.isSelected());
        UIUtil.setEnabled(myNewPanel, myCreateNewPropertyRb.isSelected(), true);
        somethingChanged();
      }
    };
    myCreateNewPropertyRb.addActionListener(listener);
    myUseExistingPropertyRb.addActionListener(listener);
    myExistingProperties.setEnabled(false);

    if (!ancestorResponsible) init();
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return myPanel; }

  protected String escapeValue(String value, @NotNull PsiFile context) {
    return value;
  }

  @Override
  protected void init() {
    populatePropertiesFiles();
    propertiesFileChanged();
    setKeyValueEditBoxes();

    super.init();
    somethingChanged();
  }

  private JTextField getKeyTextField() {
    return myKey;
  }

  protected @NotNull List<IProperty> getExistingProperties(String value) {
    if (!myCustomization.suggestExistingProperties) {
      return Collections.emptyList();
    }
    final ArrayList<IProperty> result = new ArrayList<>();

    // check if property value already exists among properties file values and suggest corresponding key
    List<String> propertyFiles = suggestPropertiesFiles();
    if (!propertyFiles.isEmpty()) {
      String selectedPath = FileUtil.toSystemIndependentName(getPropertiesFilePath());
      propertyFiles.remove(selectedPath);
      propertyFiles.add(0, selectedPath);
      for (String path : propertyFiles) {
        PropertiesFile propertiesFile = getPropertyFileByPath(path);
        if (propertiesFile != null) {
          for (IProperty property : propertiesFile.getProperties()) {
            if (Comparing.strEqual(property.getUnescapedValue(), value) && property.getUnescapedKey() != null) {
              result.add(property);
            }
          }
        }
      }
    }
    return result;
  }

  protected String suggestPropertyKey(String value) {
    if (myCustomization.suggestedName != null) {
      return myCustomization.suggestedName;
    }
    return suggestUniquePropertyKey(value, defaultSuggestPropertyKey(value), getPropertiesFile());
  }

  public static String suggestUniquePropertyKey(String value, String defaultKey, PropertiesFile propertiesFile) {
    // suggest property key not existing in this file
    if (defaultKey == null) {
      defaultKey = generateDefaultPropertyKey(value);
    }

    if (propertiesFile != null) {
      if (propertiesFile.findPropertyByKey(defaultKey) == null) return defaultKey;

      int suffix = 1;
      while (propertiesFile.findPropertyByKey(defaultKey + suffix) != null) {
        suffix++;
      }
      return defaultKey + suffix;
    }
    else {
      return defaultKey;
    }
  }

  public static @NotNull String generateDefaultPropertyKey(@NotNull String rawValue) {
    String value = PATTERN.matcher(Normalizer.normalize(rawValue, Normalizer.Form.NFD)).replaceAll("");
    String defaultKey;
    final StringBuilder result = new StringBuilder();
    boolean insertDotBeforeNextWord = false;
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        if (insertDotBeforeNextWord) {
          result.append('.');
        }
        result.append(Character.toLowerCase(c));
        insertDotBeforeNextWord = false;
      }
      else if (c == '&') {   //do not insert dot if there is letter after the amp
        if (insertDotBeforeNextWord) continue;
        if (i == value.length() - 1) {
          continue;
        }
        if (Character.isLetter(value.charAt(i + 1))) {
          continue;
        }
        insertDotBeforeNextWord = true;
      }
      else {
        if (!result.isEmpty()) {
          insertDotBeforeNextWord = true;
        }
      }
    }
    defaultKey = result.toString();
    return defaultKey;
  }

  protected String defaultSuggestPropertyKey(String value) {
    return null;
  }

  private void propertiesFileChanged() {
    PropertiesFile propertiesFile = getPropertiesFile();
    boolean hasResourceBundle =
      propertiesFile != null && propertiesFile.getResourceBundle().getPropertiesFiles().size() > 1;
    myUseResourceBundle.setEnabled(hasResourceBundle);
  }

  private void setKeyValueEditBoxes() {
    getKeyTextField().setText(suggestPropertyKey(myDefaultPropertyValue));
    final @NotNull List<IProperty> existingValueKeys = getExistingProperties(myDefaultPropertyValue);

    if (!existingValueKeys.isEmpty()) {
      for (IProperty key : existingValueKeys) {
        myExistingProperties.addItem(key);
      }
      myExistingProperties.setSelectedItem(existingValueKeys.get(0));
    }
    myUseExistingPropertyRb.setEnabled(!existingValueKeys.isEmpty());
    myValue.setText(escapeLineBreaks(myDefaultPropertyValue));
  }

  private static String escapeLineBreaks(String value) {
    return StringUtil.escapeLineBreak(StringUtil.escapeBackSlashes(value));
  }

  private static String unescapeLineBreaks(String value) {
    StringBuilder buffer = new StringBuilder(value.length());
    int length = value.length();
    int last = length - 1;
    for (int i = 0; i < length; i++) {
      char ch = value.charAt(i);
      if (ch == '\\' && i != last) {
        i++;
        ch = value.charAt(i);
        if (ch == 'n') {
          buffer.append('\n');
        }
        else if (ch == 'r') {
          buffer.append('\n');
        }
        else if (ch == '\\') {
          buffer.append('\\');
        }
        else {
          buffer.append('\\');
          buffer.append(ch);
        }
      }
      else {
        buffer.append(ch);
      }
    }
    return buffer.toString();
  }

  protected void somethingChanged() {
    setOKActionEnabled(!StringUtil.isEmptyOrSpaces(getKey()));
  }

  private void populatePropertiesFiles() {
    List<@NlsSafe String> paths = suggestPropertiesFiles();
    final String lastUrl = suggestSelectedFileUrl();
    final String lastPath = lastUrl == null ? null : FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(lastUrl));
    if (lastPath != null) {
      paths.remove(lastPath);
      paths.add(0, lastPath);
    }
    myPropertiesFile.setHistory(paths);
    if (lastPath != null) {
      myPropertiesFile.setSelectedItem(lastPath);
      myPropertiesFile.setText(lastPath);
    }
    if (myPropertiesFile.getSelectedIndex() == -1 && !paths.isEmpty()) {
      String selectedItem = paths.get(0);
      myPropertiesFile.setSelectedItem(selectedItem);
      myPropertiesFile.setText(selectedItem);
    }
  }

  private String suggestSelectedFileUrl() {
    return LastSelectedPropertiesFileStore.getInstance().suggestLastSelectedPropertiesFileUrl(myContext);
  }

  private void saveLastSelectedFile() {
    if (myCreateNewPropertyRb.isSelected()) {
      PropertiesFile propertiesFile = getPropertiesFile();
      if (propertiesFile != null) {
        LastSelectedPropertiesFileStore.getInstance().saveLastSelectedPropertiesFile(myContext, propertiesFile);
      }
    }
  }

  protected List<String> suggestPropertiesFiles() {
    if (myCustomization.propertiesFiles != null && !myCustomization.propertiesFiles.isEmpty()) {
      ArrayList<String> list = new ArrayList<>();
      for (PropertiesFile propertiesFile : myCustomization.propertiesFiles) {
        final VirtualFile virtualFile = propertiesFile.getVirtualFile();
        if (virtualFile != null) {
          list.add(FileUtil.toSystemDependentName(virtualFile.getPath()));
        }
      }
      return list;
    }
    return defaultSuggestPropertiesFiles();
  }

  protected List<String> defaultSuggestPropertiesFiles() {
    return I18nUtil.defaultSuggestPropertiesFiles(myProject, myContextModules);
  }

  protected @Nullable PropertiesFile getPropertiesFile() {
    String path = getPropertiesFilePath();
    if (path == null) return null;
    return getPropertyFileByPath(FileUtil.toSystemIndependentName(path));
  }

  private String getPropertiesFilePath() {
    return (String)myPropertiesFile.getSelectedItem();
  }

  private @Nullable PropertiesFile getPropertyFileByPath(String path) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    return virtualFile != null
           ? PropertiesImplUtil.getPropertiesFile(PsiManager.getInstance(myProject).findFile(virtualFile))
           : null;
  }

  protected boolean useExistingProperty() {
    if (myExistingProperties.isEnabled()) {
      IProperty item = myExistingProperties.getItem();
      if (item != null) return true;
    }
    return false;
  }

  private boolean createPropertiesFileIfNotExists() {
    if (getPropertiesFile() != null) return true;
    final String path = getPropertiesFilePath();
    if (StringUtil.isEmptyOrSpaces(path)) {
      String message = PropertiesBundle.message("i18nize.empty.file.path", path);
      Messages.showErrorDialog(myProject, message, PropertiesBundle.message("i18nize.error.creating.properties.file"));
      myPropertiesFile.requestFocusInWindow();
      return false;
    }
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(FileUtil.toSystemIndependentName(path));
    if (fileType != PropertiesFileType.INSTANCE && fileType != XmlFileType.INSTANCE) {
      String message = PropertiesBundle.message("i18nize.cant.create.properties.file.because.its.name.is.associated",
                                                getPropertiesFilePath(), fileType.getDescription());
      Messages.showErrorDialog(myProject, message, PropertiesBundle.message("i18nize.error.creating.properties.file"));
      myPropertiesFile.requestFocusInWindow();
      return false;
    }

    try {
      final File file = new File(path).getCanonicalFile();
      FileUtil.createParentDirs(file);
      ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<PsiFile, Exception>() {
        @Override
        public PsiFile compute() throws Exception {
          VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.getParentFile());
          final PsiManager psiManager = PsiManager.getInstance(myProject);
          if (dir == null) {
            throw new IOException("Error creating directory structure for file '" + path + "'");
          }
          if (fileType == PropertiesFileType.INSTANCE) {
            return psiManager.findFile(dir.createChildData(this, file.getName()));
          }
          else {
            FileTemplate template = FileTemplateManager.getInstance(myProject).getInternalTemplate("XML Properties File.xml");
            return (PsiFile)FileTemplateUtil.createFromTemplate(template, file.getName(), null, psiManager.findDirectory(dir));
          }
        }
      });
    }
    catch (Exception e) {
      Messages.showErrorDialog(myProject, e.getLocalizedMessage(), PropertiesBundle.message("i18nize.error.creating.properties.file"));
      return false;
    }
    return true;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCustomization.focusValueComponent ? myValue : myKey;
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  @Override
  protected void doOKAction() {
    if (!createPropertiesFileIfNotExists()) return;
    saveLastSelectedFile();
    Collection<PropertiesFile> propertiesFiles = getAllPropertiesFiles();

    HashMap<PropertiesFile, IProperty> existingProperties;
    try {
      ThrowableComputable<HashMap<PropertiesFile, IProperty>, RuntimeException> existingPropertiesGenerator =
        () -> findExistingProperties(propertiesFiles);
      existingProperties = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> ReadAction.compute(existingPropertiesGenerator),
        PropertiesBundle.message("i18nize.dialog.searching.for.already.existing.properties"),
        true, myProject
      );
    }
    catch (ProcessCanceledException e) {
      super.doOKAction();
      return;
    }

    for (PropertiesFile propertiesFile : propertiesFiles) {
      IProperty existingProperty = existingProperties.get(propertiesFile);
      final String propValue = getValue();
      if (existingProperty != null && !Comparing.strEqual(existingProperty.getUnescapedValue(), propValue)) {
        final String messageText =
          PropertiesBundle.message("i18nize.dialog.error.property.already.defined.message", getKey(), propertiesFile.getName());
        final boolean code = MessageDialogBuilder
          .yesNo(PropertiesBundle.message("i18nize.dialog.error.property.already.defined.title"), messageText)
          .ask(myProject);
        if (!code) {
          return;
        }
      }
    }

    super.doOKAction();
  }

  @RequiresBackgroundThread
  private HashMap<PropertiesFile, IProperty> findExistingProperties(Collection<PropertiesFile> files) {
    final HashMap<PropertiesFile, IProperty> existingProperties = new HashMap<>();
    for (PropertiesFile propertiesFile : files) {
      IProperty existingProperty = propertiesFile.findPropertyByKey(getKey());
      existingProperties.put(propertiesFile, existingProperty);
    }
    return existingProperties;
  }

  @Override
  protected String getHelpId() {
    return "editing.propertyFile.i18nInspection";
  }

  @Override
  public String getValue() {
    if (useExistingProperty()) {
      return myExistingProperties.getItem().getUnescapedValue();
    }
    return unescapeLineBreaks(myValue.getText());
  }

  @Override
  public String getKey() {
    if (useExistingProperty()) {
      return myExistingProperties.getItem().getUnescapedKey();
    }
    return getKeyTextField().getText();
  }

  @Override
  public boolean hasValidData() {
    assert !ApplicationManager.getApplication().isUnitTestMode();
    show();

    return getExitCode() == OK_EXIT_CODE;
  }

  private boolean isUseResourceBundle() {
    return myUseResourceBundle.isEnabled() && myUseResourceBundle.isSelected();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog";
  }

  @Override
  public Collection<PropertiesFile> getAllPropertiesFiles() {
    if (useExistingProperty()) {
      return Collections.singleton(myExistingProperties.getItem().getPropertiesFile());
    }
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile == null) return Collections.emptySet();
    Collection<PropertiesFile> propertiesFiles;
    if (isUseResourceBundle()) {
      propertiesFiles = propertiesFile.getResourceBundle().getPropertiesFiles();
    }
    else {
      propertiesFiles = Collections.singleton(propertiesFile);
    }
    return propertiesFiles;
  }
}
