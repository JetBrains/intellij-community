/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/qqq/TeX.js
 *
 *  Copyright (c) 2009-2015 The MathJax Consortium
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

MathJax.Localization.addTranslation("qqq","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "This appears in TeX expressions when open and close braces do not match e.g. \u003Ccode\u003E\\( { \\)\u003C/code\u003E\n\nSee also:\n* {{msg-mathjax|Tex-ExtraCloseMissingOpen}}",
          ExtraCloseMissingOpen: "This appears in TeX expressions when open and close braces do not match e.g. \u003Ccode\u003E\\( } \\)\u003C/code\u003E\n\nSee also:\n* {{msg-mathjax|Tex-ExtraOpenMissingClose}}",
          MissingLeftExtraRight: "{{doc-important|Do not translate \u003Ccode\u003E\\left\u003C/code\u003E and \u003Ccode\u003E\\right\u003C/code\u003E; they are TeX commands.}}\nThis appears in TeX expressions when \u003Ccode\u003Eleft\u003C/code\u003E/\u003Ccode\u003Eright\u003C/code\u003E commands do no match e.g. \u003Ccode\u003E\\( \\right) \\)\u003C/code\u003E",
          MissingScript: "This appears in TeX expressions when a superscript or subscript is missing e.g. \u003Ccode\u003Ea^2\u003C/code\u003E or \u003Ccode\u003Ea_2\u003C/code\u003E.",
          ExtraLeftMissingRight: "{{doc-important|Do not translate \u003Ccode\u003E\\left\u003C/code\u003E and \u003Ccode\u003E\\right\u003C/code\u003E; they are TeX commands.}}\nThis appears in TeX expressions when \u003Ccode\u003Eleft\u003C/code\u003E/\u003Ccode\u003Eright\u003C/code\u003E commands do no match e.g. \u003Ccode\u003E\\( \\left( \\)\u003C/code\u003E",
          Misplaced: "This appears in TeX expressions when an item is misplaced e.g. \u003Ccode\u003E\\( \u0026 \\)\u003C/code\u003E since the ampersand is supposed to be used in tabular expressions.\n\nParameters:\n* %1 - the misplaced item",
          MissingOpenForSub: "This appears in TeX expressions when a subscript is missing an open brace e.g. \u003Ccode\u003E\\( x__ \\)\u003C/code\u003E\n\nSee also:\n* {{msg-mathjax|Tex-MissingOpenForSup}}",
          MissingOpenForSup: "This appears in TeX expressions when a superscript is missing an open brace e.g. \u003Ccode\u003E\\( x^^ \\)\u003C/code\u003E\n\nSee also:\n* {{msg-mathjax|Tex-MissingOpenForSub}}",
          AmbiguousUseOf: "This appears in TeX expressions when a command is used in an ambiguous way e.g. \u003Ccode\u003E\\( x \\over y \\over z \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the name of the TeX command",
          EnvBadEnd: "{{doc-important|Do not translate \u003Ccode\u003E\\begin\u003C/code\u003E and \u003Ccode\u003E\\end\u003C/code\u003E; they are TeX commands.}}\nThis appears in TeX expressions when environment names do not match e.g. \u003Ccode\u003E\\( \\begin{aligned} \\end{eqarray} \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the environment name used for \u003Ccode\u003E\\begin\u003C/code\u003E\n* %2 - the environment name used for \u003Ccode\u003E\\end\u003C/code\u003E",
          EnvMissingEnd: "{{doc-important|Do not translate \u003Ccode\u003E\\end\u003C/code\u003E, it is a TeX command.}}\nThis appears in TeX expressions when an environment is not closed e.g. \u003Ccode\u003E\\( \\begin{aligned} \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the environment name e.g. \u003Ccode\u003Ealigned\u003C/code\u003E",
          MissingBoxFor: "This appears in TeX expressions when a command is missing a TeX box e.g. \u003Ccode\u003E\\( \\raise 1pt \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the command name",
          MissingCloseBrace: "This appears in TeX expressions when a close brace is missing e.g. \u003Ccode\u003E\\( \\array{ \\)\u003C/code\u003E",
          UndefinedControlSequence: "This appears in TeX expressions when an undefined control sequence is used. Parameters:\n* %1 - the name of the TeX command",
          DoubleExponent: "This appears in TeX expressions when an ambiguous double exponent is used e.g. \u003Ccode\u003Ex^3^2\u003C/code\u003E should be \u003Ccode\u003Ex^{3^2}\u003C/code\u003E or \u003Ccode\u003E{x^3}^2\u003C/code\u003E.\n\nSee also:\n* {{msg-mathjax|Tex-DoubleSubscripts}}",
          DoubleSubscripts: "This appears in TeX expressions when an ambiguous double subscripts is used e.g. \u003Ccode\u003Ex_3_2\u003C/code\u003E should be \u003Ccode\u003Ex_{3_2}\u003C/code\u003E or \u003Ccode\u003E{x_3}_2\u003Ccode\u003E.\n\nSee also:\n* {{msg-mathjax|Tex-DoubleExponent}}",
          DoubleExponentPrime: "This appears in TeX expressions when an ambiguous double exponent is caused by a prime e.g. \u003Ccode\u003Ex^a'\u003C/code\u003E should be \u003Ccode\u003E{x^a}'\u003C/code\u003E or \u003Ccode\u003Ex^{a'}\u003C/code\u003E",
          CantUseHash1: "This appears in TeX expressions when the macro parameter character '#' is used in math mode e.g. \u003Ccode\u003E\\( # \\)\u003C/code\u003E",
          MisplacedMiddle: "{{doc-important|Do not translate \u003Ccode\u003E\\left\u003C/code\u003E and \u003Ccode\u003E\\right\u003C/code\u003E; they are TeX commands.}}\nThis appears in TeX expressions when the \u003Ccode\u003Emiddle\u003C/code\u003E command is used outside \u003Ccode\u003E\\left ... \\right\u003C/code\u003E e.g. \u003Ccode\u003E\\( \\middle| \\)\u003C/code\u003E",
          MisplacedLimits: "This appears in TeX expressions when the \u003Ccode\u003Elimits\u003C/code\u003E command is not used on an operator e.g. \u003Ccode\u003E\\( \\limits \\)\u003C/code\u003E.\n\nParameters:\n* %1 - \u003Ccode\u003E\\limits\u003C/code\u003E",
          MisplacedMoveRoot: "This appears in TeX expressions when a move root command is used outside a root e.g. \u003Ccode\u003E\\( \\uproot \\)\u003C/code\u003E.\n\nParameters:\n* %1 - either \u003Ccode\u003E\\uproot\u003C/code\u003E or \u003Ccode\u003E\\leftroot\u003C/code\u003E",
          MultipleCommand: "This happens when a command or token can only be present once, e.g. \u003Ccode\u003E\\tag{}\u003C/code\u003E. Parameters:\n* %1 - the name of the duplicated command",
          IntegerArg: "This happens when an unexpected non-integer argument is passed to a command e.g. \u003Ccode\u003E\\uproot\u003C/code\u003E.\n\nParameters:\n* %1 - the name of the command",
          NotMathMLToken: "MathJax has a non-standard \u003Ccode\u003E\\mmlToken\u003C/code\u003E command to insert MathML token elements.\n\nThis error happens when the tag name is unknown e.g. \u003Ccode\u003E\\mmlToken{INVALID}{x}\u003C/code\u003E",
          InvalidMathMLAttr: "MathJax has non standard MathML and HTML related commands which can contain attributes.\n\nThis error happens when the parameter is not a valid attribute e.g. \u003Ccode\u003E\\( \\mmlToken{mi}[_INVALID_]{x} \\)\u003C/code\u003E where underscores are forbidden.",
          UnknownAttrForElement: "MathJax has non standard MathML and HTML related commands which can contain attributes.\n\nThis error happens when the attribute is invalid for the given element e.g. \u003Ccode\u003E\\( \\mmlToken{mi}[INVALIDATTR=\u003Cnowiki\u003E''\u003C/nowiki\u003E]{x} \\)\u003C/code\u003E\n\nParameters:\n* %1 - attribute\n* %2 - ...",
          MaxMacroSub1: "MathJax limits the number of macro substitutions to prevent infinite loops.\n\nFor example, this error may happen with \u003Ccode\u003E\\newcommand{\\a}{\\a} \\a\u003C/code\u003E",
          MaxMacroSub2: "MathJax limits the number of nested environments to prevent infinite loops.\n\nFor example, this error may happen with \u003Ccode\u003E\\newenvironment{a}{\\begin{a}}{\\end{a}} \\begin{a}\\end{a}\u003C/code\u003E",
          MissingArgFor: "This happens when an argument is missing e.g. \u003Ccode\u003E\\frac{a}\u003C/code\u003E. Parameters:\n* %1 - the command name e.g. \u003Ccode\u003E\\frac\u003C/code\u003E",
          ExtraAlignTab: "{{doc-important|Do not translate \u003Ccode\u003E\\cases\u003C/code\u003E; it is a TeX command.}}\nThis happens when \u003Ccode\u003E\\cases\u003C/code\u003E has two many columns e.g. \u003Ccode\u003E\\cases{a \u0026 b \u0026 c}\u003C/code\u003E",
          BracketMustBeDimension: "This happens when a bracket argument of an item is not a dimension e.g. \u003Ccode\u003E\\begin{array} x \\\\[INVALID] y \\end{array}\u003C/code\u003E.\n\nParameters:\n* %1 - e.g. \u003Ccode\u003E\\\u003C/code\u003E",
          InvalidEnv: "This happens with invalid environment name e.g. \u003Ccode\u003E\\begin{_INVALID_} \\end{_INVALID_}\u003C/code\u003E where underscores are forbidden.\n\nParameters:\n* %1 - the environment name e.g. \u003Ccode\u003E_INVALID_\u003C/code\u003E",
          UnknownEnv: "This happens when an unknown environment is used e.g. \u003Ccode\u003E\\begin{UNKNOWN} \\end{UNKNOWN}\u003C/code\u003E.\n\nParameters:\n* %1 - the environment name e.g. \u003Ccode\u003EUNKNOWN\u003C/code\u003E",
          ExtraCloseLooking: "This happens in some situations when an extra close brace is found while looking for another character, for example \u003Ccode\u003E\\( \\sqrt['''{{red|\u003Cnowiki\u003E}\u003C/nowiki\u003E}}''']x \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the character searched e.g. \u003Ccode\u003E]\u003C/code\u003E",
          MissingCloseBracket: "This error happens when a closing '\u003Ccode\u003E]\u003C/code\u003E' is missing e.g. \u003Ccode\u003E\\( \\sqrt[ \\)\u003C/code\u003E. Parameters:\n* %1 - the command name e.g. \u003Ccode\u003E\\sqrt\u003C/code\u003E",
          MissingOrUnrecognizedDelim: "This error happens when a delimiter is missing or unrecognized in a TeX expression e.g. \u003Ccode\u003E\\( \\left \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the command name e.g. \u003Ccode\u003E\\left\u003C/code\u003E",
          MissingDimOrUnits: "This error happens with some TeX commands that are expecting a unit e.g. \u003Ccode\u003E\\above\u003C/code\u003E. Parameters:\n* %1 - the command name",
          TokenNotFoundForCommand: "This happens while processing a TeX command that is expected to contain a token e.g. \u003Ccode\u003E\\( \\root{x} \\)\u003C/code\u003E where '\u003Ccode\u003E\\of\u003C/code\u003E' should be used.\n\nParameters:\n* %1 - the token not found e.g. \u003Ccode\u003E\\of\u003C/code\u003E\n* %2 - the command being processed e.g. \u003Ccode\u003E\\root\u003C/code\u003E",
          MathNotTerminated: "This happens when a math is not terminated in a text box e.g. \u003Ccode\u003E\\( \\text{$x} \\)\u003C/code\u003E where the closing dollar is missing.",
          IllegalMacroParam: "This error happens when an invalid macro parameter reference is used e.g. \u003Ccode\u003E\\( \\def\\mymacro#1{#2} \\mymacro{x} \\)\u003C/code\u003E where '#2' is invalid since \u003Ccode\u003E\\mymacro\u003C/code\u003E has only one parameter.",
          MaxBufferSize: "The buffer size refers to the memory used by the TeX input processor.\n\nThis error may happen with recursive calls e.g. \u003Ccode\u003E\\( \\newcommand{\\a}{\\a\\a} \\a \\)\u003C/code\u003E.\n\nNote that the number of a's is exponential with respect to the number of recursive calls.\n\nHence 'MaxBufferSize' is likely to happen before 'MaxMacroSub1'",
          CommandNotAllowedInEnv: "This appears when the \u003Ccode\u003E\\tag\u003C/code\u003E command is used inside an environment that does not allow labelling e.g. \u003Ccode\u003E\\begin{split} x \\tag{x} \\end{split}\u003C/code\u003E.\n\nParameters:\n* %1 - \u003Ccode\u003E\\tag\u003C/code\u003E\n* %2 - the name of the environment",
          MultipleLabel: "This happens when TeX labels are duplicated e.g. \u003Ccode\u003E\\( \\label{x} \\) \\( \\label{x} \\)\u003C/code\u003E.\n\nParameters:\n* %1 - TeX label name",
          CommandAtTheBeginingOfLine: "This happens when showleft/showright are misplaced. Parameters:\n* %1 - the macro name",
          IllegalAlign: "This happens when an invalid alignment is specified in \u003Ccode\u003E\\cfrac\u003C/code\u003E e.g. \u003Ccode\u003E\\cfrac[INVALID]{a}{b}\u003C/code\u003E.\n\nParameters:\n* %1 - \u003Ccode\u003E\\cfrac\u003C/code\u003E",
          BadMathStyleFor: "This happens when an invalid style is specified in \u003Ccode\u003E\\genfrac\u003C/code\u003E e.g. \u003Ccode\u003E\\genfrac{\\{}{\\}}{0pt}{INVALID}{a}{b}\u003C/code\u003E.\n\nParameters:\n* %1 - \u003Ccode\u003E\\genfrac\u003C/code\u003E",
          PositiveIntegerArg: "This happens when an invalid alignment is specified in the \u003Ccode\u003Ealignedat\u003C/code\u003E environment e.g. \u003Ccode\u003E\\begin{alignedat}{INVALID}\\end{alignedat}\u003C/code\u003E.",
          ErroneousNestingEq: "This happens when some equation structures are nested in a way forbidden by LaTeX e.g. two nested \u003Ccode\u003Emultline\u003C/code\u003E environment.",
          MultlineRowsOneCol: "This happens when a row of the \u003Ccode\u003Emultline\u003C/code\u003E environment has more than one column e.g. \u003Ccode\u003E\\begin{multline} x \u0026 y \\end{multline}\u003C/code\u003E.\n\nParameters:\n* %1 - the environment name \u003Ccode\u003Emultline\u003C/code\u003E",
          MultipleBBoxProperty: "This appears with the TeX command \u003Ccode\u003E\\bbox\u003C/code\u003E when a property e.g. the background color is specified twice.\n\nParameters:\n* %1 - the name of the duplicate property\n* %2 - the command name \u003Ccode\u003E\\bbox\u003C/code\u003E",
          InvalidBBoxProperty: "This appears with the TeX command \u003Ccode\u003E\\bbox\u003C/code\u003E when a property is not a color, a padding dimension, or a style.\n\n'padding' is a CSS property name for the 'inner margin' of a box. You may verify on MDN how it is translated in your language.\n\nParameters:\n* %1 - the name of the invalid property specified",
          ExtraEndMissingBegin: "{{doc-important|Do not translate \u003Ccode\u003E\\begingroup\u003C/code\u003E.}}\nThis appears in TeX expressions when begingroup/endgroup do not match. Parameters:\n* %1 - the command name \u003Ccode\u003E\\endgroup\u003C/code\u003E",
          GlobalNotFollowedBy: "{{doc-important|Do not translate \u003Ccode\u003E\\let\u003C/code\u003E, \u003Ccode\u003E\\def\u003C/code\u003E, or \u003Ccode\u003E\\newcommand\u003C/code\u003E; they are TeX expressions.}}\nThis appears in TeX expressions when \u003Ccode\u003E\\global\u003C/code\u003E is not followed by \u003Ccode\u003E\\let\u003C/code\u003E, \u003Ccode\u003E\\def\u003C/code\u003E, or \u003Ccode\u003E\\newcommand\u003C/code\u003E",
          UndefinedColorModel: "An invalid color model is used for the \u003Ccode\u003E\\color\u003C/code\u003E command. Parameters:\n* %1 - the color model specified",
          ModelArg1: "An invalid color value is used for the \u003Ccode\u003E\\color\u003C/code\u003E command e.g. \u003Ccode\u003E\\( \\color[RGB]{}{} \\)\u003C/code\u003E\n\nParameters:\n* %1 - color model name",
          InvalidDecimalNumber: "An invalid decimal number is used for the \u003Ccode\u003E\\color\u003C/code\u003E command e.g. \u003Ccode\u003E\\( \\color[rgb]{,,}{} \\)\u003C/code\u003E.\n\nA valid decimal number is such as: 12, 12., 12.34, .34",
          ModelArg2: "An out-of-range number is used for the \u003Ccode\u003E\\color\u003C/code\u003E command e.g. \u003Ccode\u003E\\( \\color[RGB]{256,,}{} \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the color model e.g. \u003Ccode\u003ERGB\u003C/code\u003E\n* %2 - the lower bound of the valid interval e.g. 0 for the RGB color model\n* %3 - the upper bound of the valid interval e.g. 255 for the RGB color model",
          InvalidNumber: "An invalid number is used for the \u003Ccode\u003E\\color\u003C/code\u003E command e.g. \u003Ccode\u003E\\( \\color[RGB]{,,}{} \\)\u003C/code\u003E.\n\nA valid number is such as: 123",
          NewextarrowArg1: "Used when the first argument of \u003Ccode\u003E\\Newextarrow\u003C/code\u003E is invalid. Parameters:\n* %1 - the command name \u003Ccode\u003E\\Newextarrow\u003C/code\u003E",
          NewextarrowArg2: "Used when the second argument of \u003Ccode\u003E\\Newextarrow\u003C/code\u003E is invalid. Parameters:\n* %1 - the command name \u003Ccode\u003E\\Newextarrow\u003C/code\u003E",
          NewextarrowArg3: "Used when the third argument of \u003Ccode\u003E\\Newextarrow\u003C/code\u003E is invalid. Parameters:\n* %1 - the command name \u003Ccode\u003E\\Newextarrow\u003C/code\u003E",
          NoClosingChar: "This is used in TeX mhchem expressions when a closing delimiters is missing e.g. \u003Ccode\u003E\\( \\ce{ -\u003E[ } \\)\u003C/code\u003E.\n\nParameters:\n* %1 - \u003Ccode\u003E)\u003C/code\u003E, \u003Ccode\u003E}\u003C/code\u003E, or \u003Ccode\u003E]\u003C/code\u003E",
          IllegalControlSequenceName: "This appears when the \u003Ccode\u003E\\newcommand\u003C/code\u003E TeX command is given an illegal control sequence name.\n\nParameters:\n* %1 - \u003Ccode\u003E\\newcommand\u003C/code\u003E",
          IllegalParamNumber: "This appears when the \u003Ccode\u003E\\newcommand\u003C/code\u003E TeX command is given an illegal number of parameters.\n\nParameters:\n* %1 - \u003Ccode\u003E\\newcommand\u003C/code\u003E",
          MissingCS: "This appears when a TeX definitions is not followed by a control sequence e.g. \u003Ccode\u003E\\let INVALID\u003C/code\u003E.\n\nParameters:\n* %1 - may be commands like  \u003Ccode\u003E\\let\u003C/code\u003E, \u003Ccode\u003E\\def\u003C/code\u003E, \u003Ccode\u003E\\newcommand\u003C/code\u003E, \u003Ccode\u003E\\global\u003C/code\u003E, etc.",
          CantUseHash2: "This appears when the character '#' is incorrectly used in TeX definitions, e.g. \u003Ccode\u003E\\def\\mycommand{{red|#}}A\u003C/code\u003E.\n\nParameters:\n* %1 - the command used e.g. \u003Ccode\u003Emycommand\u003C/code\u003E",
          SequentialParam: "This appears in TeX definitions when parameters are not numbered sequentially e.g. \u003Ccode\u003E\\def\\mycommand#2#1\u003C/code\u003E.\n\nParameters:\n* %1 - the command name e.g. \u003Ccode\u003E\\def\u003C/code\u003E",
          MissingReplacementString: "This appears in TeX definitions when you don't specify a replacement string e.g. \u003Ccode\u003E\\def\\mycommand\u003C/code\u003E.\n\nParameters:\n* %1 - the command name e.g. \u003Ccode\u003E\\def\u003C/code\u003E",
          MismatchUseDef: "This appears in TeX definitions when a TeX command does not match its definition e.g. \u003Ccode\u003E\\( \\def\\mycommand[#1]#2[#3]{#1+#2+#3} \\mycommand{a}{b}[c] \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the command name e.g. \u003Ccode\u003E\\mycommand\u003C/code\u003E",
          RunawayArgument: "This appears in TeX definitions when a TeX command does not match its definition e.g. \u003Ccode\u003E\\( \\def\\mycommand[#1][#2]#3{#1+#2+#3} \\mycommand[a]{b} \\)\u003C/code\u003E.\n\nParameters:\n* %1 - the command name e.g. \u003Ccode\u003E\\mycommand\u003C/code\u003E",
          NoClosingDelim: "This appears in TeX expressions when a \u003Ccode\u003E\\verb\u003C/code\u003E command is not closed e.g. \u003Ccode\u003E\\( \\verb?... \\)\u003C/code\u003E is missing a closing question mark.\n\nParameters:\n* %1 - the command name"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/qqq/TeX.js");
