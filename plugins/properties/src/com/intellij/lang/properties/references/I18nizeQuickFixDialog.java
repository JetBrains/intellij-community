// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.references;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.ide.util.TreeFileChooserFactory;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.LastSelectedPropertiesFileStore;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author cdr
 */
public class I18nizeQuickFixDialog extends DialogWrapper implements I18nizeQuickFixModel {
  protected static final Logger LOG = Logger.getInstance(I18nizeQuickFixDialog.class);

  private static final Pattern PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private JTextField myValue;
  private JComboBox myKey;
  private final TextFieldWithHistory myPropertiesFile;
  protected JPanel myPanel;
  private JCheckBox myUseResourceBundle;
  protected final Project myProject;
  protected final PsiFile myContext;
  protected final Set<Module> myContextModules;

  private JPanel myPropertiesFilePanel;
  protected JPanel myExtensibilityPanel;

  protected final String myDefaultPropertyValue;
  protected final DialogCustomization myCustomization;

  public static class DialogCustomization {
    private final String title;
    private final boolean suggestExistingProperties;
    private final boolean focusValueComponent;
    private final List<PropertiesFile> propertiesFiles;
    private final String suggestedName;

    public DialogCustomization(String title, boolean suggestExistingProperties, boolean focusValueComponent,
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
                               @NotNull final PsiFile context,
                               @NotNull String defaultPropertyValue,
                               DialogCustomization customization
                               ) {
    this(project, context, defaultPropertyValue, customization, false);
  }

  protected I18nizeQuickFixDialog(@NotNull Project project,
                                  @NotNull final PsiFile context,
                                  @NotNull String defaultPropertyValue,
                                  DialogCustomization customization,
                                  boolean ancestorResponsible) {
    super(false);
    myProject = project;
    myContext = FileContextUtil.getContextFile(context);
    myContextModules = ContainerUtil.createMaybeSingletonSet(ModuleUtilCore.findModuleForFile(myContext));

    myDefaultPropertyValue = defaultPropertyValue;
    myCustomization = customization != null ? customization:new DialogCustomization();
    setTitle(myCustomization.title != null ? myCustomization.title : PropertiesBundle.message("i18nize.dialog.title"));

    myPropertiesFile = new TextFieldWithHistory();
    myPropertiesFile.setHistorySize(-1);
    myPropertiesFilePanel.add(GuiUtils.constructFieldWithBrowseButton(myPropertiesFile, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TreeFileChooserFactory chooserFactory = TreeFileChooserFactory.getInstance(myProject);
        final PropertiesFile propertiesFile = getPropertiesFile();
        TreeFileChooser fileChooser = chooserFactory.createFileChooser(
          PropertiesBundle.message("i18nize.dialog.property.file.chooser.title"), propertiesFile != null ? propertiesFile.getContainingFile() : null, StdFileTypes.PROPERTIES, null);
        fileChooser.showDialog();
        PsiFile selectedFile = fileChooser.getSelectedFile();
        if (selectedFile == null) return;
        myPropertiesFile.setText(FileUtil.toSystemDependentName(selectedFile.getVirtualFile().getPath()));
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


    @NonNls final String KEY = "I18NIZE_DIALOG_USE_RESOURCE_BUNDLE";
    final boolean useBundleByDefault =
      !PropertiesComponent.getInstance().isValueSet(KEY) || PropertiesComponent.getInstance().isTrueValue(KEY);
    myUseResourceBundle.setSelected(useBundleByDefault);
    myUseResourceBundle.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(KEY, Boolean.valueOf(myUseResourceBundle.isSelected()).toString());
      }
    });

    if (!ancestorResponsible) init();
  }

  @Override
  protected void init() {
    populatePropertiesFiles();
    propertiesFileChanged();
    somethingChanged();
    setKeyValueEditBoxes();

    super.init();
  }

  private JTextField getKeyTextField() {
    return (JTextField)myKey.getEditor().getEditorComponent();
  }

