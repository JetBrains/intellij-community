package com.intellij.javaee;

import com.intellij.conversion.*;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene.Kudelevsky
 */
public class DefaultHtmlDoctypeConverter extends ConverterProvider {
  protected DefaultHtmlDoctypeConverter() {
    super("default-html-language-level");
  }

  @NotNull
  @Override
  public String getConversionDescription() {
    return "Default HTML language level setting will be updated";
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new MyConverter(context);
  }

  private static class MyConverter extends ProjectConverter {
    private final ConversionContext myContext;

    private MyConverter(@NotNull ConversionContext context) {
      myContext = context;
    }

    @Override
    public boolean isConversionNeeded() {
      return getElementToUpdate() != null;
    }

    @Override
    public void preProcessingFinished() throws CannotConvertException {
      final Element defaultHtmlDoctype = getElementToUpdate();

      if (defaultHtmlDoctype != null) {
        defaultHtmlDoctype.setText(ExternalResourceManagerImpl.HTML5_DOCTYPE_ELEMENT);
      }
    }

    @Nullable
    private Element getElementToUpdate() {
      final ComponentManagerSettings settings = myContext.getProjectRootManagerSettings();
      if (settings == null) {
        return null;
      }

      final Element root = settings.getComponentElement("ProjectResources");
      if (root == null) {
        return null;
      }

      Element defaultHtmlDoctype = root.getChild("default-html-language-level");
      if (defaultHtmlDoctype == null) {
        return null;
      }

      String value = defaultHtmlDoctype.getTextTrim();
      value = value != null ? myContext.expandPath(value) : null;
      if (value == null) {
        return null;
      }

      if (!FileUtil.toSystemIndependentName(value).endsWith("idea.jar!/resources/html5-schema/html5.rnc")) {
        return null;
      }
      return defaultHtmlDoctype;
    }

    @Override
    public Collection<File> getAdditionalAffectedFiles() {
      final ComponentManagerSettings settings = myContext.getProjectRootManagerSettings();
      final File file = settings != null ? settings.getFile() : null;
      return file != null
             ? Collections.singletonList(file)
             : Collections.<File>emptyList();
    }
  }
}
