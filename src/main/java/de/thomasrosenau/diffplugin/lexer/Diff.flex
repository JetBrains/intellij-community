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

package de.thomasrosenau.diffplugin;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static de.thomasrosenau.diffplugin.psi.DiffTypes.*;

%%

%public
%class DiffLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%state CONTEXT, UNIFIED, NORMAL

Newline = [\r\n]
InputCharacter = [^\r\n]
InputCharacters = {InputCharacter}+
Digits = [0-9]+
Range = {Digits} ("," {Digits})?

%%

{Newline} { return WHITE_SPACE; }

<YYINITIAL> ^ "*** " {InputCharacters} $ { yybegin(CONTEXT); return CONTEXT_FROM_LABEL; }
<CONTEXT> {
  ^ "***************" $ { return CONTEXT_HUNK_SEPARATOR; }
  ^ "*** " {Range} " ****" $ { return CONTEXT_FROM_LINE_NUMBERS; }
  ^ "--- " {Range} " ----" $ { return CONTEXT_TO_LINE_NUMBERS; }
  ^ "--- " {InputCharacters} $ { return CONTEXT_TO_LABEL; }
  ^ "! " {InputCharacters}? {Newline}? { return CONTEXT_CHANGED_LINE; }
  ^ "+ " {InputCharacters}? {Newline}? { return CONTEXT_INSERTED_LINE; }
  ^ "- " {InputCharacters}? {Newline}? { return CONTEXT_DELETED_LINE; }
  ^ "  " {InputCharacters}? {Newline}? { return CONTEXT_COMMON_LINE; }
}

<YYINITIAL,UNIFIED> ^ "--- " {InputCharacters} $ { yybegin(UNIFIED); return UNIFIED_FROM_LABEL; }
<UNIFIED> {
  ^ "+++ " {InputCharacters} $ { return UNIFIED_TO_LABEL; }
  ^ "@@ " {InputCharacters} " @@" (" " .+)? $ { return UNIFIED_LINE_NUMBERS; }
  ^ "+" {InputCharacters}? {Newline}? { return UNIFIED_INSERTED_LINE; }
  ^ "-" {InputCharacters}? {Newline}? { return UNIFIED_DELETED_LINE; }
  ^ " " {InputCharacters}? {Newline}? { return UNIFIED_COMMON_LINE; }
}

<YYINITIAL,NORMAL> {
  ^ {Digits} "a" {Range} $ { yybegin(NORMAL); return NORMAL_ADD_COMMAND; }
  ^ {Range} "c" {Range} $ { yybegin(NORMAL); return NORMAL_CHANGE_COMMAND; }
  ^ {Range} "d" {Digits} $ { yybegin(NORMAL); return NORMAL_DELETE_COMMAND; }
}
<NORMAL> {
  ^ ">" {InputCharacters}? {Newline}? { return NORMAL_TO_LINE; }
  ^ "<" {InputCharacters}? {Newline}? { return NORMAL_FROM_LINE; }
  ^ "---" $ { return NORMAL_SEPARATOR; }
}

^ "diff " {InputCharacters} $ { yybegin(YYINITIAL); return COMMAND; }

^ "\\" {InputCharacters} $ { return EOL_HINT; }

^ {InputCharacters} $ { return OTHER; }

[^] { return BAD_CHARACTER; } // this should never happen; debugging only
