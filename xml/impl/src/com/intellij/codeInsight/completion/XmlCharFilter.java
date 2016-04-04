/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:15:07 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.editorActions.XmlAutoPopupHandler;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

public class XmlCharFilter extends CharFilter {

  public static boolean isInXmlContext(Lookup lookup) {
    if (!lookup.isCompletion()) return false;

    PsiElement psiElement = lookup.getPsiElement();
    PsiFile file = lookup.getPsiFile();
    if (!(file instanceof XmlFile) && psiElement != null) {
      file = psiElement.getContainingFile();
    }


    if (file instanceof XmlFile) {
      if (psiElement != null) {
        PsiElement elementToTest = psiElement;
        if (elementToTest instanceof PsiWhiteSpace) {
          elementToTest = elementToTest.getParent(); // JSPX has whitespace with language Java
        }

        final Language language = elementToTest.getLanguage();
        if (!(language instanceof XMLLanguage)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public static boolean isWithinTag(Lookup lookup) {
    if (isInXmlContext(lookup)) {
      PsiElement psiElement = lookup.getPsiElement();
      final PsiElement parentElement = psiElement != null ? psiElement.getParent() : null;
      if (parentElement instanceof XmlTag) return true;
      if (parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof XmlDocument) return true;

      return (parentElement instanceof XmlDocument || parentElement instanceof XmlText) &&
             (psiElement.textMatches("<") || psiElement.textMatches("\""));
    }
    return false;
  }

  @Override
  public Result acceptChar(char c, final int prefixLength, final Lookup lookup) {
    if (!isInXmlContext(lookup)) return null;

    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    switch(c){
      case '-':
      case ':':
      case '?':
        return Result.ADD_TO_PREFIX;
      case '/':
        if (isWithinTag(lookup)) {
          if (prefixLength > 0) {
            return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
          }
          XmlAutoPopupHandler.autoPopupXmlLookup(lookup.getProject(), lookup.getEditor());
          return Result.HIDE_LOOKUP;
        }
        return Result.ADD_TO_PREFIX;

      case '>': if (prefixLength > 0) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }

      case '\'':
      case '\"':
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      default:
        return null;
    }
  }
}
