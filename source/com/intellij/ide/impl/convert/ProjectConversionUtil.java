/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.convert.ui.ProjectConversionWizard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class ProjectConversionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.convert.ProjectConversionUtil");
  @NonNls private static final String PROJECT_FILES_BACKUP = "projectFilesBackup";

  private static boolean isConverted(Element versionComponent) {
    return Boolean.parseBoolean(versionComponent.getAttributeValue(ProjectFileVersionImpl.CONVERTED_ATTRIBUTE));
  }

  private static Element loadProjectFileRoot(String path) throws JDOMException, IOException {
    final Document document = JDOMUtil.loadDocument(new File(FileUtil.toSystemDependentName(path)));
    return document.getRootElement();
  }

  @NotNull
  public static ProjectConversionResult convertProject(String projectFilePath) {
    try {
      final Element root = loadProjectFileRoot(projectFilePath);

      final Element versionComponent = JDomConvertingUtil.findComponent(root, ProjectFileVersionImpl.COMPONENT_NAME);
      if (versionComponent != null && isConverted(versionComponent)) {
        return ProjectConversionResult.OK;
      }

      final ProjectConverter converter = getConverter(projectFilePath);
      if (converter == null) {
        return ProjectConversionResult.OK;
      }

      converter.prepare();
      if (!converter.isConversionNeeded()) {
        return ProjectConversionResult.OK;
      }

      if (versionComponent != null) {
        LOG.assertTrue(!isConverted(versionComponent));
        return new ProjectConversionResult(converter.createHelper());
      }

      String projectName = FileUtil.getNameWithoutExtension(new File(projectFilePath));
      ProjectConversionWizard wizard = new ProjectConversionWizard(converter, projectName);
      wizard.show();
      if (!wizard.isOK()) {
        return ProjectConversionResult.DO_NOT_OPEN;
      }

      if (wizard.isConverted()) {
        return ProjectConversionResult.OK;
      }

      return new ProjectConversionResult(converter.createHelper());
    }
    catch (IOException e) {
      Messages.showErrorDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                               IdeBundle.message("title.cannot.convert.project"));
      return ProjectConversionResult.DO_NOT_OPEN;
    }
    catch (JDOMException e) {
      Messages.showErrorDialog(IdeBundle.message("error.project.file.is.corrupted"),
                               IdeBundle.message("title.cannot.convert.project"));
      return ProjectConversionResult.DO_NOT_OPEN;
    }

  }

  @Nullable
  public static ProjectConverter getConverter(final String projectFilePath) {
    for (ConverterFactory converterFactory : Extensions.getExtensions(ConverterFactory.EXTENSION_POINT)) {
      final ProjectConverter converter = converterFactory.createConverter(projectFilePath);
      if (converter != null) {
        return converter;
      }
    }
    return null;
  }

  public static File backupFiles(final File[] files, final File parentDir) throws IOException {
    final String dirName = FileUtil.createSequentFileName(parentDir, PROJECT_FILES_BACKUP, "");
    File backupDir = new File(parentDir, dirName);
    backupDir.mkdirs();
    for (File file : files) {
      FileUtil.copy(file, new File(backupDir, file.getName()));
    }
    return backupDir;
  }

  public static class ProjectConversionResult {
    public static final ProjectConversionResult OK = new ProjectConversionResult(false, null);
    public static final ProjectConversionResult DO_NOT_OPEN = new ProjectConversionResult(true, null);

    private boolean myOpeningCancelled;
    private ProjectConversionHelper myConversionHelper;

    public ProjectConversionResult(ProjectConversionHelper helper) {
      this(false, helper);
    }

    private ProjectConversionResult(final boolean openingCancelled, final ProjectConversionHelper conversionHelper) {
      myOpeningCancelled = openingCancelled;
      myConversionHelper = conversionHelper;
    }

    public boolean isOpeningCancelled() {
      return myOpeningCancelled;
    }

    public ProjectConversionHelper getConversionHelper() {
      return myConversionHelper;
    }
  }
}
