/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/nl/FontWarnings.js
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

MathJax.Localization.addTranslation("nl","FontWarnings",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          webFont: "MathJax gebruikt web-gebaseerde lettertypes om wiskunde weer te geven op deze pagina. Het kost tijd om deze te downloaden, dus de pagina zou sneller weergegeven worden als u de wiskunde lettertypes direct in de lettertype map van uw systeem zou plaatsen.",
          imageFonts: "MathJax gebruikt zijn beeld-lettertypes en niet zijn lokale of web-gebaseerde lettertypes. Dit zal een tragere weergave geven dan normaal en de wiskunde zal wellicht niet op de hoogste resolutie van uw printer afgedrukt worden.",
          noFonts: "MathJax is niet in staat een lettertype te vinden waarmee het zijn wiskunde kan weergeven, en beeld-lettertypes zijn niet beschikbaar, dus valt het nu terug op generieke Unicode karakters in de hoop dat uw browsers in staat zal zijn ze weer te geven. Sommige kararakter worden wellicht niet goed weergegeven en mogelijkerwijs helemaal niet.",
          webFonts: "De meeste moderne browsers kunnen lettertypes via het web downloaden. Updaten naar een recentere versie van uw browser (of een andere browser gaan gebruiken) zou de kwaliteit van de wiskunde op deze pagina kunnen verbeteren.",
          fonts: "MathJax kan de [STIX fonts](%1) \u00F3f de [MathJax TeX fonts](%2) gebruiken. Download en installeer een van deze lettertypes om uw MathJax ervaring te verbeteren.",
          STIXPage: "Deze pagina is ontworpen om de [STIX fonts](%1) te gebruiken. Download en installeer deze lettertypes om uw MathJax ervaring te verbeteren.",
          TeXPage: "Deze pagina is ontworpen om de [MathJax TeX fonts](%1) te gebruiken. Download en installeer deze lettertypes om uw MathJax ervaring te verbeteren."
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/nl/FontWarnings.js");
