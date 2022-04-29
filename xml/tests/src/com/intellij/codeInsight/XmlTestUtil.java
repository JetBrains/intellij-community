// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class XmlTestUtil {

  public static XmlTag tag(@NonNls String tagName, Project project) {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(project).createFileFromText("tag.xml", "<" + tagName + "/>");
    return file.getDocument().getRootTag();
  }

  public static XmlTag tag(@NonNls String tagName, @NonNls String namespace, Project project) {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(project)
      .createFileFromText("tag.xml", "<" + tagName + " xmlns=\"" + namespace + "\"/>");
    return file.getDocument().getRootTag();
  }

  @NotNull
  public static String getXmlTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData";
  }
}
