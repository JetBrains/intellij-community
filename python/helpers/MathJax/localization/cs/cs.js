/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/cs/cs.js
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

MathJax.Localization.addTranslation("cs",null,{
  menuTitle: "\u010De\u0161tina",
  version: "2.6.0",
  isLoaded: true,
  domains: {
    "_": {
        version: "2.6.0",
        isLoaded: true,
        strings: {
          CookieConfig: "MathJax nalezl cookie u\u017Eivatelsk\u00E9 konfigurace obsahuj\u00EDc\u00ED spustiteln\u00FD k\u00F3d. Chcete ho spustit?\n\n(Pokud jste cookie nenastavili sami, m\u011Bli byste stisknout Storno.)",
          MathProcessingError: "Chyba zpracov\u00E1n\u00ED matematiky",
          MathError: "Chyba matematiky",
          LoadFile: "Na\u010D\u00EDt\u00E1 se %1",
          Loading: "Na\u010D\u00EDt\u00E1 se",
          LoadFailed: "Nepoda\u0159ilo se na\u010D\u00EDst soubor: %1",
          ProcessMath: "Zpracov\u00E1v\u00E1 se matematika: %1 %%",
          Processing: "Zpracov\u00E1v\u00E1 se",
          TypesetMath: "S\u00E1z\u00ED se matematika: %1 %%",
          Typesetting: "S\u00E1z\u00ED se",
          MathJaxNotSupported: "V\u00E1\u0161 prohl\u00ED\u017Ee\u010D nepodporuje MathJax"
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
      if (n === 1) {return 1} // one
      if (n === 2 || n === 3 || n === 4) {return 2} // two--four
      return 3; // other
    },
  number: function (n) {
      return String(n).replace(".", ","); // replace dot by comma
    }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/cs/cs.js");
