/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/sv/TeX.js
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

MathJax.Localization.addTranslation("sv","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "Extra v\u00E4nsterklammerparentes eller h\u00F6gerklammerparentes saknas",
          ExtraCloseMissingOpen: "Extra h\u00F6gerklammerparentes eller v\u00E4nsterklammerparentes saknas",
          MissingLeftExtraRight: "Saknad \\left eller en extra \\right",
          ExtraLeftMissingRight: "Extra \\left eller en saknad \\right",
          Misplaced: "Felplacerad %1",
          MissingOpenForSub: "Saknad v\u00E4nsterklammerparentes f\u00F6r index",
          MissingOpenForSup: "Saknad v\u00E4nsterklammerparentes f\u00F6r exponent",
          AmbiguousUseOf: "Tvetydig anv\u00E4ndning av %1",
          EnvBadEnd: "\\begin{%1} slutade med \\end{%2}",
          EnvMissingEnd: "Saknad \\end{%1}",
          MissingBoxFor: "Saknad l\u00E5da f\u00F6r %1",
          MissingCloseBrace: "Saknad h\u00F6gerklammerparentes",
          UndefinedControlSequence: "Odefinierad kontrollsekvens %1",
          DoubleExponent: "Dubbel exponent: anv\u00E4nd klammerparenteser f\u00F6r att klarg\u00F6ra",
          DoubleSubscripts: "Dubbla index: anv\u00E4nd klammerparenteser f\u00F6r att klarg\u00F6ra",
          MisplacedMiddle: "%1 m\u00E5ste vara inom \\left och \\right",
          MisplacedLimits: "%1 till\u00E5ts endast p\u00E5 operat\u00F6rer",
          MisplacedMoveRoot: "%1 kan endast visas inom en rot",
          MultipleCommand: "Flera %1",
          IntegerArg: "Argumentet till %1 m\u00E5ste vara ett heltal",
          NotMathMLToken: "%1 \u00E4r inte ett token-element",
          InvalidMathMLAttr: "Ogiltigt MathML-attribut: %1",
          UnknownAttrForElement: "%1 \u00E4r inte ett igenk\u00E4ndt attribut f\u00F6r %2",
          MissingArgFor: "Saknat argument f\u00F6r %1",
          InvalidEnv: "Ogiltigt milj\u00F6namn '%1'",
          UnknownEnv: "Ok\u00E4nd milj\u00F6 '%1'",
          ExtraCloseLooking: "Extra h\u00F6gerklammerparentes n\u00E4r %1 s\u00F6ktes",
          MissingCloseBracket: "Kunde inte hitta avslutande ']' f\u00F6r argumentet till %1",
          TokenNotFoundForCommand: "Kunde inte hitta %1 f\u00F6r %2",
          CommandNotAllowedInEnv: "%1 till\u00E5ts inte i %2-milj\u00F6n",
          MultipleLabel: "Etiketten '%1' definierades flera g\u00E5nger",
          CommandAtTheBeginingOfLine: "%1 m\u00E5ste vara i b\u00F6rjan p\u00E5 raden",
          IllegalAlign: "Ogiltig justering anges i %1",
          MultipleBBoxProperty: "%1 anges tv\u00E5 g\u00E5nger i %2",
          ExtraEndMissingBegin: "Extra %1 eller en saknad \\begingroup",
          GlobalNotFollowedBy: "%1 f\u00F6ljs inte av \\let, \\def eller \\newcommand",
          UndefinedColorModel: "F\u00E4rgmodellen '%1' \u00E4r inte definierad",
          InvalidDecimalNumber: "Ogiltig decimaltal",
          InvalidNumber: "Ogiltigt nummer",
          NoClosingChar: "Kan inte hitta avslutande %1",
          IllegalControlSequenceName: "Ogiltigt kontrollsekvensnamn f\u00F6r %1",
          IllegalParamNumber: "Ogiltigt antal parametrar anges i %1",
          MissingScript: "Saknat argument f\u00F6r exponent- eller indexl\u00E4ge",
          DoubleExponentPrime: "Primtecken orsakar dubbla exponenter: Anv\u00E4nd klammerparenteser f\u00F6r att klarg\u00F6ra",
          CantUseHash1: "Du kan inte anv\u00E4nda 'makroparameter-tecknet #' i matematikl\u00E4ge",
          MaxMacroSub1: "MathJax maximala antal makro-substitutioner har \u00F6verskridits; finns det ett rekursivt makroanrop?",
          MaxMacroSub2: "MathJax maximala antal substitutioner har \u00F6verskridits; finns det en rekursiv LaTeX-milj\u00F6?",
          ExtraAlignTab: "Extra \u0026-tecken i \\cases-text",
          BracketMustBeDimension: "Argumentet innanf\u00F6r klammerparenteser till %1 m\u00E5ste vara en dimension",
          MissingOrUnrecognizedDelim: "Saknad eller \u00E4r ok\u00E4nd avgr\u00E4nsare f\u00F6r %1",
          MissingDimOrUnits: "Saknar dimension eller dess enheter f\u00F6r %1",
          MathNotTerminated: "Matematiskt uttryckt ej avslutat i textrutan",
          IllegalMacroParam: "Ogiltig referens till makroparameter",
          MaxBufferSize: "MathJax intern buffertstorlek har \u00F6verskridits; finns d\u00E4r ett rekursivt makroanrop?",
          BadMathStyleFor: "D\u00E5lig matematikstil f\u00F6r %1",
          PositiveIntegerArg: "Argumentet till %1 m\u00E5ste vara ett positivt heltal",
          ErroneousNestingEq: "Felaktigt n\u00E4stling av ekvationsstrukturer",
          MultlineRowsOneCol: "Raderna inom %1-milj\u00F6n m\u00E5ste ha exakt en kolumn",
          InvalidBBoxProperty: "'%1' verkar inte vara en f\u00E4rg, en utfyllnadsdimension eller en stil",
          ModelArg1: "F\u00E4rgv\u00E4rden f\u00F6r f\u00E4rgmodell %1 kr\u00E4ver 3 nummer",
          ModelArg2: "F\u00E4rgv\u00E4rden f\u00F6r f\u00E4rgmodell %1 m\u00E5ste vara mellan %2 och %3",
          NewextarrowArg1: "F\u00F6rsta argumentet till %1 m\u00E5ste vara namnet p\u00E5 en kontrollsekvens",
          NewextarrowArg2: "Andra argumentet till %1 m\u00E5ste vara tv\u00E5 heltal separerade av ett komma",
          NewextarrowArg3: "Tredje argumentet till %1 m\u00E5ste vara nummeret p\u00E5 ett Unicode-tecken",
          MissingCS: "%1 m\u00E5ste f\u00F6ljas av en kontrollsekvens",
          CantUseHash2: "Ogiltig anv\u00E4ndning av # i mallen f\u00F6r %1",
          SequentialParam: "Parametrar f\u00F6r %1 m\u00E5ste numreras sekventiellt",
          MissingReplacementString: "Saknar ers\u00E4ttningsstr\u00E4ngen f\u00F6r definition av %1",
          MismatchUseDef: "Anv\u00E4ndning av %1 matchar inte dess definition",
          RunawayArgument: "Skenande argument f\u00F6r %1?",
          NoClosingDelim: "Kunde inte hitta avslutande avgr\u00E4nsare f\u00F6r %1"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/sv/TeX.js");
