/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/pt-br/TeX.js
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

MathJax.Localization.addTranslation("pt-br","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "Sobrou uma chave de abertura ou faltou uma de fechamento",
          ExtraCloseMissingOpen: "Sobrou uma chave de fechamento ou faltou uma de abertura",
          MissingLeftExtraRight: "Faltou um \\left ou sobrou um \\right",
          MissingScript: "Faltou o argumento de um sobrescrito ou de um subscrito",
          ExtraLeftMissingRight: "Sobrou um \\left ou faltou um \\right",
          Misplaced: "%1 fora do lugar",
          MissingOpenForSub: "Faltou uma chave de abertura para o subscrito",
          MissingOpenForSup: "Faltou uma chave de abertura para o sobrescrito",
          AmbiguousUseOf: "Uso amb\u00EDguo de %1",
          EnvBadEnd: "\\begin{%1} foi terminado com \\end{%2}",
          EnvMissingEnd: "Faltou \\end{%1}",
          MissingBoxFor: "Faltou uma caixa para %1",
          MissingCloseBrace: "Faltou uma chave de fechamento",
          UndefinedControlSequence: "Sequ\u00EAncia de controle indefinida %1",
          DoubleExponent: "Expoente duplo: utilize chaves para esclarecer",
          DoubleSubscripts: "Subscrito duplo: utilize chaves para esclarecer",
          DoubleExponentPrime: "Prime causa expoente duplo: utilize chaves para esclarecer",
          CantUseHash1: "Voc\u00EA n\u00E3o pode usar o caractere # que indica um par\u00E2metro de macro no modo matem\u00E1tico",
          MisplacedMiddle: "%1 deve estar entre \\left e \\right",
          MisplacedLimits: "%1 s\u00F3 \u00E9 permitido nos operadores",
          MisplacedMoveRoot: "%1 pode aparecer somente dentro de uma raiz",
          MultipleCommand: "Repeti\u00E7\u00E3o de %1",
          IntegerArg: "O argumento de %1 deve ser um inteiro",
          NotMathMLToken: "%1 n\u00E3o \u00E9 um elemento de token",
          InvalidMathMLAttr: "Atributo MathML inv\u00E1lido: %1",
          UnknownAttrForElement: "%1 n\u00E3o \u00E9 um atributo reconhecido para %2",
          MaxMacroSub1: "Foi excedido o m\u00E1ximo de substitui\u00E7\u00F5es de macros do MathJax; h\u00E1 alguma chamada a uma macro recursiva?",
          MaxMacroSub2: "Foi excedido o m\u00E1ximo de substitui\u00E7\u00F5es do MathJax; h\u00E1 algum ambiente de LaTeX recursivo?",
          MissingArgFor: "Faltou um argumento para %1",
          ExtraAlignTab: "Sobrou um tab de alinhamento no texto de \\cases",
          BracketMustBeDimension: "O argumento nos colchetes de %1 deve ser uma dimens\u00E3o",
          InvalidEnv: "Nome de ambiente inv\u00E1lido '%1'",
          UnknownEnv: "Ambiente desconhecido '%1'",
          ExtraCloseLooking: "Sobrou uma chave de fechamento ao procurar por %1",
          MissingCloseBracket: "N\u00E3o foi encontrado um ']' de fechamento para o argumento de %1",
          MissingOrUnrecognizedDelim: "O delimitador para %1 est\u00E1 ausente ou n\u00E3o foi reconhecido",
          MissingDimOrUnits: "Faltou a dimens\u00E3o ou a unidade de %1",
          TokenNotFoundForCommand: "N\u00E3o foi encontrado %1 para %2",
          MathNotTerminated: "A f\u00F3rmula n\u00E3o foi terminada na caixa de texto",
          IllegalMacroParam: "Refer\u00EAncia inv\u00E1lida a um par\u00E2metro de macro",
          MaxBufferSize: "O tamanho do buffer interno do MathJax foi excedido; h\u00E1 alguma chamada a uma macro recursiva?",
          CommandNotAllowedInEnv: "%1 n\u00E3o \u00E9 permitido no ambiente %2",
          MultipleLabel: "O r\u00F3tulo '%1' foi definido mais de uma vez",
          CommandAtTheBeginingOfLine: "%1 deve vir no in\u00EDcio da linha",
          IllegalAlign: "Foi especificado um alinhamento ilegal em %1",
          BadMathStyleFor: "Estilo de f\u00F3rmulas matem\u00E1ticas ruim para %1",
          PositiveIntegerArg: "O argumento para %1 deve ser um numero inteiro positivo",
          ErroneousNestingEq: "Aninhamento incorreto de estruturas de equa\u00E7\u00F5es",
          MultlineRowsOneCol: "As linhas do ambiente %1 devem ter apenas uma coluna",
          MultipleBBoxProperty: "%1 foi especificado duas vezes em %2",
          InvalidBBoxProperty: "'%1' n\u00E3o parece ser uma cor, uma dimens\u00E3o para padding, nem um estilo",
          ExtraEndMissingBegin: "Sobrou um %1 ou faltou um \\begingroup",
          GlobalNotFollowedBy: "%1 n\u00E3o foi seguido por um \\let, \\def, ou \\newcommand",
          UndefinedColorModel: "O modelo de cores '%1' n\u00E3o foi definido",
          ModelArg1: "Os valores de cor para o modelo %1 exigem 3 n\u00FAmeros",
          InvalidDecimalNumber: "N\u00FAmero decimal inv\u00E1lido",
          ModelArg2: "Os valores de cor para o modelo %1 devem estar entre %2 e %3",
          InvalidNumber: "N\u00FAmero inv\u00E1lido",
          NewextarrowArg1: "O primeiro argumento de %1 deve ser o nome de uma sequ\u00EAncia de controle",
          NewextarrowArg2: "O segundo argumento de %1 deve ser composto de dois inteiros separados por uma v\u00EDrgula",
          NewextarrowArg3: "O terceiro argumento de %1 deve ser o n\u00FAmero de um caractere unicode",
          NoClosingChar: "N\u00E3o foi poss\u00EDvel encontrar um %1 de fechamento",
          IllegalControlSequenceName: "Nome ilegal para uma sequ\u00EAncia de controle de %1",
          IllegalParamNumber: "N\u00FAmero ilegal de par\u00E2metros especificado em %1",
          MissingCS: "%1 deve ser seguido por uma sequ\u00EAncia de controle",
          CantUseHash2: "Uso ilegal de # em um modelo para %1",
          SequentialParam: "Os par\u00E2metros para %1 devem ser numerados sequencialmente",
          MissingReplacementString: "Faltou a string de substitui\u00E7\u00E3o para a defini\u00E7\u00E3o de %1",
          MismatchUseDef: "O uso de %1 n\u00E3o est\u00E1 de acordo com sua defini\u00E7\u00E3o",
          RunawayArgument: "Argumento extra para %1?",
          NoClosingDelim: "N\u00E3o foi encontrado um delimitador de fechamento para %1"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/pt-br/TeX.js");
