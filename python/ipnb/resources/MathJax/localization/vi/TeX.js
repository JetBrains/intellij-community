/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/vi/TeX.js
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

MathJax.Localization.addTranslation("vi","TeX",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          ExtraOpenMissingClose: "D\u1EA5u ngo\u1EB7c m\u1EDF c\u00F2n d\u01B0 ho\u1EB7c d\u1EA5u ngo\u1EB7c \u0111\u00F3ng b\u1ECB thi\u1EBFu",
          ExtraCloseMissingOpen: "D\u1EA5u ngo\u1EB7c \u0111\u00F3ng c\u00F2n d\u01B0 ho\u1EB7c d\u1EA5u ngo\u1EB7c m\u1EDF b\u1ECB thi\u1EBFu",
          MissingLeftExtraRight: "Thi\u1EBFu \\left ho\u1EB7c d\u01B0 \\right",
          MissingScript: "Thi\u1EBFu \u0111\u1ED1i s\u1ED1 ch\u1EC9 s\u1ED1",
          ExtraLeftMissingRight: "D\u01B0 \\left ho\u1EB7c thi\u1EBFu \\right",
          MissingOpenForSub: "Thi\u1EBFu d\u1EA5u ngo\u1EB7c \u0111\u01A1n m\u1EDF cho ch\u1EC9 s\u1ED1 d\u01B0\u1EDBi",
          MissingOpenForSup: "Thi\u1EBFu d\u1EA5u ngo\u1EB7c \u0111\u01A1n m\u1EDF cho ch\u1EC9 s\u1ED1 tr\u00EAn",
          AmbiguousUseOf: "%1 \u0111\u01B0\u1EE3c s\u1EED d\u1EE5ng m\u1ED9t c\u00E1ch kh\u00F4ng r\u00F5 r\u00E0ng",
          EnvBadEnd: "\\begin{%1} k\u1EBFt th\u00FAc v\u1EDBi \\end{%2}",
          EnvMissingEnd: "Thi\u1EBFu \\end{%1}",
          MissingBoxFor: "Thi\u1EBFu h\u1ED9p cho %1",
          MissingCloseBrace: "Thi\u1EBFu d\u1EA5u ngo\u1EB7c \u0111\u00F3ng",
          MisplacedMiddle: "%1 ph\u1EA3i n\u1EB1m gi\u1EEFa \\left v\u00E0 \\right",
          MisplacedLimits: "%1 ch\u1EC9 \u0111\u01B0\u1EE3c cho ph\u00E9p \u0111\u1ED1i v\u1EDBi ph\u00E9p to\u00E1n",
          MultipleCommand: "Nhi\u1EC1u %1",
          InvalidMathMLAttr: "Thu\u1ED9c t\u00EDnh MathML kh\u00F4ng h\u1EE3p l\u1EC7: %1",
          UnknownAttrForElement: "%1 kh\u00F4ng \u0111\u01B0\u1EE3c c\u00F4ng nh\u1EADn l\u00E0 thu\u1ED9c t\u00EDnh cho %2",
          MissingArgFor: "Thi\u1EBFu \u0111\u1ED1i s\u1ED1 cho %1",
          InvalidEnv: "T\u00EAn m\u00F4i tr\u01B0\u1EDDng \u201C%1\u201D kh\u00F4ng h\u1EE3p l\u1EC7",
          UnknownEnv: "M\u00F4i tr\u01B0\u1EDDng kh\u00F4ng r\u00F5 \u201C%1\u201D",
          TokenNotFoundForCommand: "Kh\u00F4ng t\u00ECm th\u1EA5y %1 cho %2",
          CommandNotAllowedInEnv: "Kh\u00F4ng cho ph\u00E9p %1 trong m\u00F4i tr\u01B0\u1EDDng %2",
          MultipleLabel: "Nh\u00E3n \u201C%1\u201D \u0111\u01B0\u1EE3c \u0111\u1ECBnh r\u00F5 nhi\u1EC1u l\u1EA7n",
          CommandAtTheBeginingOfLine: "%1 ph\u1EA3i n\u1EB1m v\u00E0o \u0111\u1EA7u d\u00F2ng",
          MultipleBBoxProperty: "%1 \u0111\u01B0\u1EE3c \u0111\u1ECBnh r\u00F5 hai l\u1EA7n trong %2",
          InvalidDecimalNumber: "S\u1ED1 th\u1EADp ph\u00E2n kh\u00F4ng h\u1EE3p l\u1EC7",
          InvalidNumber: "S\u1ED1 kh\u00F4ng h\u1EE3p l\u1EC7",
          NoClosingChar: "Kh\u00F4ng t\u00ECm th\u1EA5y %1 \u0111\u00F3ng",
          Misplaced: "\u0110\u00E3 \u0111\u1EB7t sai ch\u1ED7 %1",
          UndefinedControlSequence: "Tr\u00ECnh t\u1EF1 ki\u1EC3m so\u00E1t kh\u00F4ng x\u00E1c \u0111\u1ECBnh %1",
          DoubleExponent: "Ch\u1EC9 s\u1ED1 tr\u00EAn ch\u1EC9 s\u1ED1 tr\u00EAn: d\u00F9ng d\u1EA5u ngo\u1EB7c m\u00F3c \u0111\u1EC3 l\u00E0m r\u00F5",
          DoubleSubscripts: "Ch\u1EC9 s\u1ED1 d\u01B0\u1EDBi ch\u1EC9 s\u1ED1 d\u01B0\u1EDBi: d\u00F9ng d\u1EA5u ngo\u1EB7c m\u00F3c \u0111\u1EC3 l\u00E0m r\u00F5",
          DoubleExponentPrime: "D\u1EA5u ph\u1EA9y tr\u00EAn g\u00E2y ra ch\u1EC9 s\u1ED1 tr\u00EAn ch\u1EC9 s\u1ED1 tr\u00EAn: d\u00F9ng d\u1EA5u ngo\u1EB7c m\u00F3c \u0111\u1EC3 l\u00E0m r\u00F5",
          CantUseHash1: "B\u1EA1n kh\u00F4ng th\u1EC3 s\u1EED d\u1EE5ng \u201Ck\u00FD t\u1EF1 tham bi\u1EBFn macro #\u201D trong ch\u1EBF \u0111\u1ED9 to\u00E1n",
          MisplacedMoveRoot: "%1 ch\u1EC9 c\u00F3 th\u1EC3 xu\u1EA5t hi\u1EC7n trong ph\u00E9p c\u0103n",
          IntegerArg: "\u0110\u1ED1i s\u1ED1 c\u1EE7a %1 ph\u1EA3i l\u00E0 s\u1ED1 nguy\u00EAn",
          NotMathMLToken: "%1 kh\u00F4ng ph\u1EA3i l\u00E0 ph\u1EA7n t\u1EED d\u1EA5u hi\u1EC7u",
          MaxMacroSub1: "\u0110\u00E3 v\u01B0\u1EE3t qu\u00E1 s\u1ED1 l\u1EA7n thay th\u1EBF macro t\u1ED1i \u0111a c\u1EE7a MathJax; c\u00F3 ph\u1EA3i g\u1ECDi macro \u0111\u1EC7 quy?",
          MaxMacroSub2: "\u0110\u00E3 v\u01B0\u1EE3t qu\u00E1 s\u1ED1 l\u1EA7n thay th\u1EBF t\u1ED1i \u0111a c\u1EE7a MathJax; m\u00F4i tr\u01B0\u1EDDng LaTeX c\u00F3 ph\u1EA3i \u0111\u1EC7 quy?",
          ExtraAlignTab: "Th\u1EBB c\u0103n ch\u1EC9nh d\u01B0 trong v\u0103n b\u1EA3n \\cases",
          BracketMustBeDimension: "\u0110\u1ED1i s\u1ED1 trong d\u1EA5u ngo\u1EB7c c\u1EE7a %1 ph\u1EA3i l\u00E0 chi\u1EC1u",
          ExtraCloseLooking: "D\u1EA5u \u0111\u00F3ng b\u1EA5t ng\u1EDD trong khi t\u00ECm ki\u1EBFm %1",
          MissingCloseBracket: "Kh\u00F4ng t\u00ECm th\u1EA5y d\u1EA5u \u201C]\u201D \u0111\u00F3ng cho \u0111\u1ED1i s\u1ED1 c\u1EE7a %1",
          MissingOrUnrecognizedDelim: "D\u1EA5u t\u00E1ch b\u1ECB thi\u1EBFu ho\u1EB7c kh\u00F4ng r\u00F5 cho %1",
          MissingDimOrUnits: "Thi\u1EBFu chi\u1EC1u ho\u1EB7c \u0111\u01A1n v\u1ECB c\u1EE7a %1",
          MathNotTerminated: "To\u00E1n kh\u00F4ng ch\u1EA5m d\u1EE9t trong h\u1ED9p v\u0103n b\u1EA3n",
          IllegalMacroParam: "Tham chi\u1EBFu tham bi\u1EBFn macro kh\u00F4ng h\u1EE3p l\u1EC7",
          MaxBufferSize: "\u0110\u00E3 v\u01B0\u1EE3t qu\u00E1 k\u00EDch th\u01B0\u1EDBc b\u1ED9 \u0111\u1EC7m n\u1ED9i b\u1ED9 c\u1EE7a MathJax; c\u00F3 ph\u1EA3i g\u1ECDi macro \u0111\u1EC7 quy?",
          IllegalAlign: "\u0110\u00E3 x\u00E1c \u0111\u1ECBnh s\u1EF1 c\u0103n ch\u1EC9nh kh\u00F4ng h\u1EE3p l\u1EC7 trong %1",
          BadMathStyleFor: "Ki\u1EC3u to\u00E1n h\u1ECFng v\u1EDBi %1",
          PositiveIntegerArg: "\u0110\u1ED1i s\u1ED1 c\u1EE7a %1 ph\u1EA3i l\u00E0 s\u1ED1 nguy\u00EAn d\u01B0\u01A1ng",
          ErroneousNestingEq: "C\u1EA5u tr\u00FAc \u0111\u1EB3ng th\u1EE9c b\u1ECB x\u1EBFp l\u1ED3ng sai l\u1EA7m",
          MultlineRowsOneCol: "C\u00E1c h\u00E0ng trong m\u00F4i tr\u01B0\u1EDDng %1 ph\u1EA3i c\u00F3 \u0111\u00FAng m\u1ED9t c\u1ED9t",
          InvalidBBoxProperty: "\u201C%1\u201D kh\u00F4ng tr\u00F4ng gi\u1ED1ng nh\u01B0 m\u00E0u, chi\u1EC1u l\u00F3t, ho\u1EB7c ki\u1EC3u",
          ExtraEndMissingBegin: "D\u01B0 %1 ho\u1EB7c thi\u1EBFu \\begingroup",
          GlobalNotFollowedBy: "%1 kh\u00F4ng c\u00F3 \\let, \\def, ho\u1EB7c \\newcommand ti\u1EBFp theo",
          UndefinedColorModel: "M\u00F4 h\u00ECnh m\u00E0u \u201C%1\u201D kh\u00F4ng \u0111\u1ECBnh ngh\u0129a",
          ModelArg1: "Gi\u00E1 tr\u1ECB m\u00E0u cho m\u00F4 h\u00ECnh %1 \u0111\u00F2i h\u1ECFi 3 s\u1ED1",
          ModelArg2: "Gi\u00E1 tr\u1ECB m\u00E0u cho m\u00F4 h\u00ECnh %1 ph\u1EA3i \u1EDF gi\u1EEFa %2 v\u00E0 %3",
          NewextarrowArg1: "\u0110\u1ED1i s\u1ED1 \u0111\u1EA7u ti\u00EAn c\u1EE7a %1 ph\u1EA3i l\u00E0 t\u00EAn tr\u00ECnh t\u1EF1 \u0111i\u1EC1u khi\u1EC3n",
          NewextarrowArg2: "\u0110\u1ED1i s\u1ED1 th\u1EE9 hai c\u1EE7a %1 ph\u1EA3i l\u00E0 hai s\u1ED1 nguy\u00EAn ph\u00E2n t\u00E1ch b\u1EB1ng d\u1EA5u ph\u1EA9y",
          NewextarrowArg3: "\u0110\u1ED1i s\u1ED1 th\u1EE9 ba c\u1EE7a %1 ph\u1EA3i l\u00E0 s\u1ED1 k\u00FD t\u1EF1 Unicode",
          IllegalControlSequenceName: "T\u00EAn tr\u00ECnh t\u1EF1 \u0111i\u1EC1u khi\u1EC3n kh\u00F4ng h\u1EE3p l\u1EC7 cho %1",
          IllegalParamNumber: "\u0110\u00E3 x\u00E1c \u0111\u1ECBnh s\u1ED1 tham bi\u1EBFn kh\u00F4ng h\u1EE3p l\u1EC7 cho %1",
          MissingCS: "%1 ph\u1EA3i c\u00F3 tr\u00ECnh t\u1EF1 \u0111i\u1EC1u khi\u1EC3n ti\u1EBFp theo",
          CantUseHash2: "\u0110\u00E3 s\u1EED d\u1EE5ng # m\u1ED9t c\u00E1ch kh\u00F4ng h\u1EE3p l\u1EC7 trong khu\u00F4n m\u1EABu c\u1EE7a %1",
          SequentialParam: "Tham bi\u1EBFn c\u1EE7a %1 ph\u1EA3i \u0111\u01B0\u1EE3c \u0111\u00E1nh s\u1ED1 li\u00EAn t\u1EE5c",
          MissingReplacementString: "Thi\u1EBFu chu\u1ED7i thay th\u1EBF khi \u0111\u1ECBnh ngh\u0129a %1",
          MismatchUseDef: "\u0110\u00E3 s\u1EED d\u1EE5ng %1 m\u1ED9t c\u00E1ch kh\u00F4ng ph\u00F9 h\u1EE3p v\u1EDBi \u0111\u1ECBnh ngh\u0129a c\u1EE7a n\u00F3",
          RunawayArgument: "\u0110\u1ED1i s\u1ED1 c\u1EE7a %1 kh\u00F4ng ng\u1EEBng?",
          NoClosingDelim: "Kh\u00F4ng t\u00ECm th\u1EA5y d\u1EA5u k\u1EBFt th\u00FAc %1"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/vi/TeX.js");
