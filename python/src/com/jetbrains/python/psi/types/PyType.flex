package com.jetbrains.python.psi.types;

import com.intellij.lexer.FlexLexer;
import com.jetbrains.python.psi.PyElementType;
import static com.jetbrains.python.psi.types.PyTypeTokenTypes.*;


%%


%class PyTypeLexer
%implements FlexLexer
%unicode
%public

%function advance
%type PyElementType

%eof{ return;
%eof}


%%


[\r\n]+ { return NL; }
[\ \t] { return SPACE; }
(":py"?":class:`"[~!]?)|("`")|([A-Z]"{")|("}") { return MARKUP; }
("...")|("*")|("or")|("of")|("from")|("to")|("<=")|("->")|[,\(\)\.\[\]|] { return OP; }
[T-Z] { return PARAMETER; }
[A-Za-z_][A-Za-z_0-9]* { return IDENTIFIER; }
