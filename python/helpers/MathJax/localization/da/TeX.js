/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/da/TeX.js
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

MathJax.Localization.addTranslation("da","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "Ekstra venstreklammeparentes eller manglende h\u00F8jreklammeparentes",
          ExtraCloseMissingOpen: "Ekstra h\u00F8jreklammeparentes eller manglende venstreklammeparentes",
          MissingLeftExtraRight: "Manglende \\left eller ekstra \\right",
          MissingScript: "Manglende h\u00E6vet skrift eller s\u00E6nket skrift argument",
          ExtraLeftMissingRight: "Ekstra \\left eller manglende \\right",
          Misplaced: "Malplaceret %1",
          MissingOpenForSub: "Manglende venstreklammeparentes til s\u00E6nket skrift",
          MissingOpenForSup: "Manglende venstreklammeparentes til h\u00E6vet skrift",
          AmbiguousUseOf: "Flertydig brug af %1",
          EnvBadEnd: "\\begin{%1} sluttede med \\end{%2}",
          EnvMissingEnd: "Manglende \\end{%1}",
          MissingBoxFor: "Manglende boks for %1",
          MissingCloseBrace: "Manglende h\u00F8jreklammeparentes",
          UndefinedControlSequence: "Udefineret kontrolsekvens %1",
          DoubleExponent: "Dobbelt eksponent: brug klammeparenteser til at tydeligg\u00F8re",
          DoubleSubscripts: "Dobbelt s\u00E6nket skrift: brug klammeparenteser til at tydeligg\u00F8re",
          DoubleExponentPrime: "M\u00E6rke for\u00E5rsager dobbelt eksponent: bruge klammeparenteser til at tydeligg\u00F8re",
          CantUseHash1: "Du kan ikke bruge 'makro parameter tegnet #' i matematik tilstand",
          MisplacedMiddle: "%1 skal v\u00E6re inden for \\left og \\right",
          MisplacedLimits: "%1 er kun tilladt p\u00E5 operatorer",
          MisplacedMoveRoot: "%1 kan kun v\u00E6re indenfor en root",
          MultipleCommand: "For mange %1",
          IntegerArg: "Argumentet til %1 skal v\u00E6re et heltal",
          NotMathMLToken: "%1 er ikke et token element",
          InvalidMathMLAttr: "Ugyldig MathML attribut: %1",
          UnknownAttrForElement: "%1 er ikke en genkendt attribut for %2",
          MaxMacroSub1: "Det maksimale antal makro substitutioner i MathJax er overskredet; er der et rekursivt makrokald?",
          MaxMacroSub2: "Det maksimale antal substitutioner i MathJax er overskredet; er der et rekursivt LaTeX milj\u00F8?",
          MissingArgFor: "Manglende argument til %1",
          ExtraAlignTab: "For mange \u0026 i \\cases tekst",
          BracketMustBeDimension: "Klammeargument til %1 skal v\u00E6re en dimension",
          InvalidEnv: "Ugyldigt navn '%1'",
          UnknownEnv: "Ukendt navn '%1'",
          ExtraCloseLooking: "Ekstra h\u00F8jreklammeparentes under s\u00F8gning efter %1",
          MissingCloseBracket: "Kunne ikke finde det afsluttende ']' argument til %1",
          MissingOrUnrecognizedDelim: "Manglende eller ukendt skilletegn for %1",
          MissingDimOrUnits: "Manglende dimension eller enheder for %1",
          TokenNotFoundForCommand: "Kunne ikke finde %1 for %2",
          MathNotTerminated: "Matematik ikke afsluttet i tekstfeltet",
          IllegalMacroParam: "Ulovlig makro parameter reference",
          MaxBufferSize: "Intern bufferst\u00F8rrelse for MathJax er overskredet; er der et rekursivt makrokald?",
          CommandNotAllowedInEnv: "%1 er ikke tilladt i milj\u00F8et %2",
          MultipleLabel: "Etiketten '%1' er defineret flere gange",
          CommandAtTheBeginingOfLine: "%1 skal v\u00E6re i begyndelsen af linjen",
          IllegalAlign: "Ulovlig justering angivet i %1",
          BadMathStyleFor: "D\u00E5rlig matematik stil for %1",
          PositiveIntegerArg: "Argumentet til %1 skal v\u00E6re et positivt heltal",
          ErroneousNestingEq: "Fejlagtig indlejring af ligningsstrukturer",
          MultlineRowsOneCol: "R\u00E6kker indenfor milj\u00F8et %1 skal have pr\u00E6cis \u00E9n kolonne",
          MultipleBBoxProperty: "%1 angivet to gange i %2",
          InvalidBBoxProperty: "'%1' ligner ikke en farve, en padding dimension eller en stil",
          ExtraEndMissingBegin: "Ekstra %1 eller manglende \\begingroup",
          GlobalNotFollowedBy: "%1 ikke efterfulgt af \\let, \\def eller \\newcommand",
          UndefinedColorModel: "Farvemodel '%1' ikke defineret",
          ModelArg1: "Farvev\u00E6rdier for modellen %1 kr\u00E6ver 3 tal",
          InvalidDecimalNumber: "Ugyldigt decimaltal",
          ModelArg2: "Farvev\u00E6rdier for modellen %1 skal v\u00E6re mellem %2 og %3",
          InvalidNumber: "Ugyldigt tal",
          NewextarrowArg1: "F\u00F8rste argument til %1 skal v\u00E6re navnet p\u00E5 en kontrol sekvens",
          NewextarrowArg2: "Andet argument til %1 skal v\u00E6re to heltal adskilt af et komma",
          NewextarrowArg3: "Tredje argument til %1 skal v\u00E6re nummeret p\u00E5 et Unicode-tegn",
          NoClosingChar: "Kan ikke finde den afsluttende %1",
          IllegalControlSequenceName: "Ulovligt kontrol sekvens navn for %1",
          IllegalParamNumber: "Ulovligt antal parametre angivet i %1",
          MissingCS: "%1 skal efterf\u00F8lges af en kontrolsekvens",
          CantUseHash2: "Ulovlig brug af # i skabelon for %1",
          SequentialParam: "Parametre for %1 skal v\u00E6re nummereret fortl\u00F8bende",
          MissingReplacementString: "Manglende erstatningsstreng til definition af %1",
          MismatchUseDef: "Brug af %1 stemmer ikke overens med dens definition",
          RunawayArgument: "L\u00F8bsk argument for %1?",
          NoClosingDelim: "Kan ikke finde afsluttende skilletegn for %1"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/da/TeX.js");
