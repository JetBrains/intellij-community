package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

class ProjectStateStorageManager extends StateStorageManager {
  private TrackingPathMacroSubstitutor myMacroSubstitutor;
  private Project myProject;
  @NonNls private static final String NAME_ATTR = "name";
  @NonNls public static final String USED_MACROS_ELEMENT_NAME = "UsedPathMacros";
  @NonNls public static final String ELEMENT_MACRO = "macro";

  public ProjectStateStorageManager(final TrackingPathMacroSubstitutor macroSubstitutor, Project project) {
    super(macroSubstitutor);
    myMacroSubstitutor = macroSubstitutor;
    myProject = project;
  }

  @Override
  public synchronized void save() throws StateStorage.StateStorageException, IOException {
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ((ModuleImpl)module).getStateStore().save();
    }

    final StateStorage defaultStateStorage = getStateStorage(ProjectStoreImpl.DEFAULT_STATE_STORAGE);
    final Element element = ((XmlElementStorage)defaultStateStorage).getRootElement();

    if (element != null) {
      final Collection<String> usedMacros = new ArrayList<String>(myMacroSubstitutor.getUsedMacros());

      final PathMacros pathMacros = PathMacros.getInstance();

      for (Iterator<String> i = usedMacros.iterator(); i.hasNext();) {
        String macro = i.next();

        final Set<String> systemMacroNames = pathMacros.getSystemMacroNames();
        for (String systemMacroName : systemMacroNames) {
          if (macro.equals(systemMacroName) || macro.indexOf("$" + systemMacroName + "$") >= 0) {
            i.remove();
          }
        }
      }

      if (!usedMacros.isEmpty()) {
        Element usedMacrosElement = element.getOwnerDocument().createElement(USED_MACROS_ELEMENT_NAME);

        for (String usedMacro : usedMacros) {
          Element macroElement = element.getOwnerDocument().createElement(ELEMENT_MACRO);

          macroElement.setAttribute(NAME_ATTR, usedMacro);

          usedMacrosElement.appendChild(macroElement);
        }

        element.appendChild(usedMacrosElement);
      }
    }

    super.save();
    myMacroSubstitutor.reset();
  }
}
