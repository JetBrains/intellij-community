/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.extensions.Extensions;
import org.jdom.JDOMException;
import org.jdom.Element;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.File;

/**
 * @author nik
 */
public class ProjectConversionUtil {
  @NonNls private static final String VERSION_ATTRIBUTE = "version";

  public static int getVersion(Element root) {
    try {
      return Integer.parseInt(root.getAttributeValue(VERSION_ATTRIBUTE));
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  private static Element loadProjectFileRoot(String path) throws JDOMException, IOException {
    final Document document = JDOMUtil.loadDocument(new File(FileUtil.toSystemDependentName(path)));
    return document.getRootElement();
  }

  public static boolean convertProject(String projectFilePath) {
    try {
      String projectName = FileUtil.getNameWithoutExtension(new File(projectFilePath));
      final Element root = loadProjectFileRoot(projectFilePath);
      final int version = getVersion(root);

      final ProjectConverter converter = getConverter(version, projectFilePath);
      if (converter == null) {
        return true;
      }

      converter.prepare();
      if (!converter.isConversionNeeded()) {
        return true;
      }

      /*
      final ConvertOrNotConvertDialog convertConfirmationDialog = new ConvertOrNotConvertDialog();
      convertConfirmationDialog.show();
      if (!convertConfirmationDialog.isOK()) {
        return false; 
      }
      */

      final ProjectConversionDialog dialog = new ProjectConversionDialog(projectName, converter);
      dialog.show();

      return dialog.isConverted();
    }
    catch (IOException e) {
      Messages.showErrorDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                               IdeBundle.message("title.cannot.convert.project"));
      return false;
    }
    catch (JDOMException e) {
      Messages.showErrorDialog(IdeBundle.message("error.project.file.is.corrupted"),
                               IdeBundle.message("title.cannot.convert.project"));
      return false;
    }

  }

  @Nullable
  private static ProjectConverter getConverter(final int version, final String projectFilePath) {
    for (ConverterFactory converterFactory : Extensions.getExtensions(ConverterFactory.EXTENSION_POINT)) {
      if (converterFactory.isApplicable(version)) {
        final ProjectConverter converter = converterFactory.createConverter(projectFilePath);
        if (converter != null) {
          return converter;
        }
      }
    }
    return null;
  }

}
