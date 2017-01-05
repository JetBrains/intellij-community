/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/fr/fr.js
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

MathJax.Localization.addTranslation("fr",null,{
  menuTitle: "fran\u00E7ais",
  version: "2.6.0",
  isLoaded: true,
  domains: {
    "_": {
        version: "2.6.0",
        isLoaded: true,
        strings: {
          CookieConfig: "MathJax a trouv\u00E9 un t\u00E9moin (cookie) de configuration utilisateur qui inclut du code ex\u00E9cutable. Souhaitez vous l\u2019ex\u00E9cuter ?\n\n(Choisissez Annuler sauf si vous avez cr\u00E9\u00E9 ce t\u00E9moin vous-m\u00EAme.)",
          MathProcessingError: "Erreur de traitement de la formule math\u00E9matique",
          MathError: "Erreur dans la formule math\u00E9matique",
          LoadFile: "Chargement de %1",
          Loading: "Chargement",
          LoadFailed: "\u00C9chec du chargement de %1",
          ProcessMath: "Traitement des formules : %1 %%",
          Processing: "Traitement en cours",
          TypesetMath: "Composition des formules: %1%%",
          Typesetting: "Composition",
          MathJaxNotSupported: "Votre navigateur ne supporte pas MathJax"
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
      if (0 <= n && n < 2) {return 1} // one
      return 2; // other
    },
  number: function (n) {
      return String(n).replace(".", ","); // replace dot by comma
    }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/fr/fr.js");
