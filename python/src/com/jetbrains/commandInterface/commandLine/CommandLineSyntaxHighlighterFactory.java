/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Highlights tokens based on lexer
 * @author Ilya.Kazakevich
 */
public final class CommandLineSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @NotNull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable final Project project,
                                                @Nullable final VirtualFile virtualFile) {
    return new CommandLineSyntaxHighlighter();
  }

  private static class CommandLineSyntaxHighlighter implements SyntaxHighlighter {
    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();
    public static final TextAttributesKey[] NO_ATTRS = new TextAttributesKey[0];

    static {
      ATTRIBUTES.put(CommandLineElementTypes.LITERAL_STARTS_FROM_LETTER,
                     TextAttributesKey.createTextAttributesKey("GNU.LETTER", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
      );
      ATTRIBUTES.put(CommandLineElementTypes.LITERAL_STARTS_FROM_DIGIT,
                     TextAttributesKey.createTextAttributesKey("GNU.NUMBER", DefaultLanguageHighlighterColors.NUMBER)
      );
      ATTRIBUTES.put(CommandLineElementTypes.SHORT_OPTION_NAME_TOKEN,
                     TextAttributesKey.createTextAttributesKey("GNU.SHORT_OPTION", DefaultLanguageHighlighterColors.INSTANCE_METHOD)

      );
      ATTRIBUTES.put(CommandLineElementTypes.LONG_OPTION_NAME_TOKEN,
                     TextAttributesKey.createTextAttributesKey("GNU.LONG_OPTION", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
      );
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
      return new FlexAdapter(new _CommandLineLexer());
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(final IElementType tokenType) {
      final TextAttributesKey attributesKey = ATTRIBUTES.get(tokenType);
      return (attributesKey == null ? NO_ATTRS : new TextAttributesKey[]{attributesKey});
    }
  }
}
