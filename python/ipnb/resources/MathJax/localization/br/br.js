/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/br/br.js
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

MathJax.Localization.addTranslation("br",null,{
  menuTitle: "brezhoneg",
  version: "2.6.0",
  isLoaded: true,
  domains: {
    "_": {
        version: "2.6.0",
        isLoaded: true,
        strings: {
          MathProcessingError: "Fazi o treta\u00F1 ar formulenn",
          MathError: "Fazi er formulenn",
          LoadFile: "O karga\u00F1 %1",
          Loading: "O karga\u00F1",
          LoadFailed: "N'eus ket bet gallet karga\u00F1 %1",
          ProcessMath: "Treta\u00F1 ar formulenno\u00F9 : %1%%",
          Processing: "O treta\u00F1",
          TypesetMath: "Aoza\u00F1 formulenno\u00F9 : %1%%",
          Typesetting: "Aoza\u00F1",
          MathJaxNotSupported: "Ne c'hall ket ho merdeer ober gant MathJax"
        }
    },
    "FontWarnings": {},
    "HTML-CSS": {},
    "HelpDialog": {},
    "MathML": {},
    "MathMenu": {},
    "TeX": {}
  },
  plural: function (n) {
      if (n % 10 === 1 && !(n % 100 === 11 || n % 100 === 71 ||
                            n % 100 === 91)) {return 1} // one
      if (n % 10 === 2 && !(n % 100 === 12 || n % 100 === 72 ||
                            n % 100 === 92)) {return 2} // two
      if ((n % 10 === 3 || n % 10 === 4 || n % 10 === 9) &&
          !(10 <= n % 100 && n % 100 <= 19 ||
            70 <= n % 100 && n % 100 <= 79 ||
            90 <= n % 100 && n % 100 <= 99)) {return 3} // few
      if (n !== 0 && n % 1000000 === 0) {return 4} // other
      return 5;
    },
  number: function (n) {
      return n;
    }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/br/br.js");
