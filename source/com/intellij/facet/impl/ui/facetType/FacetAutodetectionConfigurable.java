package com.intellij.facet.impl.ui.facetType;

import com.intellij.facet.FacetType;
import com.intellij.facet.impl.autodetecting.DisabledAutodetectionByTypeElement;
import com.intellij.facet.impl.autodetecting.DisabledAutodetectionInModuleElement;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author nik
 */
public class FacetAutodetectionConfigurable implements Configurable {
  private final Project myProject;
  private final StructureConfigurableContext myContext;
  private FacetType<?, ?> myFacetType;
  private JPanel myMainPanel;
  private JCheckBox myEnableAutoDetectionCheckBox;
  private JList myModulesList;
  private JButton myAddModuleButton;
  private JButton myRemoveModuleButton;
  private JList myFilesList;
  private JButton myRemoveFileButton;
  private JPanel mySettingsPanel;
  private JLabel mySkipFilesLabel;
  private JPanel mySkipFilesListPanel;
  private DefaultListModel myModulesListModel;
  private DefaultListModel myFilesListModel;
  private BidirectionalMap<String, String> myFile2Module = new BidirectionalMap<String, String>();

  public FacetAutodetectionConfigurable(@NotNull Project project, final StructureConfigurableContext context, final @NotNull FacetType<?, ?> facetType) {
    myProject = project;
    myContext = context;
    myFacetType = facetType;

    myModulesList.setCellRenderer(new ModulesListCellRenderer());
    myModulesListModel = new DefaultListModel();
    myModulesList.setModel(myModulesListModel);

    myFilesList.setCellRenderer(new FilesListCellRenderer());
    myFilesListModel = new DefaultListModel();
    myFilesList.setModel(myFilesListModel);

    myAddModuleButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        String description = ProjectBundle.message("choose.description.facet.auto.detection.will.be.disabled.in.the.selected.modules",
                                                    myFacetType.getPresentableName());
        String title = ProjectBundle.message("choose.modules.dialog.title");
        ChooseModulesDialog dialog = new ChooseModulesDialog(myProject, getEnabledModules(), title, description);
        dialog.show();
        List<Module> chosenElements = dialog.getChosenElements();
        if (dialog.isOK() && !chosenElements.isEmpty()) {
          for (Module module : chosenElements) {
            String moduleName = module.getName();
            myModulesListModel.addElement(moduleName);
          }
          updateButtons();
          myModulesList.repaint();
        }
      }
    });

    myRemoveModuleButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        ListUtil.removeSelectedItems(myModulesList);
        updateButtons();
      }
    });

    myRemoveFileButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        ListUtil.removeSelectedItems(myFilesList);
        updateButtons();
      }
    });

    myEnableAutoDetectionCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateButtons();
      }
    });

    ListSelectionListener selectionListener = new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateRemoveButtons();
      }
    };
    myFilesList.addListSelectionListener(selectionListener);
    myModulesList.addListSelectionListener(selectionListener);
  }

  private List<Module> getEnabledModules() {
    List<Module> modules = new ArrayList<Module>(Arrays.asList(myContext.getModules()));
    Iterator<Module> iterator = modules.iterator();

    Set<String> disabled = getDisabledModules();

    while (iterator.hasNext()) {
      Module module = iterator.next();
      if (disabled.contains(module.getName())) {
        iterator.remove();
      }
    }
    return modules;
  }

  private Set<String> getDisabledModules() {
    Set<String> disabled = new LinkedHashSet<String>();
    for (int i = 0; i < myModulesListModel.getSize(); i++) {
      disabled.add((String)myModulesListModel.getElementAt(i));
    }
    return disabled;
  }

  public String getDisplayName() {
    return ProjectBundle.message("auto.detection.configurable.display.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    DisabledAutodetectionByTypeElement state = getAutodetectingManager().getDisabledAutodetectionState(myFacetType);
    return !Comparing.equal(state, createElement());
  }

  public void apply() throws ConfigurationException {
    getAutodetectingManager().setDisabledAutodetectionState(myFacetType, createElement());
  }

  @Nullable
  private DisabledAutodetectionByTypeElement createElement() {
    if (myEnableAutoDetectionCheckBox.isSelected() && myModulesListModel.isEmpty() && myFilesListModel.isEmpty()) {
      return null;
    }
    
    DisabledAutodetectionByTypeElement element = new DisabledAutodetectionByTypeElement(myFacetType.getStringId());
    if (myEnableAutoDetectionCheckBox.isSelected()) {
      Set<String> disabledModules = getDisabledModules();
      for (String moduleName : disabledModules) {
        element.getModuleElements().add(new DisabledAutodetectionInModuleElement(moduleName));
      }

      for (int i = 0; i < myFilesListModel.getSize(); i++) {
        String url = (String)myFilesListModel.getElementAt(i);
        String moduleName = myFile2Module.get(url);
        if (!disabledModules.contains(moduleName)) {
          DisabledAutodetectionInModuleElement moduleElement = element.findElement(moduleName);
          if (moduleElement != null) {
            moduleElement.getFiles().add(url);
          }
          else {
            element.getModuleElements().add(new DisabledAutodetectionInModuleElement(moduleName, url));
          }
        }
      }
    }
    return element;
  }

  public void reset() {
    DisabledAutodetectionByTypeElement autodetectionInfo = getAutodetectingManager().getDisabledAutodetectionState(myFacetType);
    myModulesListModel.removeAllElements();
    myFilesListModel.removeAllElements();
    myFile2Module.clear();

    myEnableAutoDetectionCheckBox.setSelected(true);
    if (autodetectionInfo != null) {
      List<DisabledAutodetectionInModuleElement> moduleElements = autodetectionInfo.getModuleElements();
      if (moduleElements.isEmpty()) {
        myEnableAutoDetectionCheckBox.setSelected(false);
      }
      else {
        for (DisabledAutodetectionInModuleElement moduleElement : moduleElements) {
          String moduleName = moduleElement.getModuleName();
          if (moduleElement.getFiles().isEmpty()) {
            myModulesListModel.addElement(moduleName);
          }
          else {
            for (String url : moduleElement.getFiles()) {
              myFile2Module.put(url, moduleName);
              myFilesListModel.addElement(url);
            }
          }
        }
      }
    }
    mySkipFilesLabel.setVisible(!myFilesListModel.isEmpty());
    mySkipFilesListPanel.setVisible(!myFilesListModel.isEmpty());
    myRemoveFileButton.setVisible(!myFilesListModel.isEmpty());
    updateButtons();
  }

  private void updateButtons() {
    if (!myEnableAutoDetectionCheckBox.isSelected()) {
      UIUtil.setEnabled(mySettingsPanel, false, true);
      return;
    }

    UIUtil.setEnabled(mySettingsPanel, true, true);
    myAddModuleButton.setEnabled(!getEnabledModules().isEmpty());
    updateRemoveButtons();
  }

  private void updateRemoveButtons() {
    myRemoveModuleButton.setEnabled(myEnableAutoDetectionCheckBox.isSelected() && myModulesList.getSelectedIndices().length > 0);
    myRemoveFileButton.setEnabled(myEnableAutoDetectionCheckBox.isSelected() && myFilesList.getSelectedIndices().length > 0);
  }


  private FacetAutodetectingManagerImpl getAutodetectingManager() {
    return (FacetAutodetectingManagerImpl)FacetAutodetectingManager.getInstance(myProject);
  }

  public void disposeUIResources() {
  }

  private static class FilesListCellRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(final JList list,
                                         final Object value,
                                         final int index,
                                         final boolean selected,
                                         final boolean hasFocus) {
      String url = (String)value;
      String path = VfsUtil.urlToPath(url);
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null) {
        append(path, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(file.getIcon());
      }
      else {
        append(path, SimpleTextAttributes.ERROR_ATTRIBUTES);
        setIcon(null);
      }
    }
  }

  private class ModulesListCellRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(final JList list,
                                         final Object value,
                                         final int index,
                                         final boolean selected,
                                         final boolean hasFocus) {
      String moduleName = (String)value;
      Module module = myContext.myModulesConfigurator.getModule(moduleName);
      if (module != null) {
        append(moduleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(module.getModuleType().getNodeIcon(false));
      }
      else {
        append(moduleName, SimpleTextAttributes.ERROR_ATTRIBUTES);
        setIcon(null);
      }
    }
  }
}
