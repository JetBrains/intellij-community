package com.intellij.ide.util.importProject;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 16, 2007
 */
public class ModulesLayoutPanel extends ProjectLayoutPanel<ModuleDescriptor>{

  public ModulesLayoutPanel(ModuleInsight insight) {
    super(insight);
  }

  protected String getElementName(final ModuleDescriptor entry) {
    return entry.getName();
  }

  protected void setElementName(final ModuleDescriptor entry, final String name) {
    entry.setName(name);
  }

  protected List<ModuleDescriptor> getEntries() {
    final List<ModuleDescriptor> modules = getInsight().getSuggestedModules();
    return modules != null? modules : Collections.<ModuleDescriptor>emptyList();
  }

  protected Collection getDependencies(final ModuleDescriptor entry) {
    final List deps = new ArrayList();
    deps.addAll(entry.getDependencies());
    deps.addAll(getInsight().getLibraryDependencies(entry));
    return deps;
  }

  @Nullable
  protected ModuleDescriptor merge(final List<ModuleDescriptor> entries) {
    final ModuleInsight insight = getInsight();
    ModuleDescriptor mainDescr = null;
    for (ModuleDescriptor entry : entries) {
      if (mainDescr == null) {
        mainDescr = entry;
      }
      else {
        insight.merge(mainDescr, entry);
      }
    }
    return mainDescr;
  }

  protected ModuleDescriptor split(final ModuleDescriptor entry, final String newEntryName, final Collection<File> extractedData) {
    return getInsight().splitModule(entry, newEntryName, extractedData);
  }

  protected Collection<File> getContent(final ModuleDescriptor entry) {
    return entry.getContentRoots();
  }

  protected String getEntriesChooserTitle() {
    return "Modules";
  }

  protected String getDependenciesTitle() {
    return "Module dependencies";
  }

  protected String getSplitDialogTitle() {
    return "Split Module";
  }

  protected String getSplitDialogChooseFilesPrompt() {
    return "Select content roots to extract to the new module:";
  }

  protected String getNameAlreadyUsedMessage(final String name) {
    return "Module with name " + name + " already exists";
  }

  protected String getStepDescriptionText() {
    return "Please review suggested module structure for the project. At this stage you may set module names,\n" +
           "exclude particular modules from the project, merge or split individual modules.\n" +
           "All dependencies between the modules as well as dependencies on the libraries will be automatically updated.";
  }
}
