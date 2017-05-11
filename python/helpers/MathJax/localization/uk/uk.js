/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/uk/uk.js
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

MathJax.Localization.addTranslation("uk",null,{
  menuTitle: "\u0443\u043A\u0440\u0430\u0457\u043D\u0441\u044C\u043A\u0430",
  version: "2.6.0",
  isLoaded: true,
  domains: {
    "_": {
        version: "2.6.0",
        isLoaded: true,
        strings: {
          CookieConfig: "MathJax \u0437\u043D\u0430\u0439\u0448\u043B\u0430 \u043A\u0443\u043A\u0438 \u043A\u043E\u043D\u0444\u0456\u0433\u0443\u0440\u0430\u0446\u0456\u0457 \u043A\u043E\u0440\u0438\u0441\u0442\u0443\u0432\u0430\u0447\u0430, \u0449\u043E \u043C\u0456\u0441\u0442\u0438\u0442\u044C \u043A\u043E\u0434 \u0434\u043B\u044F \u0437\u0430\u043F\u0443\u0441\u043A\u0443. \u0412\u0438 \u0445\u043E\u0447\u0435\u0442\u0435 \u0437\u0430\u043F\u0443\u0441\u0442\u0438\u0442\u0438 \u0439\u043E\u0433\u043E?\n\n\n(\u0412\u0438 \u043F\u043E\u0432\u0438\u043D\u043D\u0456 \u043D\u0430\u0442\u0438\u0441\u043D\u0443\u0442\u0438 \"\u0421\u043A\u0430\u0441\u0443\u0432\u0430\u0442\u0438\", \u0430\u0431\u0438 \u0441\u043A\u0430\u0441\u0443\u0432\u0430\u0442\u0438 \u043D\u0430\u043B\u0430\u0448\u0442\u0443\u0432\u0430\u043D\u043D\u044F \u043A\u0443\u043A \u0443 \u043D\u0430\u043B\u0430\u0448\u0442\u0443\u0432 \u043D\u0430\u0441\u0442\u0440\u043E\u044E\u0432\u0430\u043D\u043D\u044F cookie \u0441\u0435\u0431\u0435.)",
          MathProcessingError: "\u041F\u043E\u043C\u0438\u043B\u043A\u0430 \u043E\u0431\u0440\u043E\u0431\u043A\u0438 \u043C\u0430\u0442\u0435\u043C\u0430\u0442\u0438\u043A\u0438",
          MathError: "\u041C\u0430\u0442\u0435\u043C\u0430\u0442\u0438\u0447\u043D\u0430 \u043F\u043E\u043C\u0438\u043B\u043A\u0430",
          LoadFile: "\u0417\u0430\u0432\u0430\u043D\u0442\u0430\u0436\u0435\u043D\u043D\u044F %1",
          Loading: "\u0417\u0430\u0432\u0430\u043D\u0442\u0430\u0436\u0435\u043D\u043D\u044F",
          LoadFailed: "\u041D\u0435 \u0432\u0434\u0430\u043B\u043E\u0441\u044F \u0437\u0430\u0432\u0430\u043D\u0442\u0430\u0436\u0438\u0442\u0438 \u0444\u0430\u0439\u043B: %1",
          ProcessMath: "\u041E\u0431\u0440\u043E\u0431\u043A\u0430 \u043C\u0430\u0442\u0435\u043C\u0430\u0442\u0438\u043A\u0438: %1%%",
          Processing: "\u041E\u0431\u0440\u043E\u0431\u043A\u0430...",
          TypesetMath: "\u0412\u0435\u0440\u0441\u0442\u043A\u0430 \u043C\u0430\u0442\u0435\u043C\u0430\u0442\u0438\u043A\u0438: %1%%",
          Typesetting: "\u0412\u0435\u0440\u0441\u0442\u043A\u0430",
          MathJaxNotSupported: "\u0412\u0430\u0448 \u0431\u0440\u0430\u0443\u0437\u0435\u0440 \u043D\u0435 \u043F\u0456\u0434\u0442\u0440\u0438\u043C\u0443\u0454 MathJax"
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
      if (n % 10 === 1 && n % 100 != 11) return 1; // one
      if (2 <= n % 10 && n % 10 <= 4 && !(12 <= n % 100 && n % 100 <= 14)) return 2; // few
      if (n % 10 === 0 || (5 <= n % 10 && n % 10 <= 9) || (11 <= n % 100 && n % 100 <= 14)) return 3; // many
      return 4; // other
    },
  number: function (n) {
      return n;
    }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/uk/uk.js");
