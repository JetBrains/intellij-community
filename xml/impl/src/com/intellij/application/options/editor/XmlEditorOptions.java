package com.intellij.application.options.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.xml.XmlBundle;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author spleaner
 */
public class XmlEditorOptions implements NamedJDOMExternalizable, ExportableApplicationComponent {

  private boolean myBreadcrumbsEnabled = true;

  public static XmlEditorOptions getInstance() {
    return ApplicationManager.getApplication().getComponent(XmlEditorOptions.class);
  }

  public String getExternalFileName() {
    return "editor.xml";
  }

  public void setBreadcrumbsEnabled(boolean b) {
    myBreadcrumbsEnabled = b;
  }

  public boolean isBreadcrumbsEnabled() {
    return myBreadcrumbsEnabled;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    Element settingsElement = element.getChild(XmlEditorOptions.class.getSimpleName());
    if (settingsElement != null) {
      XmlSerializer.deserializeInto(this, settingsElement);
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    element.addContent(XmlSerializer.serialize(this));
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return XmlBundle.message("xml.options");
  }

  @NotNull
  public String getComponentName() {
    return "XmlEditorOptions";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
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
}
