package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.utils.MavenFileTemplateGroupFactory;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class OpenOrCreateSettingsXmlAction extends MavenOpenOrCreateFilesAction {
  protected List<File> getFiles(AnActionEvent e) {
    File file = MavenActionUtil.getProjectsManager(e).getGeneralSettings().getEffectiveUserSettingsIoFile();
    return file != null ? Collections.singletonList(file) : Collections.EMPTY_LIST;
  }

  @Override
  protected String getFileTemplate() {
    return MavenFileTemplateGroupFactory.MAVEN_SETTINGS_XML_TEMPLATE;
  }
}
