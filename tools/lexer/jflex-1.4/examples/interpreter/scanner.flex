/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (C) 2001       Gerwin Klein <lsf@jflex.de>                    *
 * Copyright (C) 2001       Bernhard Rumpe <rumpe@in.tum.de>               *
 * All rights reserved.                                                    *
 *                                                                         *
 * This program is free software; you can redistribute it and/or modify    *
 * it under the terms of the GNU General Public License. See the file      *
 * COPYRIGHT for more information.                                         *
 *                                                                         *
 * This program is distributed in the hope that it will be useful,         *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of          *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the           *
 * GNU General Public License for more details.                            *
 *                                                                         *
 * You should have received a copy of the GNU General Public License along *
 * with this program; if not, write to the Free Software Foundation, Inc., *
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA                 *
 *                                                                         *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */


import java_cup.runtime.Symbol;

%%

%cup
%implements sym

%{ 
  SymTab symtab;          // externe symbol table

  public void setSymtab(SymTab symtab) {
    this.symtab = symtab; 
  }

  private Symbol sym(int sym) {
    return new Symbol(sym);
  }

  private Symbol sym(int sym, Object val) {
    return new Symbol(sym, val);
  }
%}

%%

"arguments"     { return sym(ARGUMENTS); }
"input"         { return sym(INPUT); }
"functions"     { return sym(FUNCTIONS); }
"output"        { return sym(OUTPUT); }
"end"           { return sym(END); }
"if"            { return sym(IF); }
"then"          { return sym(THEN); }
"else"          { return sym(ELSE); }
"fi"            { return sym(FI); }
[a-z]+          { symtab.enter(yytext(),new SymtabEntry(yytext()));
                  return sym(ID,yytext()); }
[0-9]+          { return sym(NUMBER,yytext()); }
","             { return sym(COMMA); }
"("             { return sym(LPAR); }
")"             { return sym(RPAR); }
"="             { return sym(EQ); }
"-"             { return sym(MINUS); }
"+"             { return sym(PLUS); }
"*"             { return sym(TIMES); }
"/"             { return sym(DIV); }
"<"             { return sym(LE); }
"<="            { return sym(LEQ); }
[\ \t\b\f\r\n]+ { /* eat whitespace */ }
"//"[^\n]*      { /* one-line comment */ }
.               { throw new Error("Unexpected character ["+yytext()+"]"); }
