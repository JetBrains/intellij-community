package com.intellij.application.options.editor;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author spleaner
 */
@State(
  name="XmlEditorOptions",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/editor.xml"
    )}
)
public class WebEditorOptions implements PersistentStateComponent<WebEditorOptions>, ExportableComponent {

  private boolean myBreadcrumbsEnabled = true;
  private boolean myShowCssColorPreviewInGutter = true;
  private boolean myAutomaticallyInsertClosingTag = true;
  private boolean myAutomaticallyInsertRequiredAttributes = true;
  private boolean myAutomaticallyStartAttribute = true;

  public static WebEditorOptions getInstance() {
    return ServiceManager.getService(WebEditorOptions.class);
  }

  public void setBreadcrumbsEnabled(boolean b) {
    myBreadcrumbsEnabled = b;
  }

  public boolean isBreadcrumbsEnabled() {
    return myBreadcrumbsEnabled;
  }

  public boolean isShowCssColorPreviewInGutter() {
    return myShowCssColorPreviewInGutter;
  }

  public void setShowCssColorPreviewInGutter(final boolean showCssColorPreviewInGutter) {
    myShowCssColorPreviewInGutter = showCssColorPreviewInGutter;
  }

  public boolean isAutomaticallyInsertClosingTag() {
    return myAutomaticallyInsertClosingTag;
  }

  public void setAutomaticallyInsertClosingTag(final boolean automaticallyInsertClosingTag) {
    myAutomaticallyInsertClosingTag = automaticallyInsertClosingTag;
  }

  public boolean isAutomaticallyInsertRequiredAttributes() {
    return myAutomaticallyInsertRequiredAttributes;
  }

  public void setAutomaticallyInsertRequiredAttributes(final boolean automaticallyInsertRequiredAttributes) {
    myAutomaticallyInsertRequiredAttributes = automaticallyInsertRequiredAttributes;
  }

  public boolean isAutomaticallyStartAttribute() {
    return myAutomaticallyStartAttribute;
  }

  public void setAutomaticallyStartAttribute(final boolean automaticallyStartAttribute) {
    myAutomaticallyStartAttribute = automaticallyStartAttribute;
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("editor")};
  }

  @NotNull
  public String getPresentableName() {
    return XmlBundle.message("xml.options");
  }

  @Nullable
  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public WebEditorOptions getState() {
    return this;
  }

  public void loadState(final WebEditorOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
