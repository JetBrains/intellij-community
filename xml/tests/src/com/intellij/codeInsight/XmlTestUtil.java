package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

public class XmlTestUtil {

  public static XmlTag tag(@NonNls String tagName, Project project) throws Exception {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(project).createFileFromText("tag.xml", "<" + tagName + "/>");
    return file.getDocument().getRootTag();
  }

  public static XmlTag tag(@NonNls String tagName, @NonNls String namespace, Project project) throws Exception {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(project)
      .createFileFromText("tag.xml", "<" + tagName + " xmlns=\"" + namespace + "\"/>");
    return file.getDocument().getRootTag();
  }

}
