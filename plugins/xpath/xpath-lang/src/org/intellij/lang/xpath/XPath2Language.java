/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public final class XPath2Language extends Language {
    public static final String ID = "XPath2";

    XPath2Language() {
        super(Language.findLanguageByID(XPathLanguage.ID), ID);
    }

  @Override
  public XPathFileType getAssociatedFileType() {
    return XPathFileType.XPATH2;
  }

  public static class XPathSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
    @NotNull
    protected SyntaxHighlighter createHighlighter() {
      return new XPathHighlighter(true);
    }
  }

  public static class XPath2Commenter implements Commenter  {
    @Override
    public String getLineCommentPrefix() {
      return null;
    }

    @Override
    public String getBlockCommentPrefix() {
      return "(:";
    }

    @Override
    public String getBlockCommentSuffix() {
      return ":)";
    }

    @Override
    public String getCommentedBlockCommentPrefix() {
      return getBlockCommentPrefix();
    }

    @Override
    public String getCommentedBlockCommentSuffix() {
      return getBlockCommentSuffix();
    }
  }
}
