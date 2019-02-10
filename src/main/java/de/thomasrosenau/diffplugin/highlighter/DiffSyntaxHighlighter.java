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
            DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey ADDED = createTextAttributesKey("PATCH_ADDED",
            DiffColors.DIFF_INSERTED);
    public static final TextAttributesKey DELETED = createTextAttributesKey("PATCH_DELETED",
            DiffColors.DIFF_DELETED);
    public static final TextAttributesKey HUNK_HEAD = createTextAttributesKey("PATCH_HUNK_HEAD",
            DefaultLanguageHighlighterColors.LINE_COMMENT);

    private static final TextAttributesKey[] COMMAND_KEYS = new TextAttributesKey[] {COMMAND};
    private static final TextAttributesKey[] ADDED_KEYS = new TextAttributesKey[] {ADDED};
    private static final TextAttributesKey[] DELETED_KEYS = new TextAttributesKey[] {DELETED};
    private static final TextAttributesKey[] HUNK_HEAD_KEYS = new TextAttributesKey[] {HUNK_HEAD};
    private static final TextAttributesKey[] TEXT_KEYS = new TextAttributesKey[] {HighlighterColors.TEXT};

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new DiffLexerAdapter();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(DiffTypes.COMMAND)) {
            return COMMAND_KEYS;
        } else if (tokenType.equals(DiffTypes.ADDED)) {
            return ADDED_KEYS;
        } else if (tokenType.equals(DiffTypes.DELETED)) {
            return DELETED_KEYS;
        } else if (tokenType.equals(DiffTypes.HUNK_HEAD)) {
            return HUNK_HEAD_KEYS;
        // TODO add more colors
        } else {
            return TEXT_KEYS;
        }
    }
}
