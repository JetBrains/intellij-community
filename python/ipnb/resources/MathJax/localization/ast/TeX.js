/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/ast/TeX.js
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

MathJax.Localization.addTranslation("ast","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "Hai una llave d'apertura de m\u00E1s o falta una llave de zarramientu",
          ExtraCloseMissingOpen: "Hai una llave de zarramientu de m\u00E1s o falta una llave d'apertura",
          MissingLeftExtraRight: "Falta un \\left o sobra un \\right",
          MissingScript: "Falta un argumentu de super\u00EDndiz o sub\u00EDndiz",
          ExtraLeftMissingRight: "Sobra un \\left o falta un \\right",
          Misplaced: "%1 ta mal coloc\u00E1u",
          MissingOpenForSub: "Falta una llave d'apertura pal sub\u00EDndiz",
          MissingOpenForSup: "Falta una llave d'apertura pal super\u00EDndiz",
          AmbiguousUseOf: "Usu ambiguu de %1",
          EnvBadEnd: "\\begin{%1} acab\u00E1u con \\end{%2}",
          EnvMissingEnd: "Falta \\end{%1}",
          MissingBoxFor: "Falta un cuadru pa %1",
          MissingCloseBrace: "Falta la llave de zarramientu",
          UndefinedControlSequence: "Secuencia de control indefinida %1",
          DoubleExponent: "Doble esponente: use llaves p'aclarar",
          DoubleSubscripts: "Doble sub\u00EDndiz: usu llaves p'aclarar",
          DoubleExponentPrime: "La prima causa un doble esponente: use llaves p'aclarar",
          CantUseHash1: "Nun pue usar el \u00ABcar\u00E1uter # de par\u00E1metru de macro\u00BB en mou matem\u00E1ticu",
          MisplacedMiddle: "%1 tien de tar ente \\left y \\right",
          MisplacedLimits: "%1 s\u00F3lo ta permit\u00EDu pa operadores",
          MisplacedMoveRoot: "%1 s\u00F3lo pue apaecer dientro d'una ra\u00EDz",
          MultipleCommand: "M\u00FAltiples %1",
          IntegerArg: "L'argumentu de %1 tien de ser un enteru",
          NotMathMLToken: "%1 nun ye un elementu de \u00ABtoken\u00BB",
          InvalidMathMLAttr: "Atributu de MathML inv\u00E1lidu: %1",
          UnknownAttrForElement: "%1 nun ye un atributu reconoc\u00EDu pa %2",
          MaxMacroSub1: "Se sobrepas\u00F3'l n\u00FAmberu m\u00E1ximu de sustituciones de macro de MathJax; \u00BFhai ha una llamada de macro recursiva?",
          MaxMacroSub2: "Pas\u00F3se'l n\u00FAmberu m\u00E1ximu de sustituciones de MathJax; \u00BFhai un entornu de LaTeX recursivu?",
          MissingArgFor: "Falta l'argumentu pa %1",
          ExtraAlignTab: "Tabulador d'alliniamientu estra en testu \\cases",
          BracketMustBeDimension: "L'argumentu de corchete pa %1 tien de ser una dimensi\u00F3n",
          InvalidEnv: "Nome d'entornu \u00AB%1\u00BB inv\u00E1lidu",
          UnknownEnv: "Entornu desconoc\u00EDu \u00AB%1\u00BB",
          ExtraCloseLooking: "Llave de zarramientu estra cuando se buscaba %1",
          MissingCloseBracket: "Nun pudo alcontrase'l \u00AB]\u00BB de zarramientu pal argumentu de %1",
          MissingOrUnrecognizedDelim: "El delimitador pa %1 falta o nun ta reconoc\u00EDu",
          MissingDimOrUnits: "Falta la dimensi\u00F3n o les unidaes pa %1",
          TokenNotFoundForCommand: "Nun pudo alcontrase %1 pa %2",
          MathNotTerminated: "Espresi\u00F3n matem\u00E1tica inacabada nel cuadru de testu",
          IllegalMacroParam: "Referencia illegal a par\u00E1metru de macro",
          MaxBufferSize: "Se sobrepas\u00F3 el tama\u00F1u del almacenamientu intermediu internu de MathJax; \u00BFhai ha una llamada a una macro recursiva?",
          CommandNotAllowedInEnv: "%1 nun se permite nel entornu %2",
          MultipleLabel: "La etiqueta '%1' tien definiciones m\u00FAltiples",
          CommandAtTheBeginingOfLine: "%1 tien d'apaecer al principiu de la llinia",
          IllegalAlign: "Alliniaci\u00F3n illegal especificada en %1",
          BadMathStyleFor: "Estilu de matem\u00E1tiques incorreutu pa %1",
          PositiveIntegerArg: "L'argumentu de %1 tien de ser un enteru positivu",
          ErroneousNestingEq: "A\u00F1eramientu incorreutu d'estructures d'ecuaci\u00F3n",
          MultlineRowsOneCol: "Les fileres dientro del entornu %1 han de tener exactamente una columna",
          MultipleBBoxProperty: "Propied\u00E1 %1 especificada dos veces en %2",
          InvalidBBoxProperty: "'%1' nun paez un color, una distancia de separaci\u00F3n o un estilu",
          ExtraEndMissingBegin: "Sobra un %1 o falta un \\begingroup",
          GlobalNotFollowedBy: "%1 nun ta sigu\u00EDu por \\let, \\def o \\newcommand",
          UndefinedColorModel: "El modelu de color '%1' nun ta defin\u00EDu",
          ModelArg1: "Los valores de color pal modelu %1 requieren 3 n\u00FAmberos",
          InvalidDecimalNumber: "N\u00FAmberu decimal inv\u00E1lidu",
          ModelArg2: "Los valores de color pal modelu %1 tienen de tar ente %2 y %3",
          InvalidNumber: "N\u00FAmberu inv\u00E1lidu",
          NewextarrowArg1: "El primer argumentu pa %1 tien de ser un nome de secuencia de control",
          NewextarrowArg2: "El segundu argumentu pa %1 tienen de ser dos enteros separaos por una coma",
          NewextarrowArg3: "El tercer argumentu pa %1 tien de ser un n\u00FAmberu de car\u00E1uter Unicode",
          NoClosingChar: "Nun pue alcontrase'l %1 de zarru",
          IllegalControlSequenceName: "Nome incorreutu de secuencia de control pa %1",
          IllegalParamNumber: "N\u00FAmberu par\u00E1metros illegal especific\u00E1u en %1",
          MissingCS: "%1 tien de tar sigu\u00EDu por una secuencia de control",
          CantUseHash2: "Usu illegal de # nuna plant\u00EDa pa %1",
          SequentialParam: "Los par\u00E1metros pa %1 tienen de numberase de mou secuencial",
          MissingReplacementString: "Falta la cadena de sustituci\u00F3n pa la definici\u00F3n de %1",
          MismatchUseDef: "L'usu de %1 nun casa cola so definici\u00F3n",
          RunawayArgument: "\u00BFArgumentu descontrol\u00E1u pa %1?",
          NoClosingDelim: "Nun s'alcontr\u00F3 el delimitador de zarru pa %1"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/ast/TeX.js");
