package org.jetbrains.postfixCompletion.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.templates.PostfixTemplate;

import java.io.File;
import java.util.Map;

@State(
  name = "PostfixCompletionSettings",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/postfixCompletion.xml")
  }
)
public class PostfixCompletionSettings implements PersistentStateComponent<PostfixCompletionSettings>, ExportableComponent {
  @NotNull private Map<String, Boolean> myTemplatesState = ContainerUtil.newHashMap();

  public boolean isTemplateEnabled(@NotNull PostfixTemplate template) {
    return ContainerUtil.getOrElse(myTemplatesState, template.getPresentableName(), true);
  }

  @NotNull
  public Map<String, Boolean> getTemplatesState() {
    return myTemplatesState;
  }

  public void setTemplatesState(@NotNull Map<String, Boolean> templatesState) {
    myTemplatesState = templatesState;
  }

  @Nullable
  public static PostfixCompletionSettings getInstance() {
    return ServiceManager.getService(PostfixCompletionSettings.class);
  }

  @Nullable
  @Override
  public PostfixCompletionSettings getState() {
    return this;
  }

  @Override
  public void loadState(PostfixCompletionSettings settings) {
    XmlSerializerUtil.copyBean(settings, this);
  }

  @NotNull
  @Override
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("postfixCompletion.xml")};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Postfix Completion";
  }
}
