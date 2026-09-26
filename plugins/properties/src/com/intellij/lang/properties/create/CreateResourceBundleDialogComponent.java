// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.create;

import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.properties.BundleNameEvaluator;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.xml.XmlPropertiesFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ClickListener;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.AbstractButton;
import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class CreateResourceBundleDialogComponent {
  private static final Logger LOG = Logger.getInstance(CreateResourceBundleDialogComponent.class);

  private static final Comparator<Locale> LOCALE_COMPARATOR = (l1, l2) -> {
    if (l1 == PropertiesUtil.DEFAULT_LOCALE) {
      return -1;
    }
    if (l2 == PropertiesUtil.DEFAULT_LOCALE) {
      return 1;
    }
    return l1.toString().compareTo(l2.toString());
  };
  private final Project myProject;
  private final PsiDirectory myDirectory;
  private final ResourceBundle myResourceBundle;
  private final JPanel myPanel;
  private final JTextField myResourceBundleBaseNameTextField;
  private final JButton myAddLocaleFromExistButton;
  private final JPanel myNewBundleLocalesPanel;
  private final JPanel myProjectExistLocalesPanel;
  private final JButton myAddAllButton;
  private final JPanel myResourceBundleNamePanel;
  private final JCheckBox myUseXMLBasedPropertiesCheckBox;
  private CollectionListModel<Locale> myLocalesModel;
  private final Map<Locale, @NlsSafe String> myLocaleSuffixes; // java.util.Locale is case insensitive

  public CreateResourceBundleDialogComponent(@NotNull Project project, PsiDirectory directory, ResourceBundle resourceBundle) {
    myProject = project;
    myDirectory = directory;
    myResourceBundle = resourceBundle;
    {
      final JBList<Locale> projectExistLocalesList = new JBList<>();
      final MyExistLocalesListModel existLocalesListModel = new MyExistLocalesListModel();
      projectExistLocalesList.setModel(existLocalesListModel);
      projectExistLocalesList.setCellRenderer(getLocaleRenderer());
      myProjectExistLocalesPanel = ToolbarDecorator.createDecorator(projectExistLocalesList)
        .disableRemoveAction()
        .disableUpDownActions()
        .createPanel();
      myProjectExistLocalesPanel.setBorder(IdeBorderFactory.createTitledBorder(
        PropertiesBundle.message("create.resource.bundle.project.locales.title"), false));

      final JBList localesToAddList = new JBList();

      final List<Locale> locales;
      final List<Locale> restrictedLocales;
      if (myResourceBundle == null) {
        locales = Collections.singletonList(PropertiesUtil.DEFAULT_LOCALE);
        restrictedLocales = Collections.emptyList();
      }
      else {
        locales = Collections.emptyList();
        restrictedLocales = ContainerUtil.map(myResourceBundle.getPropertiesFiles(), PropertiesFile::getLocale);
      }
      myLocalesModel = new CollectionListModel<>(locales) {
        @Override
        public void add(@NotNull List<? extends Locale> elements) {
          final List<Locale> currentItems = getItems();
          elements = ContainerUtil.filter(elements,
                                          locale -> !restrictedLocales.contains(locale) && !currentItems.contains(locale));
          super.add(elements);
        }
      };
      localesToAddList.setModel(myLocalesModel);
      localesToAddList.setCellRenderer(getLocaleRenderer());
      localesToAddList.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          projectExistLocalesList.clearSelection();
        }
      });
      projectExistLocalesList.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          localesToAddList.clearSelection();
        }
      });

      myNewBundleLocalesPanel = ToolbarDecorator.createDecorator(localesToAddList).setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            final String rawAddedLocales = Messages.showInputDialog(myProject,
                                                                    PropertiesBundle.message(
                                                                      "create.resource.bundle.dialog.add.locales.validator.message"),
                                                                    PropertiesBundle.message(
                                                                      "create.resource.bundle.dialog.add.locales.validator.title"),
                                                                    null, null, new InputValidatorEx() {
                @Override
                public @Nullable String getErrorText(String inputString) {
                  return checkInput(inputString) ? null : PropertiesBundle.message("create.resource.bundle.invalid.locales.error.text");
                }

                @Override
                public boolean checkInput(String inputString) {
                  return extractLocalesFromString(inputString) != null;
                }

                @Override
                public boolean canClose(String inputString) {
                  return checkInput(inputString);
                }
              });
            if (rawAddedLocales != null) {
              final Map<Locale, String> locales = extractLocalesFromString(rawAddedLocales);
              LOG.assertTrue(locales != null);
              myLocaleSuffixes.putAll(locales);
              myLocalesModel.add(new ArrayList<>(locales.keySet()));
            }
          }
        }).setAddActionName(PropertiesBundle.message("create.resource.bundle.add.locales.by.suffix.action.text"))
        .disableUpDownActions().createPanel();
      myNewBundleLocalesPanel.setBorder(IdeBorderFactory.createTitledBorder(
        PropertiesBundle.message("create.resource.bundle.locales.to.add.chooser.title"), false));

      myAddLocaleFromExistButton = new JButton(AllIcons.Actions.Forward);
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          if (clickCount == 1) {
            myLocalesModel.add(projectExistLocalesList.getSelectedValuesList());
            return true;
          }
          return false;
        }
      }.installOn(myAddLocaleFromExistButton);

      projectExistLocalesList.addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          final List<Locale> currentItems = myLocalesModel.getItems();
          for (Locale l : projectExistLocalesList.getSelectedValuesList()) {
            if (!restrictedLocales.contains(l) && !currentItems.contains(l)) {
              myAddLocaleFromExistButton.setEnabled(true);
              return;
            }
          }
          myAddLocaleFromExistButton.setEnabled(false);
        }
      });
      myAddLocaleFromExistButton.setEnabled(false);

      myAddAllButton = new JButton(PropertiesBundle.message("create.resource.bundle.add.all.btn.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          if (clickCount == 1) {
            myLocalesModel.add(existLocalesListModel.getLocales());
          }
          return false;
        }
      }.installOn(myAddAllButton);
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myPanel = new JPanel();
      myPanel.setLayout(new BorderLayout(0, 0));
      myResourceBundleNamePanel = new JPanel();
      myResourceBundleNamePanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(myResourceBundleNamePanel, BorderLayout.NORTH);
      myResourceBundleBaseNameTextField = new JTextField();
      myResourceBundleNamePanel.add(myResourceBundleBaseNameTextField,
                                    new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                        new Dimension(150, -1), null, 0, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/PropertiesBundle", "label.resource.bundle.base.name"));
      myResourceBundleNamePanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                null, null, 0, false));
      myUseXMLBasedPropertiesCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(myUseXMLBasedPropertiesCheckBox,
                                this.$$$getMessageFromBundle$$$("messages/PropertiesBundle", "checkbox.use.xml.based.properties.files"));
      myResourceBundleNamePanel.add(myUseXMLBasedPropertiesCheckBox,
                                    new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(panel1, BorderLayout.CENTER);
      myAddLocaleFromExistButton.setFocusable(false);
      myAddLocaleFromExistButton.setText("");
      panel1.add(myAddLocaleFromExistButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
      panel1.add(myNewBundleLocalesPanel, new GridConstraints(0, 2, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              null, null, null, 0, false));
      panel1.add(myProjectExistLocalesPanel, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
      myAddAllButton.setFocusable(false);
      this.$$$loadButtonText$$$(myAddAllButton, this.$$$getMessageFromBundle$$$("messages/PropertiesBundle", "button.add.all"));
      panel1.add(myAddAllButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }
    myLocaleSuffixes = new HashMap<>();
    if (resourceBundle != null) {
      myResourceBundleNamePanel.setVisible(false);
      myUseXMLBasedPropertiesCheckBox.setVisible(false);
    }
    else {
      final String checkBoxSelectedStateKey = getClass() + ".useXmlPropertiesFiles";
      myUseXMLBasedPropertiesCheckBox.setSelected(PropertiesComponent.getInstance().getBoolean(checkBoxSelectedStateKey));
      myUseXMLBasedPropertiesCheckBox.addContainerListener(new ContainerAdapter() {
        @Override
        public void componentRemoved(ContainerEvent e) {
          PropertiesComponent.getInstance().setValue(checkBoxSelectedStateKey, myUseXMLBasedPropertiesCheckBox.isSelected());
        }
      });
    }
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    java.util.ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (java.util.ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = java.util.ResourceBundle.getBundle(path);
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

  public static class Dialog extends DialogWrapper {
    private final @NotNull PsiDirectory myDirectory;
    private final CreateResourceBundleDialogComponent myComponent;
    private PsiElement[] myCreatedFiles;

    protected Dialog(@NotNull Project project, @Nullable PsiDirectory directory, @Nullable ResourceBundle resourceBundle) {
      super(project);
      if (directory == null) {
        LOG.assertTrue(resourceBundle != null && getResourceBundlePlacementDirectory(resourceBundle) != null);
      }
      myDirectory = directory == null ? resourceBundle.getDefaultPropertiesFile().getContainingFile().getContainingDirectory() : directory;
      myComponent = new CreateResourceBundleDialogComponent(project, myDirectory, resourceBundle);
      init();
      initValidation();
      setTitle(resourceBundle == null ? PropertiesBundle.message("create.resource.bundle.action.text")
                                      : PropertiesBundle
                                        .message("create.resource.bundle.add.locales.to.resource.bundle.title",
                                                 resourceBundle.getBaseName()));
    }

    @Override
    protected void doOKAction() {
      final String errorString = myComponent.canCreateAllFilesForAllLocales();
      if (errorString != null) {
        Messages.showErrorDialog(getContentPanel(), errorString);
      }
      else {
        final List<PsiFile> createFiles = myComponent.createPropertiesFiles();
        myCreatedFiles = createFiles.toArray(PsiElement.EMPTY_ARRAY);
        super.doOKAction();
      }
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
      for (String fileName : myComponent.getFileNamesToCreate()) {
        if (!PathUtil.isValidFileName(fileName)) {
          return new ValidationInfo(
            PropertiesBundle.message("create.resource.bundle.file.name.for.properties.file.0.is.invalid.error", fileName));
        }
        else {
          if (myDirectory.findFile(fileName) != null) {
            return new ValidationInfo(PropertiesBundle.message("create.resource.bundle.file.with.name.0.already.exist.error", fileName));
          }
        }
      }

      return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      return myComponent.getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myComponent.myResourceBundleBaseNameTextField;
    }

    public PsiElement[] getCreatedFiles() {
      return myCreatedFiles;
    }
  }

  private List<PsiFile> createPropertiesFiles() {
    final Set<String> fileNames = getFileNamesToCreate();
    final List<PsiFile> createdFiles = WriteCommandAction.runWriteCommandAction(myProject,
                                                                                (Computable<List<PsiFile>>)() -> ReadAction.compute(
                                                                                  () -> ContainerUtil.map(fileNames, n -> {
                                                                                    final boolean isXml = myResourceBundle == null
                                                                                                          ? myUseXMLBasedPropertiesCheckBox.isSelected()
                                                                                                          : myResourceBundle.getDefaultPropertiesFile() instanceof XmlPropertiesFile;
                                                                                    if (isXml) {
                                                                                      FileTemplate template =
                                                                                        FileTemplateManager.getInstance(myProject)
                                                                                          .getInternalTemplate("XML Properties File.xml");
                                                                                      try {
                                                                                        return (PsiFile)FileTemplateUtil.createFromTemplate(
                                                                                          template, n, null, myDirectory);
                                                                                      }
                                                                                      catch (Exception e) {
                                                                                        throw new RuntimeException(e);
                                                                                      }
                                                                                    }
                                                                                    else {
                                                                                      return myDirectory.createFile(n);
                                                                                    }
                                                                                  })));
    combineToResourceBundleIfNeeded(createdFiles);
    return createdFiles;
  }

  private @NotNull @Unmodifiable Set<String> getFileNamesToCreate() {
    final String name = getBaseName();
    final String suffix = getPropertiesFileSuffix();
    return ContainerUtil.map2Set(myLocalesModel.getItems(),
                                 locale -> name +
                                           (locale == PropertiesUtil.DEFAULT_LOCALE ? "" : ("_" + myLocaleSuffixes
                                                                                                  .getOrDefault(locale,
                                                                                                                locale.toString()))) +
                                           suffix);
  }

  private void combineToResourceBundleIfNeeded(Collection<? extends PsiFile> files) {
    Collection<PropertiesFile> createdFiles = ContainerUtil.map(files, dom -> {
      final PropertiesFile file = PropertiesImplUtil.getPropertiesFile(dom);
      LOG.assertTrue(file != null, dom.getName());
      return file;
    });

    ResourceBundle mainBundle = myResourceBundle;
    final Set<ResourceBundle> allBundles = new HashSet<>();
    if (mainBundle != null) {
      allBundles.add(mainBundle);
    }
    boolean needCombining = false;
    for (PropertiesFile file : createdFiles) {
      final ResourceBundle rb = file.getResourceBundle();
      if (mainBundle == null) {
        mainBundle = rb;
      }
      else if (!mainBundle.equals(rb)) {
        needCombining = true;
      }
      allBundles.add(rb);
    }

    if (needCombining) {
      final List<PropertiesFile> toCombine = new ArrayList<>(createdFiles);
      final String baseName = getBaseName();
      if (myResourceBundle != null) {
        toCombine.addAll(myResourceBundle.getPropertiesFiles());
      }
      ResourceBundleManager manager = ResourceBundleManager.getInstance(mainBundle.getProject());
      for (ResourceBundle bundle : allBundles) {
        manager.dissociateResourceBundle(bundle);
      }
      manager.combineToResourceBundle(toCombine, baseName);
    }
  }

  private String getBaseName() {
    return myResourceBundle == null ? myResourceBundleBaseNameTextField.getText() : myResourceBundle.getBaseName();
  }

  private @NlsContexts.DialogMessage String canCreateAllFilesForAllLocales() {
    final String name = getBaseName();
    if (name.isEmpty()) {
      return PropertiesBundle.message("create.resource.bundle.base.name.is.empty.error");
    }
    final Set<String> files = getFileNamesToCreate();
    if (files.isEmpty()) {
      return PropertiesBundle.message("create.resource.bundle.no.locales.added.error");
    }
    for (PsiElement element : myDirectory.getChildren()) {
      if (element instanceof PsiFile) {
        if (element instanceof PropertiesFile propertiesFile) {
          final String propertiesFileName = propertiesFile.getName();
          if (files.contains(propertiesFileName)) {
            return PropertiesBundle.message("create.resource.bundle.some.of.files.already.exist.error");
          }
        }
      }
    }
    return null;
  }

  private String getPropertiesFileSuffix() {
    if (myResourceBundle == null) {
      return myUseXMLBasedPropertiesCheckBox.isSelected() ? ".xml" : ".properties";
    }
    return "." + myResourceBundle.getDefaultPropertiesFile().getContainingFile().getFileType().getDefaultExtension();
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private static @Nullable Map<Locale, String> extractLocalesFromString(final String rawLocales) {
    if (rawLocales.isEmpty()) {
      return Collections.emptyMap();
    }
    final String[] splitRawLocales = rawLocales.split(",");
    final Map<Locale, String> locales = new HashMap<>(splitRawLocales.length);

    for (String rawLocale : splitRawLocales) {
      final Pair<Locale, String> localeAndSuffix = PropertiesUtil.getLocaleAndTrimmedSuffix("_" + rawLocale + ".properties");
      if (localeAndSuffix.getFirst() == PropertiesUtil.DEFAULT_LOCALE) {
        return null;
      }
      locales.putIfAbsent(localeAndSuffix.getFirst(), localeAndSuffix.getSecond());
    }
    return locales;
  }

  private @NotNull ColoredListCellRenderer<Locale> getLocaleRenderer() {
    return new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Locale locale, int index, boolean selected, boolean hasFocus) {
        if (PropertiesUtil.DEFAULT_LOCALE == locale) {
          append(PropertiesBundle.message("create.resource.bundle.default.locale.presentation"));
        }
        else {
          append(myLocaleSuffixes.getOrDefault(locale, locale.toString()));
          append(PropertiesUtil.getPresentableLocale(locale), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    };
  }

  private final class MyExistLocalesListModel extends AbstractListModel {
    private final List<Locale> myLocales;

    private MyExistLocalesListModel() {
      myLocales = new ArrayList<>();
      myLocales.add(PropertiesUtil.DEFAULT_LOCALE);
      PropertiesReferenceManager.getInstance(myProject).processPropertiesFiles(GlobalSearchScope.projectScope(myProject),
                                                                               (baseName, propertiesFile) -> {
                                                                                 final Locale locale = propertiesFile.getLocale();
                                                                                 if (locale != PropertiesUtil.DEFAULT_LOCALE &&
                                                                                     !myLocales.contains(locale)) {
                                                                                   myLocales.add(locale);
                                                                                 }
                                                                                 return true;
                                                                               }, BundleNameEvaluator.DEFAULT);
      myLocales.sort(LOCALE_COMPARATOR);
    }

    @Override
    public int getSize() {
      return myLocales.size();
    }

    @Override
    public Locale getElementAt(int index) {
      return myLocales.get(index);
    }

    public List<Locale> getLocales() {
      return myLocales;
    }
  }

  static @Nullable PsiDirectory getResourceBundlePlacementDirectory(ResourceBundle resourceBundle) {
    PsiDirectory containingDirectory = null;
    for (PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
      if (containingDirectory == null) {
        containingDirectory = propertiesFile.getContainingFile().getContainingDirectory();
      }
      else if (!containingDirectory.isEquivalentTo(propertiesFile.getContainingFile().getContainingDirectory())) {
        return null;
      }
    }
    LOG.assertTrue(containingDirectory != null);
    return containingDirectory;
  }
}
