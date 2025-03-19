// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.configuration.browser;

import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.theoryinpractice.testng.TestngBundle;

/**
 * @author Hani Suleiman
 */
public class SuiteBrowser extends BrowseModuleValueActionListener {
  public SuiteBrowser(Project project) {
    super(project);
  }

  @Override
  public String showDialog() {
    var descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", "XML|YAML"), "xml", "yaml")
      .withTitle(TestngBundle.message("testng.suite.browser.select.suite"))
      .withDescription((TestngBundle.message("testng.suite.browser.select.xml.or.yaml.suite.file")));
    var file = FileChooser.chooseFile(descriptor, getProject(), null);
    return file != null ? file.getPath() : null;
  }
}
