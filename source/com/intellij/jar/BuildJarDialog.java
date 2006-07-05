package com.intellij.jar;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.ui.SplitterProportionsData;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.io.FileTypeFilter;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.text.MessageFormat;

/**
 * @author cdr
 */
public class BuildJarDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.jar.BuildJarDialog");
  @NonNls private static final String WARNING_TEMPLATE = "<html><body><b>{0}: </b>{1}</body><body>";
  private final Project myProject;
  private JPanel myModulesPanel;
  private JPanel myEditorPanel;
  private TextFieldWithBrowseButton myJarPath;
  private TextFieldWithBrowseButton myMainClass;
  private JPanel myPanel;
  private final Map<Module, SettingsEditor> mySettings = new THashMap<Module, SettingsEditor>();
  private Module myCurrentModule;
  private ElementsChooser<Module> myElementsChooser;
  private JPanel myModuleSettingsPanel;
  private LabeledComponent<TextFieldWithBrowseButton> myMainClassComponent;
  private LabeledComponent<TextFieldWithBrowseButton> myJarFilePathComponent;
  private JCheckBox myBuildJarsOnMake;
  private JLabel myWarningLabel;
  private final SplitterProportionsData mySplitterProportionsData = new SplitterProportionsData();

  protected BuildJarDialog(Project project) {
    super(true);
    myProject = project;

    setupControls();

    setTitle(IdeBundle.message("jar.build.dialog.title"));
    mySplitterProportionsData.externalizeFromDimensionService(getDimensionKey());
    mySplitterProportionsData.restoreSplitterProportions(myPanel);
    getOKAction().putValue(Action.NAME, IdeBundle.message("jar.build.button"));
    init();
    updateWarning();
  }

  private void updateWarning() {
    for (Map.Entry<Module, SettingsEditor> entry : mySettings.entrySet()) {
      try {
        final BuildJarDialog.SettingsEditor editor = entry.getValue();
        editor.saveUI();
        editor.checkSettings();
      }
      catch (RuntimeConfigurationException e) {
        myWarningLabel.setText(MessageFormat.format(WARNING_TEMPLATE, entry.getKey().getName(), e.getMessage()));
        myWarningLabel.setVisible(true);
        repaint();
        return;
      }
    }
    repaint();
    myWarningLabel.setVisible(false);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.generateJarFiles");
  }
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public static Collection<Module> getModulesToJar(Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    ArrayList<Module> result = new ArrayList<Module>();
    for (Module module : modules) {
      if (module.getModuleType() == ModuleType.JAVA) {
        result.add(module);
      }
    }
    Collections.sort(result, new Comparator<Module>() {
      public int compare(final Module o1, final Module o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    return result;

  }

  private void setupControls() {
    myWarningLabel.setIcon(IconLoader.getIcon("/runConfigurations/configurationWarning.png"));

    myBuildJarsOnMake.setSelected(BuildJarProjectSettings.getInstance(myProject).isBuildJarOnMake());

    myJarPath = myJarFilePathComponent.getComponent();
    myMainClass = myMainClassComponent.getComponent();

    myElementsChooser = new ElementsChooser<Module>(true);
    myModulesPanel.setLayout(new BorderLayout());
    myModulesPanel.add(myElementsChooser, BorderLayout.CENTER);

    final Collection<Module> modules = getModulesToJar(myProject);
    for (final Module module : modules) {
      if (module.getModuleType().isJ2EE()) continue;
      BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(module);
      myElementsChooser.addElement(module, buildJarSettings.isBuildJar(), new ChooserElementProperties(module));
    }
    myElementsChooser.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (myCurrentModule != null) {
          saveEditor(myCurrentModule);
        }

        Module selectedModule = myElementsChooser.getSelectedElement();
        if (selectedModule != null) {
          BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(selectedModule);
          BuildJarDialog.SettingsEditor settingsEditor = mySettings.get(selectedModule);
          if (settingsEditor == null) {
            settingsEditor = new SettingsEditor(selectedModule, buildJarSettings);
            mySettings.put(selectedModule, settingsEditor);
          }
          settingsEditor.refreshControls();
          boolean isBuildJar = myElementsChooser.getMarkedElements().contains(selectedModule);
          GuiUtils.enableChildren(myModuleSettingsPanel, isBuildJar);
          TitledBorder titledBorder = (TitledBorder)myModuleSettingsPanel.getBorder();
          titledBorder.setTitle(IdeBundle.message("jar.build.module.0.jar.settings", selectedModule.getName()));
          myModuleSettingsPanel.repaint();
        }
        myCurrentModule = selectedModule;
      }
    });
    myElementsChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<Module>() {
      public void elementMarkChanged(final Module element, final boolean isMarked) {
        GuiUtils.enableChildren(myModuleSettingsPanel, isMarked);
        if (isMarked) {
          setDefaultJarPath();
        }
      }
    });
    myJarPath.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String lastFilePath = myJarPath.getText();
        String path = lastFilePath == null ? RecentProjectsManager.getInstance().getLastProjectPath() : lastFilePath;
        File file = new File(path);
        if (!file.exists()) {
          path = file.getParent();
        }
        JFileChooser fileChooser = new JFileChooser(path);
        FileView fileView = new FileView() {
          public Icon getIcon(File f) {
            if (f.isDirectory()) return super.getIcon(f);
            FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
            return fileType.getIcon();
          }
        };
        fileChooser.setFileView(fileView);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setDialogTitle(IdeBundle.message("jar.build.save.title"));
        fileChooser.addChoosableFileFilter(new FileTypeFilter(StdFileTypes.ARCHIVE));

        if (fileChooser.showSaveDialog(WindowManager.getInstance().suggestParentWindow(myProject)) != JFileChooser.APPROVE_OPTION) {
          return;
        }
        file = fileChooser.getSelectedFile();
        if (file == null) return;
        myJarPath.setText(file.getPath());
      }
    });

    myMainClass.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String mainClass = myMainClass.getText();
        GlobalSearchScope scope = createMainClassScope();
        PsiClass aClass = PsiManager.getInstance(myProject).findClass(mainClass, scope);
        TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
        final TreeClassChooser dialog =
          factory.createNoInnerClassesScopeChooser(IdeBundle.message("jar.build.main.class.title"), scope, null, aClass);
        dialog.showDialog();
        final PsiClass psiClass = dialog.getSelectedClass();
        if (psiClass != null && psiClass.getQualifiedName() != null) {
          myMainClass.setText(psiClass.getQualifiedName());
        }
        updateWarning();
      }
    });
    myMainClass.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateWarning();
      }
    });

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Module element = myElementsChooser.getElementAt(0);
        myElementsChooser.selectElements(Collections.singletonList(element));
      }
    });
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);
  }

  private GlobalSearchScope createMainClassScope() {
    GlobalSearchScope result = null;
    for (Module module : mySettings.keySet()) {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(module);
      if (result == null) {
        result = scope;
      }
      else {
        result = result.uniteWith(scope);
      }
    }
    return result;
  }

  public JComponent getPreferredFocusedComponent() {
    return myJarPath;
  }

  private void setDefaultJarPath() {
    if (!Comparing.strEqual(myJarPath.getText(), "") || myCurrentModule == null) {
      return;
    }
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(myCurrentModule).getContentRoots();
    if (contentRoots.length == 0) return;
    VirtualFile contentRoot = contentRoots[0];
    if (contentRoot == null) return;
    VirtualFile moduleFile = myCurrentModule.getModuleFile();
    if (moduleFile == null) return;
    String jarPath = FileUtil.toSystemDependentName(contentRoot.getPath() + "/" + moduleFile.getNameWithoutExtension() + ".jar");
    myJarPath.setText(jarPath);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.j2ee.jar.BuildJarDialog";
  }

  public void dispose() {
    mySplitterProportionsData.saveSplitterProportions(myPanel);
    mySplitterProportionsData.externalizeToDimensionService(getDimensionKey());
    for (SettingsEditor editor : mySettings.values()) {
      editor.dispose();
    }
    super.dispose();
  }

  protected void doOKAction() {
    if (myCurrentModule != null) {
      saveEditor(myCurrentModule);
    }
    for (SettingsEditor editor : mySettings.values()) {
      editor.apply();
    }
    BuildJarProjectSettings.getInstance(myProject).setBuildJarOnMake(myBuildJarsOnMake.isSelected());
    super.doOKAction();
  }

  private void saveEditor(final Module module) {
    SettingsEditor settingsEditor = mySettings.get(module);
    if (settingsEditor != null) {
      settingsEditor.saveUI();
    }
  }

  private class SettingsEditor {
    private final Module myModule;
    private final BuildJarSettings myBuildJarSettings;
    private final BuildJarSettings myModifiedBuildJarSettings;
    private PackagingSettingsEditor myEditor;

    public SettingsEditor(Module module, BuildJarSettings buildJarSettings) {
      myModule = module;
      myBuildJarSettings = buildJarSettings;

      myModifiedBuildJarSettings = new BuildJarSettings(module);
      copySettings(buildJarSettings, myModifiedBuildJarSettings);

      myEditor = new PackagingSettingsEditor(module, myModifiedBuildJarSettings.getModuleContainer());
    }

    private void refreshControls() {
      myEditorPanel.removeAll();
      myEditorPanel.setLayout(new BorderLayout());
      myEditorPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
      myEditorPanel.revalidate();

      myJarPath.setText(FileUtil.toSystemDependentName(VfsUtil.urlToPath(myModifiedBuildJarSettings.getJarUrl())));
      myMainClass.setText(myModifiedBuildJarSettings.getMainClass());
    }

    public void dispose() {
      myEditor.disposeUIResources();
    }

    public void apply() {
      try {
        myEditor.apply();
      }
      catch (ConfigurationException e1) {
        //ignore
      }
      copySettings(myModifiedBuildJarSettings, myBuildJarSettings);
    }

    public void saveUI() {
      myEditor.saveData();
      String url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(myJarPath.getText()));
      myModifiedBuildJarSettings.setJarUrl(url);
      boolean isBuildJar = myElementsChooser.getMarkedElements().contains(myModule);
      myModifiedBuildJarSettings.setBuildJar(isBuildJar);
      myModifiedBuildJarSettings.setMainClass(myMainClass.getText());
    }


    public void checkSettings() throws RuntimeConfigurationException {
      myModifiedBuildJarSettings.checkSettings();
    }
  }

  private static void copySettings(BuildJarSettings from, BuildJarSettings to) {
    @NonNls Element element = new Element("dummy");
    try {
      from.writeExternal(element);
    }
    catch (WriteExternalException e) {
    }
    try {
      to.readExternal(element);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private static class ChooserElementProperties implements ElementsChooser.ElementProperties {
    private final Module myModule;

    public ChooserElementProperties(final Module module) {
      myModule = module;
    }

    public Icon getIcon() {
      return IconUtilEx.getIcon(myModule, 0);
    }

    public Color getColor() {
      return null;
    }
  }
}
