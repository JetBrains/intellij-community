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

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.diagnostic.Logger;

class XPathInsertHandler implements InsertHandler {
    private static final Logger LOG = Logger.getInstance(XPathInsertHandler.class.getName());

    public void handleInsert(CompletionContext completionContext, int i, LookupData lookupData, LookupItem lookupItem, boolean selection, char c) {
        final Object object = lookupItem.getObject();
        LOG.debug("object = " + object);

        handleInsertImpl(completionContext, lookupData, lookupItem, c);

        final CharSequence charsSequence = completionContext.editor.getDocument().getCharsSequence();
        final CaretModel caretModel = completionContext.editor.getCaretModel();
        int offset = caretModel.getOffset();

        if (object instanceof Lookup) {
            final Lookup item = (Lookup)object;

            if (item.isFunction()) {
                if (charAt(charsSequence, offset) != '(') {
                    EditorModificationUtil.insertStringAtCaret(completionContext.editor, "()");
                    if (item.hasParameters()) {
                        caretModel.moveCaretRelatively(-1, 0, false, false, true);
                    }
                } else {
                    caretModel.moveCaretRelatively(1, 0, false, false, true);
                }
            } else if (item instanceof NamespaceLookup) {
                if (charAt(charsSequence, offset) != ':') {
                    EditorModificationUtil.insertStringAtCaret(completionContext.editor, ":");
                    return;
                }
            }
        }

        if (c == '\t') {
            if (charAt(charsSequence, offset) == ',') {
                offset++;
                caretModel.moveCaretRelatively(1, 0, false, false, true);
                while (charAt(charsSequence, offset++) == ' ') {
                    caretModel.moveCaretRelatively(1, 0, false, false, true);
                }
            } else if (isIdentifier(charAt(charsSequence, offset)) && isIdentifier(charAt(charsSequence, offset - 1))) {
                EditorModificationUtil.insertStringAtCaret(completionContext.editor, " ");
            } else if (charAt(charsSequence, offset) == ':') {
                caretModel.moveCaretRelatively(1, 0, false, false, true);
            }
        } 
    }

    private static char charAt(CharSequence charsSequence, int offset) {
        return charsSequence.length() > offset ? charsSequence.charAt(offset) : 0;
    }

    public void handleInsertImpl(CompletionContext context, LookupData data, LookupItem item, char c) {
//        final int selectionLength = (context.getSelectionEndOffset() - context.getStartOffset());
//        context.shiftOffsets(item.getLookupString().length() - data.prefix.length() - selectionLength);

        adjustIdentifierEnd(context, item);

        final int idEndOffset = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
        final boolean isOverwrite = c != 0
                ? c == com.intellij.codeInsight.lookup.Lookup.REPLACE_SELECT_CHAR
                : item.getAttribute(LookupItem.OVERWRITE_ON_AUTOCOMPLETE_ATTR) != null;
        if (idEndOffset != context.getSelectionEndOffset() && isOverwrite) {
            context.editor.getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
        }
    }

    private static void adjustIdentifierEnd(CompletionContext completioncontext, LookupItem item) {
        final boolean isNamespace = (item.getObject() instanceof NamespaceLookup);
        final CharSequence charsSequence = completioncontext.editor.getDocument().getCharsSequence();
        final int textLength = completioncontext.editor.getDocument().getTextLength();

        int x = completioncontext.getSelectionEndOffset();
        while (x < textLength) {
            final char c = charAt(charsSequence, x);
            if (isIdentifier(c) || (c == ':' && !isNamespace)) {
                x++;
            } else {
                break;
            }
        }
        completioncontext.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, x);
    }

    private static boolean isIdentifier(char c) {
        return Character.isLetter(c) || Character.isDigit(c) || c == '-' || c == '.' || c == '_';
    }
}
