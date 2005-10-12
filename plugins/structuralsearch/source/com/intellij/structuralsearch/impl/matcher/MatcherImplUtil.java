package com.intellij.structuralsearch.impl.matcher;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 19, 2004
 * Time: 6:56:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class MatcherImplUtil {
  public static final Key<List<PsiCodeBlock>> UNMATCHED_CATCH_BLOCK_CONTENT_VAR_KEY = Key.create("UnmatchedCatchBlock");
  public static final Key<List<PsiParameter>> UNMATCHED_CATCH_PARAM_CONTENT_VAR_KEY = Key.create("UnmatchedCatchParam");

  public static void transform(MatchOptions options) {
    if (options.hasVariableConstraints()) return;
    PatternCompiler.transformOldPattern(options);
  }

  public static PsiElement[] createTreeFromText(String text, boolean file, FileType fileType, Project project) throws IncorrectOperationException {
    PsiElementFactory elementFactory = PsiManager.getInstance(project).getElementFactory();
    if (fileType == StdFileTypes.XML || fileType == StdFileTypes.HTML) {
      return ((XmlFile)elementFactory.createFileFromText("dummy." + fileType.getDefaultExtension(), "<QQQ>\n"+text+"\n</QQQ>"))
        .getDocument().getRootTag().getSubTags();
    } else {
      PsiElement element = (file)?
        (PsiElement)elementFactory.createFileFromText("__$$__.java",text):
        elementFactory.createStatementFromText("{\n"+ text + "\n}",null);

      PsiElement[] result;

      if (!file) {
        result = ((PsiBlockStatement)element).getCodeBlock().getChildren();
        if (result.length > 4) {
          PsiElement[] newresult = new PsiElement[result.length-4];
          System.arraycopy(result,2,newresult,0,result.length-4);
          result = newresult;
        } else {
          result = PsiElement.EMPTY_ARRAY;
        }
      } else {
        result = element.getChildren();
      }

      return result;
    }
  }
}
