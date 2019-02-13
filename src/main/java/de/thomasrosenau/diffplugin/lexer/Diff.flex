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
%state GitHead, DEFAULT

Newline = [\r\n]
InputCharacter = [^\r\n]
InputCharacters = {InputCharacter}+
Digits = [0-9]+
Range = {Digits} "," {Digits}

%%

<YYINITIAL> ^ "From "  {InputCharacters} $ { yybegin(GitHead); return GIT_HEAD; }

<GitHead> {
  ^ "diff " {InputCharacters} $ { yybegin(DEFAULT); return COMMAND; }
  ^ "---" {Newline} { return SEPARATOR; }
  // TODO detect email address etc
  ^ {InputCharacters} $ { return GIT_HEAD; }
}

{Newline} { return WHITE_SPACE; }

^ "diff " {InputCharacters} $ { yybegin(DEFAULT); return COMMAND; }

^ {Range} $ { yybegin(DEFAULT); return HUNK_HEAD; }
^ "--- " {Range} " ----" $ { yybegin(DEFAULT); return HUNK_HEAD; }
^ "*** " {Range} " ****" $ { yybegin(DEFAULT); return HUNK_HEAD; }
^ "@@" {InputCharacters} $ { yybegin(DEFAULT); return HUNK_HEAD; }

^ "*** " {InputCharacters} $ { yybegin(DEFAULT); return FILE; }
^ "--- " {InputCharacters} $ { yybegin(DEFAULT); return FILE; } // TODO: find out if first or second file (-u vs. -c)
^ "+++ " {InputCharacters} $ { yybegin(DEFAULT); return FILE; }

^ "--" ("-" | " ")? {Newline} { yybegin(DEFAULT); return SEPARATOR; }
^ "***************" {Newline} { yybegin(DEFAULT); return SEPARATOR; }

// TODO: handle EOF
^ [+>] {InputCharacters}? {Newline} { yybegin(DEFAULT); return ADDED; }
^ [-<] {InputCharacters}? {Newline} { yybegin(DEFAULT); return DELETED; }
^ "!" {InputCharacters} {Newline} { yybegin(DEFAULT); return MODIFIED; } // TODO: find out if added or deleted

^ "\\" {InputCharacters} $ { yybegin(DEFAULT); return EOLHINT; } // TODO: find out if added or deleted

^ {InputCharacters} $ { yybegin(DEFAULT); return OTHER; }

[^] { return BAD_CHARACTER; } // this should never happen; debugging only
