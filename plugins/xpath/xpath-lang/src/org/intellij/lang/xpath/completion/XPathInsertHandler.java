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
package org.intellij.lang.xpath.completion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import org.jetbrains.annotations.NotNull;

final class XPathInsertHandler {
    private static final Logger LOG = Logger.getInstance(XPathInsertHandler.class.getName());

    static void handleInsert(@NotNull InsertionContext context, @NotNull AbstractLookup item) {
        final Object object = item.getObject();
        LOG.debug("object = " + object);

        handleInsertImpl(context, item, context.getCompletionChar());

      final Editor editor = context.getEditor();
      final CharSequence charsSequence = editor.getDocument().getCharsSequence();
        final CaretModel caretModel = editor.getCaretModel();
        int offset = caretModel.getOffset();

        if (item instanceof FunctionLookup) {
            if (charAt(charsSequence, offset) != '(') {
                EditorModificationUtil.insertStringAtCaret(editor, "()");
                if (((FunctionLookup)item).hasParameters()) {
                    caretModel.moveCaretRelatively(-1, 0, false, false, true);
                }
            } else {
                caretModel.moveCaretRelatively(1, 0, false, false, true);
            }
        } else if (item instanceof NamespaceLookup) {
            if (charAt(charsSequence, offset) != ':') {
                EditorModificationUtil.insertStringAtCaret(editor, ":");
                return;
            }
        }

        if (context.getCompletionChar() == '\t') {
            if (charAt(charsSequence, offset) == ',') {
                offset++;
                caretModel.moveCaretRelatively(1, 0, false, false, true);
                while (charAt(charsSequence, offset++) == ' ') {
                    caretModel.moveCaretRelatively(1, 0, false, false, true);
                }
            } else if (isIdentifier(charAt(charsSequence, offset)) && isIdentifier(charAt(charsSequence, offset - 1))) {
                EditorModificationUtil.insertStringAtCaret(editor, " ");
            } else if (charAt(charsSequence, offset) == ':') {
                caretModel.moveCaretRelatively(1, 0, false, false, true);
            }
        }
    }

    private static char charAt(CharSequence charsSequence, int offset) {
        return charsSequence.length() > offset ? charsSequence.charAt(offset) : 0;
    }

    public static void handleInsertImpl(InsertionContext context, LookupElement item, char c) {
//        final int selectionLength = (context.getSelectionEndOffset() - context.getStartOffset());
//        context.shiftOffsets(item.getLookupString().length() - data.prefix.length() - selectionLength);

        adjustIdentifierEnd(context, item);

        final int idEndOffset = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
        final boolean isOverwrite = c == Lookup.REPLACE_SELECT_CHAR;
        if (idEndOffset != context.getSelectionEndOffset() && isOverwrite) {
            context.getEditor().getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
        }
    }

    private static void adjustIdentifierEnd(InsertionContext context, LookupElement item) {
        final boolean isNamespace = (item.getObject() instanceof NamespaceLookup);
        final CharSequence charsSequence = context.getEditor().getDocument().getCharsSequence();
        final int textLength = context.getEditor().getDocument().getTextLength();

        int x = context.getSelectionEndOffset();
        while (x < textLength) {
            final char c = charAt(charsSequence, x);
            if (isIdentifier(c) || c == '*' || (c == ':' && !isNamespace)) {
                x++;
            } else {
                break;
            }
        }
        context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, x);
    }

    private static boolean isIdentifier(char c) {
        return Character.isLetter(c) || Character.isDigit(c) || c == '-' || c == '.' || c == '_' || c == '$';
    }
}
