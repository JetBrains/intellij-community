package com.intellij.ide.util.importProject;

import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 18, 2007
 */
public class ModulesDetectionStep extends AbstractStepWithProgress<List<ModuleDescriptor>> {
  private final ProjectFromSourcesBuilder myBuilder;
  private final ModuleInsight myInsight;
  private final Icon myIcon;
  private final String myHelpId;
  private ModulesLayoutPanel myModulesLayoutPanel;

  public ModulesDetectionStep(ProjectFromSourcesBuilder builder, final ModuleInsight insight, Icon icon, @NonNls String helpId) {
    super("Stop module analysis?");
    myBuilder = builder;
    myInsight = insight;
    myIcon = icon;
    myHelpId = helpId;
  }

  public void updateDataModel() {
    myBuilder.setModules(myModulesLayoutPanel.getChosenEntries());
  }

  protected JComponent createResultsPanel() {
    myModulesLayoutPanel = new ModulesLayoutPanel(myInsight);
    return myModulesLayoutPanel;
  }

  protected String getProgressText() {
    return "Searching for modules. Please wait.";
  }

  int myPreviousStateHashCode = -1;
  protected boolean shouldRunProgress() {
    final int currentHash = calcStateHashCode();
    try {
      return currentHash != myPreviousStateHashCode;
    }
    finally {
      myPreviousStateHashCode = currentHash;
    }
  }

  private int calcStateHashCode() {
    int hash = myBuilder.getContentEntryPath().hashCode();
    for (Pair<String, String> pair : myBuilder.getSourcePaths()) {
      hash = 31 * hash + pair.getFirst().hashCode();
      hash = 31 * hash + pair.getSecond().hashCode();
    }
    final List<LibraryDescriptor> libs = myBuilder.getLibraries();
    for (LibraryDescriptor lib : libs) {
      final Collection<File> files = lib.getJars();
      for (File file : files) {
        hash = 31 * hash + file.hashCode();
      }
    }
    return hash;
  }

  protected List<ModuleDescriptor> calculate() {
    myInsight.scanModules();
    return myInsight.getSuggestedModules();
  }

  protected void onFinished(final List<ModuleDescriptor> moduleDescriptors, final boolean canceled) {
    myModulesLayoutPanel.rebuild();
  }

  @NonNls
  public String getHelpId() {
    return myHelpId;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
