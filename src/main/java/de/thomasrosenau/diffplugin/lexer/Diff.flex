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

Newline = [\r\n]
InputCharacter = [^\r\n]
InputCharacters = {InputCharacter}+
Digits = [0-9]+
Range = {Digits} "," {Digits}

%%
<YYINITIAL> {
  ^ "diff " {InputCharacters} $ { return COMMAND; }

  ^ {Range} $ { return HUNK_HEAD; }
  ^ "--- " {Range} " ----" $ { return HUNK_HEAD; }
  ^ "*** " {Range} " ****" $ { return HUNK_HEAD; }
  ^ "@@" {InputCharacters} $ { return HUNK_HEAD; }

  ^ "*** " {InputCharacters} $ { return FILE; }
  ^ "--- " {InputCharacters} $ { return FILE; } // TODO: find out if first or second file (-u vs. -c)
  ^ "+++ " {InputCharacters} $ { return FILE; }

  ^ "--" ("-" | " ")? {Newline} { return SEPARATOR; }
  ^ "***************" {Newline} { return SEPARATOR; }

  // TODO: handle EOF
  ^ [+>] {InputCharacters}? {Newline} { return ADDED; }
  ^ [-<] {InputCharacters}? {Newline} { return DELETED; }
  ^ "!" {InputCharacters} {Newline} { return MODIFIED; } // TODO: find out if added or deleted

  ^ "\\" {InputCharacters} $ { return EOLHINT; } // TODO: find out if added or deleted

  ^ {InputCharacters} $ { return OTHER; }

  {Newline} { return WHITE_SPACE; }

}

[^] { return BAD_CHARACTER; }
