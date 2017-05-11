/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/lt/TeX.js
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

MathJax.Localization.addTranslation("lt","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "Per daug atidarom\u0173j\u0173 arba per ma\u017Eai u\u017Edarom\u0173j\u0173 riestini\u0173 skliausteli\u0173",
          ExtraCloseMissingOpen: "Per daug u\u017Edarom\u0173j\u0173 arba per ma\u017Eai atidarom\u0173j\u0173 riestini\u0173 skliausteli\u0173",
          MissingLeftExtraRight: "Per ma\u017Eai \\left arba per daug \\right",
          MissingScript: "N\u0117ra vir\u0161utinio arba apatinio indekso argumento",
          ExtraLeftMissingRight: "Per daug \\left arba per ma\u017Eai \\right",
          Misplaced: "Ne tinkamoje vietoje %1",
          MissingOpenForSub: "N\u0117ra atidaromojo riestinio apatinio indekso skliaustelio",
          MissingOpenForSup: "N\u0117ra atidaromojo riestinio vir\u0161utinio indekso skliaustelio",
          AmbiguousUseOf: "Nevienareik\u0161m\u0117 %1 vartosena",
          EnvBadEnd: "\\begin{%1} baig\u0117si \\end{%2}",
          EnvMissingEnd: "N\u0117ra \\end{%1}",
          MissingBoxFor: "N\u0117ra %1 langelio",
          MissingCloseBrace: "N\u0117ra u\u017Edaromojo riestinio skliaustelio",
          UndefinedControlSequence: "Neapibr\u0117\u017Eta valdymo seka %1",
          DoubleExponent: "Kartojamas laipsnio rodiklis: tikslinti riestiniais skliausteliais",
          DoubleSubscripts: "Kartojamas apatinis indeksas: tikslinti riestiniais skliausteliais",
          DoubleExponentPrime: "Pirminis skai\u010Dius kartoja laipsnio rodikl\u012F: tikslinti riestiniais skliausteliais",
          CantUseHash1: "Makrokomandos parametro ra\u0161mens \u201E#\u201C matematikos veiksenoje vartoti negalima",
          MisplacedMiddle: "%1 privalo b\u016Bti \\left ir \\right viduje",
          MisplacedLimits: "%1 taikomas tik operatoriams",
          MisplacedMoveRoot: "%1 rodomas tik \u0161aknyje",
          MultipleCommand: "Kartojamas %1",
          IntegerArg: "%1 argumentas privalo b\u016Bti sveikasis skai\u010Dius",
          NotMathMLToken: "%1 n\u0117ra leksema",
          InvalidMathMLAttr: "Netinkamas \u201EMathML\u201C po\u017Eymis: %1",
          UnknownAttrForElement: "%1 n\u0117ra atpa\u017E\u012Fstamas %2 po\u017Eymis",
          MaxMacroSub1: "Vir\u0161ytas did\u017Eiausias leid\u017Eiamas \u201EMathJax\u201C makrokomand\u0173 pakait\u0173 skai\u010Dius; galb\u016Bt vykdomas rekursinis makrokomandos kreipinys?",
          MaxMacroSub2: "Vir\u0161ytas did\u017Eiausias leid\u017Eiamas \u201EMathJax\u201C pakait\u0173 skai\u010Dius; galb\u016Bt vykdoma rekursin\u0117 \u201ELaTeX\u201C aplinka?",
          MissingArgFor: "N\u0117ra %1 argumento",
          ExtraAlignTab: "Per daug lygiavimo tabuliatori\u0173 \\cases tekste",
          BracketMustBeDimension: "%1 argumentas riestiniuose skliausteliuose privalo b\u016Bti matmuo",
          InvalidEnv: "Netinkamas aplinkos pavadinimas \u201E%1\u201C",
          UnknownEnv: "Ne\u017Einoma aplinka '%1'",
          ExtraCloseLooking: "Per daug riestini\u0173 skliausteli\u0173 ie\u0161kant %1",
          MissingCloseBracket: "%1 argumente nepavyko rasti u\u017Edaromojo \u201E]\u201C",
          MissingOrUnrecognizedDelim: "%1 neturi skirtuko arba \u0161is n\u0117ra atpa\u017E\u012Fstamas",
          MissingDimOrUnits: "%1 neturi matmens arba \u0161iojo vienet\u0173",
          TokenNotFoundForCommand: "%2 skirto %1 rasti nepavyko",
          MathNotTerminated: "Teksto langelyje matematikos neaptikta",
          IllegalMacroParam: "Netinkama makrokomandos parametro nuoroda",
          MaxBufferSize: "Vir\u0161ytas vidinio \u201EMathJax\u201C buferio dydis; galb\u016Bt vykdomas rekursinis makrokomandos kreipinys?",
          CommandNotAllowedInEnv: "%1 aplinkoje %2 neleid\u017Eiamas",
          MultipleLabel: "Apibr\u0117\u017Etas \u017Eymenos \u201E%1\u201C kartojimas",
          CommandAtTheBeginingOfLine: "%1 privalo b\u016Bti eilut\u0117s prad\u017Eioje",
          IllegalAlign: "%1 nurodyta netinkama lygiuot\u0117",
          BadMathStyleFor: "Netinkamas %1 matematikos stilius",
          PositiveIntegerArg: "%1 argumentas privalo b\u016Bti teigiamas sveikasis skai\u010Dius",
          ErroneousNestingEq: "Klaidingas lyg\u010Di\u0173 sandar\u0173 \u012Fd\u0117jimas",
          MultlineRowsOneCol: "Eilut\u0117s %1 aplinkoje privalo apimti tiksliai vien\u0105 stulpel\u012F",
          MultipleBBoxProperty: "%1 kartojamas %2",
          InvalidBBoxProperty: "\u201E%1\u201C neatrodo kaip spalva, u\u017Epildymo matmuo arba stilius",
          ExtraEndMissingBegin: "Per daug %1 arba per ma\u017Eai \\begingroup",
          GlobalNotFollowedBy: "Po %1 neina \\let, \\def arba \\newcommand",
          UndefinedColorModel: "Neapibr\u0117\u017Etas spalvos modelis \u201E%1\u201C",
          ModelArg1: "Modelio \u201E%1\u201C spalv\u0173 reik\u0161m\u0117s ra\u0161omos trimis skaitmenimis",
          InvalidDecimalNumber: "Netinkamas de\u0161imtainis skai\u010Dius",
          ModelArg2: "Modelio \u201E%1\u201C spalv\u0173 reik\u0161m\u0117s privalo b\u016Bti tarp %2 ir %3",
          InvalidNumber: "Neleistinas skai\u010Dius",
          NewextarrowArg1: "Pirmasis %1 argumentas privalo b\u016Bti valdymo sekos pavadinimas",
          NewextarrowArg2: "Antrasis %1 argumentas privalo b\u016Bti du kableliu skiriami sveikieji skai\u010Diai",
          NewextarrowArg3: "Tre\u010Diasis %1 argumentas privalo b\u016Bti unikodo ra\u0161mens skai\u010Dius",
          NoClosingChar: "Nepavyksta rasti u\u017Edaromojo %1",
          IllegalControlSequenceName: "Netinkamas %1 valdymo sekos pavadinimas",
          IllegalParamNumber: "%1 nurodytas netinkamas parametr\u0173 skai\u010Dius",
          MissingCS: "Po %1 privalo eiti valdymo seka",
          CantUseHash2: "Netinkama \u201E#\u201C vartosena %1 ruo\u0161inyje",
          SequentialParam: "%1 parametrai numeruotini nuosekliai",
          MissingReplacementString: "N\u0117ra %1 apibr\u0117\u017Eimo pakaitos eilut\u0117s",
          MismatchUseDef: "%1 vartosena nesutampa su %1 apibr\u0117\u017Eimu",
          RunawayArgument: "Nevaldomas %1 argumentas?",
          NoClosingDelim: "Nepavyksta rasti u\u017Edaromojo %1 skirtuko"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/lt/TeX.js");
