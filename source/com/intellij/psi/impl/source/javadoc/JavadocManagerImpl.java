package com.intellij.psi.impl.source.javadoc;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.JavadocFormatterUtilHlper;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.javadoc.JavadocTagInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author mike
 */
public class JavadocManagerImpl implements JavadocManager {
  private final List<JavadocTagInfo> myInfos;

  static {
    FormatterUtil.addHelper(new JavadocFormatterUtilHlper());
  }

  public JavadocManagerImpl(Project project) {
    myInfos = new ArrayList<JavadocTagInfo>();

    myInfos.add(new SimpleDocTagInfo("author", PsiClass.class, false, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("deprecated", PsiElement.class, false, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("serialData", PsiMethod.class, false, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("serialField", PsiField.class, false, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("since", PsiElement.class, false, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("version", PsiClass.class, false, LanguageLevel.JDK_1_3));

    myInfos.add(new SimpleDocTagInfo("docRoot", PsiElement.class, true, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("inheritDoc", PsiElement.class, true, LanguageLevel.JDK_1_4));
    myInfos.add(new SimpleDocTagInfo("literal", PsiElement.class, true, LanguageLevel.JDK_1_5));
    myInfos.add(new SimpleDocTagInfo("code", PsiElement.class, true, LanguageLevel.JDK_1_5));

    //Not a standard tag, but added by IDEA for inspection suppression
    myInfos.add(new SimpleDocTagInfo("noinspection", PsiElement.class, false, LanguageLevel.JDK_1_3));

    myInfos.add(new ParamDocTagInfo());
    myInfos.add(new ReturnDocTagInfo());
    myInfos.add(new SerialDocTagInfo());
    myInfos.add(new SeeDocTagInfo("see", false));
    myInfos.add(new SeeDocTagInfo("link", true));
    myInfos.add(new SeeDocTagInfo("linkplain", true));
    myInfos.add(new ExceptionTagInfo("exception"));
    myInfos.add(new ExceptionTagInfo("throws"));
    myInfos.add(new ValueDocTagInfo());
    Collections.addAll(myInfos, Extensions.getExtensions(JavadocTagInfo.EP_NAME, project));
  }

  @Deprecated
  public void registerTagInfo(@NotNull JavadocTagInfo info) {
    myInfos.add(info);
  }

  @NotNull
  public JavadocTagInfo[] getTagInfos(PsiElement context) {
    List<JavadocTagInfo> result = new ArrayList<JavadocTagInfo>();

    for (JavadocTagInfo info : myInfos) {
      if (info.isValidInContext(context)) {
        result.add(info);
      }
    }

    return result.toArray(new JavadocTagInfo[result.size()]);
  }

  @Nullable
  public JavadocTagInfo getTagInfo(String name) {
    for (JavadocTagInfo info : myInfos) {
      if (info.getName().equals(name)) return info;
    }

    return null;
  }
}
