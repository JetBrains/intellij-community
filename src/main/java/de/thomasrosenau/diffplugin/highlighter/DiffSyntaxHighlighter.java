/*
 Copyright 2019 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package de.thomasrosenau.diffplugin.highlighter;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import de.thomasrosenau.diffplugin.lexer.DiffLexerAdapter;
import de.thomasrosenau.diffplugin.psi.DiffTypes;
import org.jetbrains.annotations.NotNull;

public class DiffSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey COMMAND = createTextAttributesKey("PATCH_COMMAND",
            ConsoleViewContentType.USER_INPUT_KEY);
    public static final TextAttributesKey FILE = createTextAttributesKey("PATCH_FILEINFO",
            DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey ADDED = createTextAttributesKey("PATCH_ADDED",
            DiffColors.DIFF_INSERTED);
    public static final TextAttributesKey DELETED = createTextAttributesKey("PATCH_DELETED",
            DiffColors.DIFF_DELETED);
    public static final TextAttributesKey MODIFIED = createTextAttributesKey("PATCH_MODIFIED",
            DiffColors.DIFF_MODIFIED);
    public static final TextAttributesKey HUNK_HEAD = createTextAttributesKey("PATCH_HUNK_HEAD",
            DefaultLanguageHighlighterColors.LABEL);
    public static final TextAttributesKey SEPARATOR = createTextAttributesKey("PATCH_SEPARATOR",
            DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey EOLHINT = createTextAttributesKey("PATCH_EOLHINT",
            DefaultLanguageHighlighterColors.DOC_COMMENT);
    public static final TextAttributesKey TEXT = createTextAttributesKey("PATCH_TEXT",
            HighlighterColors.TEXT);

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new DiffLexerAdapter();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(DiffTypes.COMMAND)) {
            return pack(COMMAND);
        } else if (tokenType.equals(DiffTypes.FILE)) {
            return pack(FILE);
        } else if (tokenType.equals(DiffTypes.ADDED)) {
            return pack(ADDED);
        } else if (tokenType.equals(DiffTypes.DELETED)) {
            return pack(DELETED);
        } else if (tokenType.equals(DiffTypes.MODIFIED)) {
            return pack(MODIFIED);
        } else if (tokenType.equals(DiffTypes.HUNK_HEAD)) {
            return pack(HUNK_HEAD);
        } else if (tokenType.equals(DiffTypes.SEPARATOR)) {
            return pack(SEPARATOR);
        } else if (tokenType.equals(DiffTypes.EOLHINT)) {
            return pack(EOLHINT);
        } else {
            return pack(TEXT);
        }
    }
}
