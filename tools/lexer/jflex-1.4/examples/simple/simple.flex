/* this is the scanner example from the JLex website 
   (with small modifications to make it more readable) */

%%

%{
  private int comment_count = 0;
%} 

%line
%char
%state COMMENT
%full

%debug

ALPHA=[A-Za-z]
DIGIT=[0-9]
NONNEWLINE_WHITE_SPACE_CHAR=[\ \t\b\012]
NEWLINE=\r|\n|\r\n
WHITE_SPACE_CHAR=[\n\r\ \t\b\012]
STRING_TEXT=(\\\"|[^\n\r\"]|\\{WHITE_SPACE_CHAR}+\\)*
COMMENT_TEXT=([^*/\n]|[^*\n]"/"[^*\n]|[^/\n]"*"[^/\n]|"*"[^/\n]|"/"[^*\n])*
Ident = {ALPHA}({ALPHA}|{DIGIT}|_)*

%% 

<YYINITIAL> {
  "," { return (new Yytoken(0,yytext(),yyline,yychar,yychar+1)); }
  ":" { return (new Yytoken(1,yytext(),yyline,yychar,yychar+1)); }
  ";" { return (new Yytoken(2,yytext(),yyline,yychar,yychar+1)); }
  "(" { return (new Yytoken(3,yytext(),yyline,yychar,yychar+1)); }
  ")" { return (new Yytoken(4,yytext(),yyline,yychar,yychar+1)); }
  "[" { return (new Yytoken(5,yytext(),yyline,yychar,yychar+1)); }
  "]" { return (new Yytoken(6,yytext(),yyline,yychar,yychar+1)); }
  "{" { return (new Yytoken(7,yytext(),yyline,yychar,yychar+1)); }
  "}" { return (new Yytoken(8,yytext(),yyline,yychar,yychar+1)); }
  "." { return (new Yytoken(9,yytext(),yyline,yychar,yychar+1)); }
  "+" { return (new Yytoken(10,yytext(),yyline,yychar,yychar+1)); }
  "-" { return (new Yytoken(11,yytext(),yyline,yychar,yychar+1)); }
  "*" { return (new Yytoken(12,yytext(),yyline,yychar,yychar+1)); }
  "/" { return (new Yytoken(13,yytext(),yyline,yychar,yychar+1)); }
  "=" { return (new Yytoken(14,yytext(),yyline,yychar,yychar+1)); }
  "<>" { return (new Yytoken(15,yytext(),yyline,yychar,yychar+2)); }
  "<"  { return (new Yytoken(16,yytext(),yyline,yychar,yychar+1)); }
  "<=" { return (new Yytoken(17,yytext(),yyline,yychar,yychar+2)); }
  ">"  { return (new Yytoken(18,yytext(),yyline,yychar,yychar+1)); }
  ">=" { return (new Yytoken(19,yytext(),yyline,yychar,yychar+2)); }
  "&"  { return (new Yytoken(20,yytext(),yyline,yychar,yychar+1)); }
  "|"  { return (new Yytoken(21,yytext(),yyline,yychar,yychar+1)); }
  ":=" { return (new Yytoken(22,yytext(),yyline,yychar,yychar+2)); }

  {NONNEWLINE_WHITE_SPACE_CHAR}+ { }

  "/*" { yybegin(COMMENT); comment_count++; }

  \"{STRING_TEXT}\" {
    String str =  yytext().substring(1,yylength()-1);
    return (new Yytoken(40,str,yyline,yychar,yychar+yylength()));
  }
  
  \"{STRING_TEXT} {
    String str =  yytext().substring(1,yytext().length());
    Utility.error(Utility.E_UNCLOSEDSTR);
    return (new Yytoken(41,str,yyline,yychar,yychar + str.length()));
  } 
  
  {DIGIT}+ { return (new Yytoken(42,yytext(),yyline,yychar,yychar+yylength())); }  

  {Ident} { return (new Yytoken(43,yytext(),yyline,yychar,yychar+yylength())); }  
}

<COMMENT> {
  "/*" { comment_count++; }
  "*/" { if (--comment_count == 0) yybegin(YYINITIAL); }
  {COMMENT_TEXT} { }
}


{NEWLINE} { }

. {
  System.out.println("Illegal character: <" + yytext() + ">");
	Utility.error(Utility.E_UNMATCHED);
}

