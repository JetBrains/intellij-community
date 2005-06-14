package com.intellij.jar;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModuleChooserElement;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.GuiUtils;
import gnu.trove.THashMap;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * @author cdr
 */
public class BuildJarActionDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.jar.BuildJarActionDialog");

  private final Project myProject;
  private JPanel myModulesPanel;
  private JPanel myEditorPanel;
  private TextFieldWithBrowseButton myJarPath;
  private JPanel myPanel;
  private PackagingSettingsEditor myEditor;
  private final Map<Module, SettingsEditor> mySettings = new THashMap<Module, SettingsEditor>();
  private Module myCurrentModule;
  private ElementsChooser<ModuleChooserElement> myElementsChooser;
  private JPanel myModuleSettingsPanel;

  protected BuildJarActionDialog(Project project) {
    super(true);
    myProject = project;

    setupControls();

    setTitle("Buld Jar");
    init();
  }

  private void setupControls() {
    myElementsChooser = new ElementsChooser<ModuleChooserElement>();
    myModulesPanel.setLayout(new BorderLayout());
    myModulesPanel.add(myElementsChooser, BorderLayout.CENTER);

    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
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
        fileChooser.addChoosableFileFilter(new TypeFilter(StdFileTypes.ARCHIVE));

        if (fileChooser.showSaveDialog(WindowManager.getInstance().suggestParentWindow(myProject)) != JFileChooser.APPROVE_OPTION) {
          return;
        }
        file = fileChooser.getSelectedFile();
        if (file == null) return;
        myJarPath.setText(file.getPath());
      }
      class TypeFilter extends FileFilter {
        private FileType myType;

        public TypeFilter(FileType fileType) {
          myType = fileType;
          myDescription = myType.getDescription();
        }

        public boolean accept(File f) {
          if (f.isDirectory()) return true;
          FileType type = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
          return myType == type;
        }

        public String getDescription() {
          return myDescription;
        }

        private String myDescription;
      }

    });

    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        ModuleChooserElement element = myElementsChooser.getElementAt(0);
        myElementsChooser.selectElements(Collections.singletonList(element));
      }
    });
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);
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
