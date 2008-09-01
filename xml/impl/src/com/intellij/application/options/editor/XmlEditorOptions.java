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
public class XmlEditorOptions implements PersistentStateComponent<XmlEditorOptions>, ExportableComponent {

  private boolean myBreadcrumbsEnabled = true;
  private boolean myShowCssColorPreviewInGutter = true;

  public static XmlEditorOptions getInstance() {
    return ServiceManager.getService(XmlEditorOptions.class);
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

  public XmlEditorOptions getState() {
    return this;
  }

  public void loadState(final XmlEditorOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
