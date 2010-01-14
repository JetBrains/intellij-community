" Vim syntax file
" Language:         JFlex
" Maintainer:       Gerwin Klein <lsf@jflex.de>
" Last Change:      $Revision: 2.1 $, $Date: 2003/06/08 11:01:12 $

" Thanks to Michael Brailsford for help and suggestions

" Quit when a syntax file was already loaded	{{{
if exists("b:current_syntax")
	finish
endif
"}}}

" Include java syntax {{{
if version >= 600
	runtime! syntax/java.vim
	unlet b:current_syntax 
else
	so $VIMRUNTIME/syntax/java.vim
endif
"}}}

syn cluster jflexOptions contains=jflexOption,jflexCodeInclude,jflexComment,jflexMacroIdent,jflexMacroRegExp,jflexOptionError
syn cluster jflexRules contains=jflexRule,jflexComment,jflexActionCode,jflexRuleStates,jflexRegExp

" java code section
syn region jflexStart start="/\*\|//\|import\|package\|class"me=s end="^%%"me=e-2 contains=@javaTop nextgroup=jflexOptionReg

" %% 
" options 
syn region jflexOptionReg matchgroup=jflexSectionSep start="^%%" end="^%%"me=e-2 contains=@jflexOptions nextgroup=jflexRulesReg

syn match jflexOptionError "%\i*" contained

syn match jflexOption "^\(%s\|%x\)" contained
syn match jflexOption "^%state" contained
syn match jflexOption "^%states" contained
syn match jflexOption "^%xstate" contained
syn match jflexOption "^%xstates" contained
syn match jflexOption "^%char" contained
syn match jflexOption "^%line" contained
syn match jflexOption "^%column" contained
syn match jflexOption "^%byaccj" contained
syn match jflexOption "^%cup" contained
syn match jflexOption "^%cupsym" contained
syn match jflexOption "^%cupdebug" contained
syn match jflexOption "^%eofclose" contained
syn match jflexOption "^%class" contained
syn match jflexOption "^%function" contained
syn match jflexOption "^%type" contained
syn match jflexOption "^%integer" contained
syn match jflexOption "^%int" contained
syn match jflexOption "^%intwrap" contained
syn match jflexOption "^%yyeof" contained
syn match jflexOption "^%notunix" contained
syn match jflexOption "^%7bit" contained
syn match jflexOption "^%8bit" contained
syn match jflexOption "^%full" contained
syn match jflexOption "^%16bit" contained
syn match jflexOption "^%unicode" contained
syn match jflexOption "^%caseless" contained
syn match jflexOption "^%ignorecase" contained
syn match jflexOption "^%implements" contained
syn match jflexOption "^%extends" contained
syn match jflexOption "^%public" contained
syn match jflexOption "^%apiprivate" contained
syn match jflexOption "^%final" contained
syn match jflexOption "^%abstract" contained
syn match jflexOption "^%debug" contained
syn match jflexOption "^%standalone" contained
syn match jflexOption "^%switch" contained
syn match jflexOption "^%table" contained
syn match jflexOption "^%pack" contained
syn match jflexOption "^%include" contained
syn match jflexOption "^%buffer" contained
syn match jflexOption "^%initthrow" contained
syn match jflexOption "^%eofthrow" contained
syn match jflexOption "^%yylexthrow" contained
syn match jflexOption "^%throws" contained
syn match jflexOption "^%scannerror" contained

syn match jflexMacroIdent "\I\i*\s*="me=e-1 contained nextgroup=jflexMacroRegExp

syn region jflexMacroRegExp matchgroup=jflexOperator start="=" end="^\(%\|\I\|\i\|/\)"me=e-1 contains=NONE contained

syn region jflexCodeInclude matchgroup=jflexCodeIncludeMark start="^%{" end="^%}" contains=@javaTop contained
syn region jflexCodeInclude matchgroup=jflexCodeIncludeMark start="^%init{" end="^%init}" contains=@javaTop contained
syn region jflexCodeInclude matchgroup=jflexCodeIncludeMark start="^%initthrow{" end="^%initthrow}" contains=@javaTop contained
syn region jflexCodeInclude matchgroup=jflexCodeIncludeMark start="^%eof{" end="^%eof}" contains=@javaTop contained
syn region jflexCodeInclude matchgroup=jflexCodeIncludeMark start="^%eofthrow{" end="^%eofthrow}" contains=@javaTop contained
syn region jflexCodeInclude matchgroup=jflexCodeIncludeMark start="^%yylexthrow{" end="^%yylexthrow}" contains=@javaTop contained
syn region jflexCodeInclude matchgroup=jflexCodeIncludeMark start="^%eofval{" end="^%eofval}" contains=@javaTop contained

" rules (end pattern shouldn't occur, if it does anyway we just stay in jflexRulesReg)
syn region jflexRulesReg matchgroup=jflexSectionSep start="^%%" end="^%%"me=e-2 contains=@jflexRules 

" at first everything but strings is a regexp
syn match jflexRegExp "\([^\" \t]\|\\\"\)\+" contained

" take out comments
syn match jflexComment "//.*" contained
syn region jflexComment start="/\*" end="\*/" contained contains=jflexComment

" lex states
syn match jflexRuleStates "<\s*\I\i*\(\s*,\s*\I\i*\)*\s*>" contained skipnl skipwhite nextgroup=jflexStateGroup

" action code (only after states braces and macro use)
syn region jflexActionCode matchgroup=Delimiter start="{" end="}" contained contains=@javaTop,jflexJavaBraces

" macro use
syn match jflexRegExp "{\s*\I\i*\s*}" contained

" state braces (only active after <state>)
syn region jflexStateGroup matchgroup=jflexRuleStates start="{$" start="{\s" end="}" contained contains=@jflexRules

" string
syn region jflexRegExp matchgroup=String start=+"+ skip=+\\\\\|\\"+ end=+"+ contained

" not to be confused with a state
syn match jflexRegExp "<<EOF>>" contained

" escape sequence
syn match jflexRegExp "\\." contained


" keep braces in actions balanced
syn region jflexJavaBraces start="{" end="}" contained contains=@javaTop,jflexJavaBraces


" syncing
syn sync clear
syn sync minlines=10
syn sync match jflexSync grouphere jflexOptionReg "^%[a-z]"
syn sync match jflexSync grouphere jflexRulesReg "^<"


" highlighting
hi link jflexOption      Special
hi link jflexMacroIdent  Ident
hi link jflexMacroRegExp Macro
hi link jflexOptionError Error
hi link jflexComment     Comment
hi link jflexOperator    Operator
hi link jflexRuleStates  Special
hi link jflexRegExp      Function
hi jflexSectionSep guifg=yellow ctermfg=yellow guibg=blue ctermbg=blue gui=bold cterm=bold
hi link jflexCodeIncludeMark jflexSectionSep

let b:current_syntax="jflex"
