/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/cs/TeX.js
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

MathJax.Localization.addTranslation("cs","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "P\u0159eb\u00FDvaj\u00EDc\u00ED otv\u00EDrac\u00ED nebo chyb\u011Bj\u00EDc\u00ED zav\u00EDrac\u00ED slo\u017Een\u00E1 z\u00E1vorka",
          ExtraCloseMissingOpen: "P\u0159eb\u00FDvaj\u00EDc\u00ED zav\u00EDrac\u00ED nebo chyb\u011Bj\u00EDc\u00ED otv\u00EDrac\u00ED slo\u017Een\u00E1 z\u00E1vorka",
          MissingLeftExtraRight: "Chyb\u011Bj\u00EDc\u00ED \\left nebo p\u0159eb\u00FDvaj\u00EDc\u00ED \\right",
          MissingScript: "Chyb\u00ED argument horn\u00EDho nebo doln\u00EDho indexu",
          ExtraLeftMissingRight: "P\u0159eb\u00FDvaj\u00EDc\u00ED \\left nebo chyb\u011Bj\u00EDc\u00ED \\right",
          Misplaced: "Chybn\u011B um\u00EDst\u011Bn\u00FD %1",
          MissingOpenForSub: "U doln\u00EDho indexu chyb\u00ED otv\u00EDrac\u00ED slo\u017Een\u00E1 z\u00E1vorka",
          MissingOpenForSup: "U horn\u00EDho indexu chyb\u00ED otv\u00EDrac\u00ED slo\u017Een\u00E1 z\u00E1vorka",
          AmbiguousUseOf: "Nejednozna\u010Dn\u00E9 u\u017Eit\u00ED %1",
          EnvBadEnd: "\\begin{%1} bylo uzav\u0159eno \\end{%2}",
          EnvMissingEnd: "Chyb\u011Bj\u00EDc\u00ED \\end{%1}",
          MissingBoxFor: "Chyb\u00ED box pro %1",
          MissingCloseBrace: "Chyb\u00ED zav\u00EDrac\u00ED slo\u017Een\u00E1 z\u00E1vorka",
          UndefinedControlSequence: "Nedefinovan\u00E1 \u0159\u00EDdic\u00ED sekvence %1",
          DoubleExponent: "Dvojit\u00FD exponent: pro vyjasn\u011Bn\u00ED pou\u017Eijte slo\u017Een\u00E9 z\u00E1vorky",
          DoubleSubscripts: "Dvojit\u00FD doln\u00ED index: pro vyjasn\u011Bn\u00ED pou\u017Eijte slo\u017Een\u00E9 z\u00E1vorky",
          DoubleExponentPrime: "Symbol \u010D\u00E1rky zp\u016Fsobil dvojit\u00FD exponent: pro vyjasn\u011Bn\u00ED pou\u017Eijte slo\u017Een\u00E9 z\u00E1vorky",
          CantUseHash1: "V matematick\u00E9m re\u017Eimu nem\u016F\u017Eete pou\u017E\u00EDt znak \u201E#\u201C pro parametry maker",
          MisplacedMiddle: "%1 mus\u00ED b\u00FDt uvnit\u0159 \\left a \\right",
          MisplacedLimits: "%1 je dovoleno pouze u oper\u00E1tor\u016F",
          MisplacedMoveRoot: "%1 se m\u016F\u017Ee vyskytnout pouze v ko\u0159eni",
          MultipleCommand: "V\u00EDcen\u00E1sobn\u00FD %1",
          IntegerArg: "Argumentem %1 mus\u00ED b\u00FDt cel\u00E9 \u010D\u00EDslo",
          NotMathMLToken: "%1 nen\u00ED primitivn\u00ED element",
          InvalidMathMLAttr: "Neplatn\u00FD atribut MathML: %1",
          UnknownAttrForElement: "%1 nen\u00ED zn\u00E1m\u00FD atribut %2",
          MaxMacroSub1: "P\u0159ekro\u010Den maxim\u00E1ln\u00ED po\u010Det substituc\u00ED makra MathJaxu; nen\u00ED tam rekurzivn\u00ED vol\u00E1n\u00ED makra?",
          MaxMacroSub2: "P\u0159ekro\u010Den maxim\u00E1ln\u00ED po\u010Det substituc\u00ED MathJaxu; nen\u00ED tam rekurzivn\u00ED LaTexov\u00E9 prost\u0159ed\u00ED?",
          MissingArgFor: "Chyb\u00ED argument pro %1",
          ExtraAlignTab: "P\u0159ebyte\u010Dn\u00FD zarovn\u00E1vac\u00ED tabul\u00E1tor v textu \\cases",
          BracketMustBeDimension: "Z\u00E1vorkov\u00FD argument u %1 mus\u00ED b\u00FDt rozm\u011Br",
          InvalidEnv: "Neplatn\u00E9 jm\u00E9no prost\u0159ed\u00ED \u201E%1\u201C",
          UnknownEnv: "Nezn\u00E1m\u00E9 prost\u0159ed\u00ED \u201E%1\u201C",
          ExtraCloseLooking: "P\u0159ebyte\u010Dn\u00E1 zav\u00EDrac\u00ED hranat\u00E1 z\u00E1vorka, zat\u00EDmco bylo o\u010Dek\u00E1v\u00E1no %1",
          MissingCloseBracket: "U argumentu %1 nebyla nalezena zav\u00EDrac\u00ED \u201E]\u201C",
          MissingOrUnrecognizedDelim: "Chyb\u011Bj\u00EDc\u00ED nebo nerozpoznan\u00FD odd\u011Blova\u010D u %1",
          MissingDimOrUnits: "U %1 chyb\u00ED rozm\u011Br nebo jeho jednotka",
          TokenNotFoundForCommand: "Nenalezeno %1 k %2",
          MathNotTerminated: "V textov\u00E9m boxu nen\u00ED ukon\u010Dena matematika",
          IllegalMacroParam: "Neplatn\u00FD odkaz na parametr makra",
          MaxBufferSize: "P\u0159ekro\u010Dena velikost intern\u00ED pam\u011Bti MathJaxu; nen\u00ED tam rekurzivn\u00ED vol\u00E1n\u00ED makra?",
          CommandNotAllowedInEnv: "V prost\u0159ed\u00ED %2 nen\u00ED dovolen %1",
          MultipleLabel: "V\u00EDcen\u00E1sobn\u00E1 definice n\u00E1v\u011Bst\u00ED %1",
          CommandAtTheBeginingOfLine: "%1 mus\u00ED b\u00FDt um\u00EDst\u011Bno na za\u010D\u00E1tku \u0159\u00E1dky",
          IllegalAlign: "U %1 uvedeno neplatn\u00E9 zarovn\u00E1n\u00ED",
          BadMathStyleFor: "Chybn\u00FD styl matematiky u %1",
          PositiveIntegerArg: "Argumentem %1 mus\u00ED b\u00FDt kladn\u00E9 cel\u00E9 \u010D\u00EDslo",
          ErroneousNestingEq: "Chybn\u00E9 zano\u0159ov\u00E1n\u00ED struktury rovnic",
          MultlineRowsOneCol: "\u0158\u00E1dky v prost\u0159ed\u00ED %1 mus\u00ED m\u00EDt pr\u00E1v\u011B jeden sloupec",
          MultipleBBoxProperty: "U %2 je %1 uvedeno dvakr\u00E1t",
          InvalidBBoxProperty: "\u201E%1\u201C nevypad\u00E1 jako barva, rozm\u011Br paddingu nebo styl",
          ExtraEndMissingBegin: "P\u0159eb\u00FDvaj\u00EDc\u00ED %1 nebo chyb\u011Bj\u00EDc\u00ED \\begingroup",
          GlobalNotFollowedBy: "Za %1 chyb\u00ED \\let, \\def nebo \\newcommand",
          UndefinedColorModel: "Barevn\u00FD model \u201E%1\u201C nen\u00ED definov\u00E1n",
          ModelArg1: "Barevn\u00E9 hodnoty modelu %1 vy\u017Eaduj\u00ED t\u0159i \u010D\u00EDsla",
          InvalidDecimalNumber: "Neplatn\u00E9 desetinn\u00E9 \u010D\u00EDslo",
          ModelArg2: "Barevn\u00E9 hodnoty modelu %1 mus\u00ED le\u017Eet mezi %2 a %3",
          InvalidNumber: "Neplatn\u00E9 \u010D\u00EDslo",
          NewextarrowArg1: "Prvn\u00EDm argumentem %1 mus\u00ED b\u00FDt n\u00E1zev \u0159\u00EDdic\u00ED sekvence",
          NewextarrowArg2: "Druh\u00FDm argumentem %1 mus\u00ED b\u00FDt dv\u011B cel\u00E1 \u010D\u00EDsla odd\u011Blen\u00E1 \u010D\u00E1rkou",
          NewextarrowArg3: "T\u0159et\u00EDm argumentem %1 mus\u00ED b\u00FDt \u010D\u00EDslo znaku Unicode",
          NoClosingChar: "Nelze naj\u00EDt zav\u00EDrac\u00ED %1",
          IllegalControlSequenceName: "Neplatn\u00FD n\u00E1zev \u0159\u00EDdic\u00ED sekvence u %1",
          IllegalParamNumber: "U %1 uveden neplatn\u00FD po\u010Det parametr\u016F",
          MissingCS: "Za %1 mus\u00ED b\u00FDt \u0159\u00EDdic\u00ED sekvence",
          CantUseHash2: "Chybn\u00E9 u\u017Eit\u00ED # v \u0161ablon\u011B pro %1",
          SequentialParam: "Parametry %1 mus\u00ED b\u00FDt \u010D\u00EDslov\u00E1ny postupn\u011B",
          MissingReplacementString: "V definici %1 chyb\u00ED nahrazuj\u00EDc\u00ED \u0159et\u011Bzec",
          MismatchUseDef: "Pou\u017Eit\u00ED %1 neodpov\u00EDd\u00E1 jeho definici",
          RunawayArgument: "Zbloudil\u00FD argument u %1?",
          NoClosingDelim: "Nenalezen ukon\u010Dovac\u00ED znak u %1"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/cs/TeX.js");
