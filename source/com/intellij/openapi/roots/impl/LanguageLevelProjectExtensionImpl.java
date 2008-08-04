/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class LanguageLevelProjectExtensionImpl extends LanguageLevelProjectExtension {
  @NonNls private static final String ASSERT_KEYWORD_ATTR = "assert-keyword";
  @NonNls private static final String JDK_15_ATTR = "jdk-15";

  private LanguageLevel myLanguageLevel = LanguageLevel.JDK_1_5;
  private LanguageLevel myOriginalLanguageLevel = myLanguageLevel;

  private Project myProject;
  private Runnable myReloadProjectRequest;

  public LanguageLevelProjectExtensionImpl(final Project project) {
    myProject = project;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    if (myProject.isDefault()) {
      return; // TODO[max]: hack to enforce default project always has LangLevel == 1.5. This is necessary until StubUpdatingIndex
      // is able to determine correct language level for stub parsing.
    }

    final boolean assertKeyword = Boolean.valueOf(element.getAttributeValue(ASSERT_KEYWORD_ATTR)).booleanValue();
    final boolean jdk15 = Boolean.valueOf(element.getAttributeValue(JDK_15_ATTR)).booleanValue();
    if (jdk15) {
      myLanguageLevel = LanguageLevel.JDK_1_5;
    }
    else if (assertKeyword) {
      myLanguageLevel = LanguageLevel.JDK_1_4;
    }
    else {
      myLanguageLevel = LanguageLevel.JDK_1_3;
    }
    myOriginalLanguageLevel = myLanguageLevel;
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    final boolean is14 = LanguageLevel.JDK_1_4.equals(myLanguageLevel);
    final boolean is15 = LanguageLevel.JDK_1_5.equals(myLanguageLevel);
    element.setAttribute(ASSERT_KEYWORD_ATTR, Boolean.toString(is14 || is15));
    element.setAttribute(JDK_15_ATTR, Boolean.toString(is15));
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void setLanguageLevel(LanguageLevel languageLevel) {
    if (myLanguageLevel != languageLevel) {
      reloadProjectOnLanguageLevelChange(languageLevel, false);
    }
    myLanguageLevel = languageLevel;
  }

  public void reloadProjectOnLanguageLevelChange(final LanguageLevel languageLevel, final boolean forceReload) {
    if (myProject.isOpen() && !ApplicationManager.getApplication().isUnitTestMode()) {
      myReloadProjectRequest = new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          if (myReloadProjectRequest != this) {
            // obsolete, another request has already replaced this one
            return;
          }
          if (!forceReload && myOriginalLanguageLevel.equals(getLanguageLevel())) {
            // the question does not make sense now
            return;
          }
          final String _message = ProjectBundle.message("project.language.level.reload.prompt", myProject.getName());
          if (Messages.showYesNoDialog(myProject, _message, ProjectBundle.message("project.language.level.reload.title"), Messages.getQuestionIcon()) == 0) {
            ProjectManager.getInstance().reloadProject(myProject);
          }
          myReloadProjectRequest = null;
        }
      };
      ApplicationManager.getApplication().invokeLater(myReloadProjectRequest, ModalityState.NON_MODAL);
    }
    else {
      // if the project is not open, reset the original level to the same value as mylanguageLevel has
      myOriginalLanguageLevel = languageLevel;
    }
  }

  public static class MyProjectExtension extends ProjectExtension {
    private final Project myProject;

    public MyProjectExtension(final Project project) {
      myProject = project;
    }

    public void readExternal(final Element element) throws InvalidDataException {
      ((LanguageLevelProjectExtensionImpl)getInstance(myProject)).readExternal(element);
    }

    public void writeExternal(final Element element) throws WriteExternalException {
      ((LanguageLevelProjectExtensionImpl)getInstance(myProject)).writeExternal(element);
    }
  }
}