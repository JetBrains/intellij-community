package com.intellij.psi.impl.source.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.javadoc.JavadocTagInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class JavadocManagerImpl implements JavadocManager {
  private JavadocTagInfo[] myInfos;

  public JavadocManagerImpl() {
    List infos = new ArrayList();

    infos.add(new SimpleDocTagInfo("author", PsiClass.class, false, LanguageLevel.JDK_1_3));
    infos.add(new SimpleDocTagInfo("deprecated", PsiElement.class, false, LanguageLevel.JDK_1_3));
    infos.add(new SimpleDocTagInfo("serialData", PsiMethod.class, false, LanguageLevel.JDK_1_3));
    infos.add(new SimpleDocTagInfo("serialField", PsiField.class, false, LanguageLevel.JDK_1_3));
    infos.add(new SimpleDocTagInfo("since", PsiElement.class, false, LanguageLevel.JDK_1_3));
    infos.add(new SimpleDocTagInfo("version", PsiClass.class, false, LanguageLevel.JDK_1_3));

    infos.add(new SimpleDocTagInfo("docRoot", PsiElement.class, true, LanguageLevel.JDK_1_3));
    infos.add(new SimpleDocTagInfo("inheritDoc", PsiElement.class, true, LanguageLevel.JDK_1_4));
    infos.add(new SimpleDocTagInfo("value", PsiElement.class, true, LanguageLevel.JDK_1_4));
    infos.add(new SimpleDocTagInfo("literal", PsiElement.class, true, LanguageLevel.JDK_1_5));
    infos.add(new SimpleDocTagInfo("code", PsiElement.class, true, LanguageLevel.JDK_1_5));

    infos.add(new ParamDocTagInfo());
    infos.add(new ReturnDocTagInfo());
    infos.add(new SerialDocTagInfo());
    infos.add(new SeeDocTagInfo("see", false));
    infos.add(new SeeDocTagInfo("link", true));
    infos.add(new SeeDocTagInfo("linkplain", true));
    infos.add(new ExceptionTagInfo("exception"));
    infos.add(new ExceptionTagInfo("throws"));

    myInfos = (JavadocTagInfo[])infos.toArray(new JavadocTagInfo[infos.size()]);
  }

  public JavadocTagInfo[] getTagInfos(PsiElement context) {
    List result = new ArrayList();

    for (int i = 0; i < myInfos.length; i++) {
      JavadocTagInfo info = myInfos[i];
      if (info.isValidInContext(context)) result.add(info);
    }

    return (JavadocTagInfo[])result.toArray(new JavadocTagInfo[result.size()]);
  }

  public JavadocTagInfo getTagInfo(String name) {
    for (int i = 0; i < myInfos.length; i++) {
      JavadocTagInfo info = myInfos[i];
      if (info.getName().equals(name)) return info;
    }

    return null;
  }
}
