package com.intellij.jar;

import com.intellij.ide.RecentProjectsManager;
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
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModuleChooserElement;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.GuiUtils;
import com.intellij.util.io.FileTypeFilter;
import com.intellij.ide.ui.SplitterProportionsData;
import gnu.trove.THashMap;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;

/**
 * @author cdr
 */
public class BuildJarActionDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.jar.BuildJarActionDialog");

  private final Project myProject;
  private JPanel myModulesPanel;
  private JPanel myEditorPanel;
  private TextFieldWithBrowseButton myJarPath;
  private TextFieldWithBrowseButton myMainClass;
  private JPanel myPanel;
  private PackagingSettingsEditor myEditor;
  private final Map<Module, SettingsEditor> mySettings = new THashMap<Module, SettingsEditor>();
  private Module myCurrentModule;
  private ElementsChooser<ModuleChooserElement> myElementsChooser;
  private JPanel myModuleSettingsPanel;
  private LabeledComponent<TextFieldWithBrowseButton> myMainClassComponent;
  private LabeledComponent<TextFieldWithBrowseButton> myJarFilePathComponent;
  private final SplitterProportionsData mySplitterProportionsData = new SplitterProportionsData();

  protected BuildJarActionDialog(Project project) {
    super(true);
    myProject = project;

    setupControls();

    setTitle("Build Jars");
    init();
    mySplitterProportionsData.externalizeFromDimensionService(getDimensionKey());
    mySplitterProportionsData.restoreSplitterProportions(myPanel);
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
      if (module.getModuleType().isJ2EE()) continue;
      result.add(module);
    }
    Collections.sort(result, new Comparator<Module>() {
      public int compare(final Module o1, final Module o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    return result;

  }

  private void setupControls() {
    myJarPath = myJarFilePathComponent.getComponent();
    myMainClass = myMainClassComponent.getComponent();

    myElementsChooser = new ElementsChooser<ModuleChooserElement>();
    myModulesPanel.setLayout(new BorderLayout());
    myModulesPanel.add(myElementsChooser, BorderLayout.CENTER);

    final Collection<Module> modules = getModulesToJar(myProject);
    for (Module module : modules) {
      if (module.getModuleType().isJ2EE()) continue;
      ModuleChooserElement moduleChooserElement = new ModuleChooserElement(module, null);
      BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(module);
      myElementsChooser.addElement(moduleChooserElement, buildJarSettings.isBuildJar(), moduleChooserElement);
    }
    myElementsChooser.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (myCurrentModule != null) {
          applyEditor(myCurrentModule);
        }
        ModuleChooserElement selectedElement = myElementsChooser.getSelectedElement();
        Module module = selectedElement == null ? null : selectedElement.getModule();
        if (module != null) {
          BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(module);
          SettingsEditor settingsEditor = new SettingsEditor(module, buildJarSettings);
          mySettings.put(module, settingsEditor);
          boolean isBuildJar = myElementsChooser.getMarkedElements().contains(selectedElement);
          GuiUtils.enableChildren(myModuleSettingsPanel, isBuildJar, null);
        }
        myCurrentModule = module;
      }
    });
    myElementsChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<ModuleChooserElement>() {
      public void elementMarkChanged(final ModuleChooserElement element, final boolean isMarked) {
        GuiUtils.enableChildren(myModuleSettingsPanel, isMarked, null);
        if (isMarked) {
          setDefaultJarPath();
        }
      }
    });
    myJarPath.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String lastFilePath = myJarPath.getText();
        String path = lastFilePath != null ? lastFilePath : RecentProjectsManager.getInstance().getLastProjectPath();
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
        fileChooser.setDialogTitle("Save Jar File");
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
        final TreeClassChooser dialog = factory.createNoInnerClassesScopeChooser("Choose Main Class", scope, null, aClass);
        dialog.showDialog();
        final PsiClass psiClass = dialog.getSelectedClass();
        if (psiClass != null && psiClass.getQualifiedName() != null) {
          myMainClass.setText(psiClass.getQualifiedName());
        }
      }
    });

    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        ModuleChooserElement element = myElementsChooser.getElementAt(0);
        myElementsChooser.selectElements(Collections.singletonList(element));
      }
    });
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel,true);
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
    return "#com.intellij.j2ee.jar.BuildJarActionDialog";
  }

  protected void dispose() {
    mySplitterProportionsData.saveSplitterProportions(myPanel);
    mySplitterProportionsData.externalizeToDimensionService(getDimensionKey());
    super.dispose();
  }

  protected void doOKAction() {
    ModuleChooserElement selectedElement = myElementsChooser.getSelectedElement();
    if (selectedElement != null) {
      Module module = selectedElement.getModule();
      applyEditor(module);
    }
    super.doOKAction();
  }

  private void applyEditor(final Module module) {
    SettingsEditor settingsEditor = mySettings.get(module);
    if (settingsEditor != null) {
      settingsEditor.apply();
    }
  }

  private class SettingsEditor {
    private final Module myModule;
    private final BuildJarSettings myBuildJarSettings;
    private final BuildJarSettings myModifiedBuildJarSettings;

    public SettingsEditor(Module module, BuildJarSettings buildJarSettings) {
      myModule = module;
      myBuildJarSettings = buildJarSettings;

      myModifiedBuildJarSettings = new BuildJarSettings(module);
      copySettings(buildJarSettings, myModifiedBuildJarSettings);

      myEditor = new PackagingSettingsEditor(module, myModifiedBuildJarSettings.getModuleContainer());

      myEditorPanel.removeAll();
      myEditorPanel.setLayout(new BorderLayout());
      myEditorPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
      myEditorPanel.revalidate();

      myJarPath.setText(buildJarSettings.getJarPath());
      myMainClass.setText(buildJarSettings.getMainClass());
    }

    public void apply() {
      myEditor.saveData();
      try {
        myEditor.apply();
      }
      catch (ConfigurationException e1) {
        //ignore
      }
      myEditor.disposeUIResources();
      myModifiedBuildJarSettings.setJarPath(myJarPath.getText());
      boolean isBuildJar = myElementsChooser.getMarkedElements().contains(new ModuleChooserElement(myModule, null));
      myModifiedBuildJarSettings.setBuildJar(isBuildJar);
      myModifiedBuildJarSettings.setMainClass(myMainClass.getText());
      copySettings(myModifiedBuildJarSettings, myBuildJarSettings);
    }
  }

  private static void copySettings(BuildJarSettings from, BuildJarSettings to) {
    Element element = new Element("dummy");
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

}