  @NotNull
  protected List<String> getExistingValueKeys(String value) {
    if(!myCustomization.suggestExistingProperties) {
      return Collections.emptyList();
    }
    final ArrayList<String> result = new ArrayList<>();

    // check if property value already exists among properties file values and suggest corresponding key
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      for (IProperty property : propertiesFile.getProperties()) {
        if (Comparing.strEqual(property.getValue(), value)) {
          result.add(0, property.getUnescapedKey());
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

  @NotNull
  public static String generateDefaultPropertyKey(@NotNull String rawValue) {
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
        if (result.length() > 0) {
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
    final List<String> existingValueKeys = getExistingValueKeys(myDefaultPropertyValue);

    if (existingValueKeys.isEmpty()) {
      getKeyTextField().setText(suggestPropertyKey(myDefaultPropertyValue));
    }
    else {
      for (String key : existingValueKeys) {
        myKey.addItem(key);
      }
      myKey.setSelectedItem(existingValueKeys.get(0));
    }


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
    List<String> paths = suggestPropertiesFiles();
    final String lastUrl = suggestSelectedFileUrl(paths);
    final String lastPath = lastUrl == null ? null : FileUtil.toSystemDependentName(VfsUtil.urlToPath(lastUrl));
    if (lastPath != null) {
      paths.remove(lastPath);
      paths.add(0, lastPath);
    }
    myPropertiesFile.setHistory(paths);
    if (lastPath != null) {
      myPropertiesFile.setSelectedItem(lastPath);
    }
    if (myPropertiesFile.getSelectedIndex() == -1 && !paths.isEmpty()) {
      myPropertiesFile.setText(paths.get(0));
    }
  }

  private String suggestSelectedFileUrl(List<String> paths) {
    if (myDefaultPropertyValue != null) {
      for (String path : paths) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path));
        if (file == null) continue;
        PsiFile psiFile = myContext.getManager().findFile(file);
        if (!(psiFile instanceof PropertiesFile)) continue;
        for (IProperty property : ((PropertiesFile)psiFile).getProperties()) {
          if (property.getValue().equals(myDefaultPropertyValue)) return path;
        }
      }
    }
    return LastSelectedPropertiesFileStore.getInstance().suggestLastSelectedPropertiesFileUrl(myContext);
  }

  private void saveLastSelectedFile() {
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      LastSelectedPropertiesFileStore.getInstance().saveLastSelectedPropertiesFile(myContext, propertiesFile);
    }
  }

  protected List<String> suggestPropertiesFiles() {
    if (myCustomization.propertiesFiles != null && !myCustomization.propertiesFiles.isEmpty()) {
      ArrayList<String> list = new ArrayList<>();
      for (PropertiesFile propertiesFile : myCustomization.propertiesFiles) {
        final VirtualFile virtualFile = propertiesFile.getVirtualFile();
        if (virtualFile != null) {
          list.add(virtualFile.getPath());
        }
      }
      return list;
    }
    return defaultSuggestPropertiesFiles();
  }

  protected List<String> defaultSuggestPropertiesFiles() {
    return I18nUtil.defaultSuggestPropertiesFiles(myProject, myContextModules);
  }

  protected PropertiesFile getPropertiesFile() {
    String path = FileUtil.toSystemIndependentName(myPropertiesFile.getText());
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    return virtualFile != null
           ? PropertiesImplUtil.getPropertiesFile(PsiManager.getInstance(myProject).findFile(virtualFile))
           : null;
  }

  private boolean createPropertiesFileIfNotExists() {
    if (getPropertiesFile() != null) return true;
    final String path = FileUtil.toSystemIndependentName(myPropertiesFile.getText());
    if (StringUtil.isEmptyOrSpaces(path)) {
      String message = PropertiesBundle.message("i18nize.empty.file.path", myPropertiesFile.getText());
      Messages.showErrorDialog(myProject, message, PropertiesBundle.message("i18nize.error.creating.properties.file"));
      myPropertiesFile.requestFocusInWindow();
      return false;
    }
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(path);
    if (fileType != StdFileTypes.PROPERTIES && fileType != StdFileTypes.XML) {
      String message = PropertiesBundle.message("i18nize.cant.create.properties.file.because.its.name.is.associated",
                                                 myPropertiesFile.getText(), fileType.getDescription());
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
          if (fileType == StdFileTypes.PROPERTIES) {
            return psiManager.findFile(dir.createChildData(this, file.getName()));
          }
          else {
            FileTemplate template = FileTemplateManager.getInstance(myProject).getInternalTemplate("XML Properties File.xml");
            LOG.assertTrue(template != null);
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
    return myCustomization.focusValueComponent ? myValue:myKey;
  }

  @Override
  public void dispose() {
    saveLastSelectedFile();
    super.dispose();
  }

  @Override
  protected void doOKAction() {
    if (!createPropertiesFileIfNotExists()) return;
    Collection<PropertiesFile> propertiesFiles = getAllPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      IProperty existingProperty = propertiesFile.findPropertyByKey(getKey());
      final String propValue = getValue();
      if (existingProperty != null && !Comparing.strEqual(existingProperty.getValue(), propValue)) {
        final String messageText = PropertiesBundle.message("i18nize.dialog.error.property.already.defined.message", getKey(), propertiesFile.getName());
        final int code = Messages.showOkCancelDialog(myProject,
                                                     messageText,
                                                     PropertiesBundle.message("i18nize.dialog.error.property.already.defined.title"),
                                                     null);
        if (code == Messages.CANCEL) {
          return;
        }
      }
    }

    super.doOKAction();
  }

  @Override
  protected String getHelpId() {
    return "editing.propertyFile.i18nInspection";
  }

  @Override
  public String getValue() {
    return unescapeLineBreaks(myValue.getText());
  }

  @Override
  public String getKey() {
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
