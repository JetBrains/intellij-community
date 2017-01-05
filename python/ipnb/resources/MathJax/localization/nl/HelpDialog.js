/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/nl/HelpDialog.js
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

MathJax.Localization.addTranslation("nl","HelpDialog",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          Help: "MathJax-hulp",
          MathJax: "*MathJax* is een JavaScript bibliotheek die het mogelijk maakt dat auteurs wiskunde kunnen toevoegen aan hun web-pagina's. Als een lezer hoeft u niks te doen om dat mogelijk te maken.",
          Browsers: "*Browsers*: MathJax werkt met alle moderne browsers, inclusief IE6+, Firefox 3+, Chrome 0.2+, Safari 2+, Opera 9.6+ en de meeste mobiele browsers.",
          Menu: "*Math menu*:MathJax voegt een context menu toe aan vergelijkingen. Rechtsklik of Ctrl-klik op willekeurige wiskunde om het menu weer te geven.",
          ShowMath: "*Wiskunde weergeven als* geeft de formule in bron opmaak weer voor knippen en plakken (als MathML of in de originele opmaak).",
          Settings: "Via *Instellingen* kunt u de functionaliteit van MathJax beheersen, zoals de grootte van de wiskunde en het mechanisme dat gebruikt wordt om vergelijkingen weer te geven.",
          Language: "Via *Taal* kunt u de taal kiezen die MathJax gebruikt voor de menu's en waarschuwingsboodschappen.",
          Zoom: "*Wiskunde zoom*: Als u moeite heeft met het lezen van een vergelijking dan kan MathJax deze vergroten zodat u het beter kunt zien.",
          Accessibilty: "*Toegankelijkheid*: MathJax werkt automatisch samen met schermlezers waardoor wiskunde toegankelijk wordt voor slechtzienden.",
          Fonts: "*Lettertypes*: MathJax zal bepaalde wiskunde lettertypes gebruiken als die ge\u00EFnstalleerd zijn op uw computer; anders zal het web-gebaseerde lettertypes gebruiken. Alhoewel het niet noodzakelijk is, zullen lokaal ge\u00EFnstalleerde lettertypes het zetwerk versnellen. We raden aan om de [STIX fonts](%1) te installeren."
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/nl/HelpDialog.js");
