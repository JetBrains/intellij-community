/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/ca/TeX.js
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

MathJax.Localization.addTranslation("ca","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "Sobra una clau d'apertura o falta una clau de tancament",
          ExtraCloseMissingOpen: "Sobra una clau de tancament o falta una clau d'abertura",
          MissingLeftExtraRight: "Falta \\left o sobra \\right",
          MissingScript: "Falta argument de super\u00EDdex o sub\u00EDndex",
          ExtraLeftMissingRight: "Sobra \\left o falta \\right",
          Misplaced: "%1 col\u00B7locat err\u00F2niament",
          MissingOpenForSub: "Falta clau d'abertura per sub\u00EDndex",
          MissingOpenForSup: "Falta clau d'abertura per super\u00EDndex",
          AmbiguousUseOf: "\u00DAs ambigu de %1",
          EnvBadEnd: "\\begin{%1} finalitzat amb \\end{%2}",
          EnvMissingEnd: "Falta \\end{%1}",
          MissingBoxFor: "Falta caixa per %1",
          MissingCloseBrace: "Falta clau de tancament",
          UndefinedControlSequence: "Seq\u00FC\u00E8ncia de control no definida %1",
          DoubleExponent: "Doble exponent: useu claus per aclarir",
          DoubleSubscripts: "Dobre sub\u00EDndex: useu claus per aclarir",
          DoubleExponentPrime: "El s\u00EDmbol \"prima\" causa doble exponent: useu claus per aclarir",
          CantUseHash1: "No podeu fer servir 'el car\u00E0cter # com a par\u00E0metre de macro' en mode matem\u00E0tic",
          MisplacedMiddle: "%1 ha d'estar entre \\left i \\right",
          MisplacedLimits: "%1 nom\u00E9s es permet en operadors",
          MisplacedMoveRoot: "%1 nom\u00E9s pot apar\u00E8ixer a dins d'una arrel",
          MultipleCommand: "%1 m\u00FAltiples",
          IntegerArg: "L'argument de %1 ha de ser enter",
          NotMathMLToken: "%1 no \u00E9s un element de token",
          InvalidMathMLAttr: "Atribut MathML inv\u00E0lid: %1",
          UnknownAttrForElement: "%1 no \u00E9s un atribut v\u00E0lid per %2",
          MaxMacroSub1: "S'ha sobrepassat el nombre m\u00E0xim de substitucions en una macro MathJax; hi ha una crida de macro recursiva?",
          MaxMacroSub2: "El comptador m\u00E0xim de substitucions de MathJax s'ha sobrepassat; hi ha un entorn de LaTeX recursiu?",
          MissingArgFor: "Falta argument per %1",
          ExtraAlignTab: "Marca d'alineaci\u00F3 extra en text \\cases",
          BracketMustBeDimension: "Argument de claud\u00E0tor per %1 ha de ser una dimensi\u00F3",
          InvalidEnv: "Nom d'entorn inv\u00E0lid '%1'",
          UnknownEnv: "Entorn desconegut '%1'",
          ExtraCloseLooking: "Clau de tancament extra mentre es buscava %1",
          MissingCloseBracket: "No s'ha pogut trobar ']' de tancament per argument de %1",
          MissingOrUnrecognizedDelim: "Falta delimitador o delimitador no reconegut per %1",
          MissingDimOrUnits: "Falta dimensi\u00F3 o unitats per %1",
          TokenNotFoundForCommand: "No s'ha pogut trobar %1 per %2",
          MathNotTerminated: "Expressi\u00F3 matem\u00E0tica no finalitzada en quadre de text",
          IllegalMacroParam: "Refer\u00E8ncia il\u00B7legal a par\u00E0metre de macro",
          MaxBufferSize: "S'ha sobrepassat la mida de la mem\u00F2ria interm\u00E8dia interna de MathJax; hi ha una crida de macro recursiva?",
          CommandNotAllowedInEnv: "%1 no perm\u00E8s en entorn %2",
          MultipleLabel: "Etiqueta '%1' definida m\u00E9s d'una veegada",
          CommandAtTheBeginingOfLine: "%1 ha d'apar\u00E8ixer a l'inici de la l\u00EDnia",
          IllegalAlign: "Alineaci\u00F3 especificiada no \u00E9s v\u00E0lida a %1",
          BadMathStyleFor: "Estil de matem\u00E0tiques incorrecte per %1",
          PositiveIntegerArg: "L'argument de %1 ha de ser un enter positiu",
          ErroneousNestingEq: "Nidificaci\u00F3 incorrecta d'estructures d'equaci\u00F3",
          MultlineRowsOneCol: "Les files dins l'entorn %1 han de tenir exactament una columna",
          MultipleBBoxProperty: "%1 s'ha especificat dues vegades dins %2",
          InvalidBBoxProperty: "'%1' no \u00E9s un color, una dimensi\u00F3 o un estil",
          ExtraEndMissingBegin: "Sobra %1 o falta \\begingroup",
          GlobalNotFollowedBy: "%1 no est\u00E0 seguit per \\let, \\def o \\newcommand",
          UndefinedColorModel: "Model de color '%1' no definit",
          ModelArg1: "Valors de color pel model %1 requereixen 3 n\u00FAmeros",
          InvalidDecimalNumber: "N\u00FAmero decimal incorrecte",
          ModelArg2: "Valors de color pel model %1 han d'estar entre %2 i %3",
          InvalidNumber: "N\u00FAmero inv\u00E0lid",
          NewextarrowArg1: "El primer argument per %1 ha de ser un nom de seq\u00FC\u00E8ncia de control",
          NewextarrowArg2: "El segon argument per %1 ha de ser dos enters separats per una coma",
          NewextarrowArg3: "El tercer argument per %1 ha de ser un n\u00FAmero de car\u00E0cter Unicode",
          NoClosingChar: "No s'ha trobat el %1 de tancament",
          IllegalControlSequenceName: "Nom de seq\u00FC\u00E8ncia de control incorrecta per %1",
          IllegalParamNumber: "S'ha especificat un nombre incorrecte de par\u00E0metres dins %1",
          MissingCS: "%1 ha de ser seguit per una seq\u00FC\u00E8ncia de control",
          CantUseHash2: "\u00DAs incorrecte de # en plantilla per %1",
          SequentialParam: "Els par\u00E0metres per %1 s'han de numerar seq\u00FCencialment",
          MissingReplacementString: "Falta cadena de substituci\u00F3 a la definici\u00F3 de %1",
          MismatchUseDef: "L'\u00FAs de %1 no concorda amb la seva definici\u00F3",
          RunawayArgument: "Argument fora de control per %1?",
          NoClosingDelim: "No s'ha trobar el delimitador de tancament per %1"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/ca/TeX.js");
