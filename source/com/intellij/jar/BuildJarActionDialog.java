package com.intellij.jar;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModuleChooserElement;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.GuiUtils;
import gnu.trove.THashMap;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
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
        }
        myCurrentModule = module;
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
