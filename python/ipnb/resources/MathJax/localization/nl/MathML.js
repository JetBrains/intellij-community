/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/nl/MathML.js
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

MathJax.Localization.addTranslation("nl","MathML",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          BadMglyph: "Onjuiste mglyph: %1",
          BadMglyphFont: "Verkeerd lettertype: %1",
          MathPlayer: "MathJax was niet in staat MathPlayer in te stellen.\n\\n\nAls MathPlay niet ge\u00EFnstalleerd is dan dient u dat eerst te doen.\nAnders kan het zijn dat beveiligingsinstellingen de uitvoering van ActiveX-besturingselementen verhinderen. Gebruik de keuze Internet Opties in het Extra menu en selecteer het tabblag Beveiligingsinstellingen en druk op de Aangepaste niveau knop. Controleer dat de instellingen voor 'Het uitvoeren van ActiveX-besturingselementen' en 'Gedrag van binaire elementen en scripts' ingeschakeld zijn.\n\\n\nMomenteel zult u foutmeldingen zien in plaats van opgemaakte wiskunde",
          CantCreateXMLParser: "MathJax kan geen XML verwerker cre\u00EBren voor MathML. Controleer dat de 'ActiveX-besturingselementen die zijn gemarkeerd als veilig voor scripts uitvoeren in scripts' beveiligingsinstelling ingeschakeld is (gebruik de Internet Opties keuze in het Extra menu en selecteer het paneel Beveiliging, druk dan op de Aangepaste niveau knop om dit te controleren.)\n\\n\nHet zal voor MathML vergelijkingen niet mogelijk zijn verwerkt te worden door MathJax.",
          UnknownNodeType: "Onbekend knooptype: %1",
          UnexpectedTextNode: "Onverwachte tekstknoop: %1",
          ErrorParsingMathML: "Fout tijdens verwerken MathML",
          ParsingError: "Fout tijdens verwerken MathML: %1",
          MathMLSingleElement: "MathML moet bestaan uit \u00E9\u00E9n element",
          MathMLRootElement: "MathML moet bestaan uit een \u003Cmath\u003E element, niet %1"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/nl/MathML.js");
