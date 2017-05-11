/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/es/es.js
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

MathJax.Localization.addTranslation("es",null,{
  menuTitle: "espa\u00F1ol",
  version: "2.6.0",
  isLoaded: true,
  domains: {
    "_": {
        version: "2.6.0",
        isLoaded: true,
        strings: {
          CookieConfig: "MathJax ha encontrado una cookie de configuraci\u00F3n de usuario que incluye c\u00F3digo para ser ejecutado.\u00BFQuieres ejecutarlo?\n\\n\n(Pulse Cancelar al menos que configure la cookie).",
          MathProcessingError: "Error de procesamiento de matem\u00E1ticas",
          MathError: "Error de matem\u00E1ticas",
          LoadFile: "Cargando %1",
          Loading: "Cargando",
          LoadFailed: "Fall\u00F3 la carga del archivo: %1",
          ProcessMath: "Procesando notaci\u00F3n matem\u00E1tica: %1\u00A0%%",
          Processing: "Procesando",
          TypesetMath: "Composici\u00F3n tipogr\u00E1fica en curso: %1 %%",
          Typesetting: "Composici\u00F3n tipogr\u00E1fica",
          MathJaxNotSupported: "El navegador no admite MathJax"
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
      if (n === 1) return 1; // one
      return 2; // other
    },
  number: function (n) {
      return n;
    }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/es/es.js");
