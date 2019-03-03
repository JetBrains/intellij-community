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

class DiffSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey COMMAND = createTextAttributesKey("PATCH_COMMAND",
            ConsoleViewContentType.USER_INPUT_KEY);
    public static final TextAttributesKey FILE = createTextAttributesKey("PATCH_FILENAME",
            DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey INSERTED = createTextAttributesKey("PATCH_INSERTED",
            DiffColors.DIFF_INSERTED);
    public static final TextAttributesKey DELETED = createTextAttributesKey("PATCH_DELETED",
            DiffColors.DIFF_DELETED);
    public static final TextAttributesKey CHANGED = createTextAttributesKey("PATCH_CHANGED",
            DiffColors.DIFF_MODIFIED);
    public static final TextAttributesKey HUNK_HEAD = createTextAttributesKey("PATCH_HUNK_HEAD",
            DefaultLanguageHighlighterColors.LABEL);
    public static final TextAttributesKey SEPARATOR = createTextAttributesKey("PATCH_SEPARATOR",
            DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey EOL_HINT = createTextAttributesKey("PATCH_EOL_HINT",
            DefaultLanguageHighlighterColors.DOC_COMMENT);
    public static final TextAttributesKey TEXT = createTextAttributesKey("PATCH_TEXT",
            HighlighterColors.TEXT);

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new DiffLexerAdapter();
    }

    private boolean isChangedLine(IElementType tokenType) {
        return tokenType.equals(DiffTypes.CONTEXT_CHANGED_LINE);
    }

    private boolean isInsertedLine(IElementType tokenType) {
        return tokenType.equals(DiffTypes.CONTEXT_INSERTED_LINE) ||
                tokenType.equals(DiffTypes.UNIFIED_INSERTED_LINE) ||
                tokenType.equals(DiffTypes.NORMAL_TO_LINE);
    }

    private boolean isDeletedLine(IElementType tokenType) {
        return tokenType.equals(DiffTypes.CONTEXT_DELETED_LINE) ||
                tokenType.equals(DiffTypes.UNIFIED_DELETED_LINE) ||
                tokenType.equals(DiffTypes.NORMAL_FROM_LINE);
    }

    private boolean isHunkHead(IElementType tokenType) {
        return tokenType.equals(DiffTypes.CONTEXT_FROM_LINE_NUMBERS) ||
                tokenType.equals(DiffTypes.CONTEXT_TO_LINE_NUMBERS) ||
                tokenType.equals(DiffTypes.UNIFIED_LINE_NUMBERS) ||
                tokenType.equals(DiffTypes.NORMAL_ADD_COMMAND) ||
                tokenType.equals(DiffTypes.NORMAL_DELETE_COMMAND) ||
                tokenType.equals(DiffTypes.NORMAL_CHANGE_COMMAND);
    }

    private boolean isSeparator(IElementType tokenType) {
        return tokenType.equals(DiffTypes.CONTEXT_HUNK_SEPARATOR) ||
                tokenType.equals(DiffTypes.NORMAL_SEPARATOR);
    }

    private boolean isFileName(IElementType tokenType) {
        return tokenType.equals(DiffTypes.CONTEXT_FROM_LABEL) ||
                 tokenType.equals(DiffTypes.CONTEXT_TO_LABEL) ||
                 tokenType.equals(DiffTypes.UNIFIED_FROM_LABEL) ||
                 tokenType.equals(DiffTypes.UNIFIED_TO_LABEL);
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(DiffTypes.COMMAND)) {
            return pack(COMMAND);
        } else if (isChangedLine(tokenType)) {
            return pack(CHANGED);
        } else if (isInsertedLine(tokenType)) {
            return pack(INSERTED);
        } else if (isDeletedLine(tokenType)) {
            return pack(DELETED);
        } else if (isHunkHead(tokenType)) {
            return pack(HUNK_HEAD);
        } else if (isSeparator(tokenType)) {
            return pack(SEPARATOR);
        } else if (isFileName(tokenType)) {
            return pack(FILE);
        } else if (tokenType.equals(DiffTypes.EOL_HINT)) {
            return pack(EOL_HINT);
        } else {
            return pack(TEXT);
        }
    }
}
