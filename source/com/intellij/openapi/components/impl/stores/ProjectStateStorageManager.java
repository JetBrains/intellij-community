package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

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
    super(macroSubstitutor, "project");
    myMacroSubstitutor = macroSubstitutor;
    myProject = project;
  }

  @Override
  public synchronized void save() throws StateStorage.StateStorageException, IOException {
    final StateStorage defaultStateStorage = getFileStateStorage(ProjectStoreImpl.DEFAULT_STATE_STORAGE);
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

      element.removeChildren(USED_MACROS_ELEMENT_NAME);

      if (!usedMacros.isEmpty()) {

        Element usedMacrosElement = new Element(USED_MACROS_ELEMENT_NAME);

        for (String usedMacro : usedMacros) {
          Element macroElement = new Element(ELEMENT_MACRO);

          macroElement.setAttribute(NAME_ATTR, usedMacro);

          usedMacrosElement.addContent(macroElement);
        }

        element.addContent(usedMacrosElement);
      }
    }

    super.save();
    myMacroSubstitutor.reset();
  }
}
