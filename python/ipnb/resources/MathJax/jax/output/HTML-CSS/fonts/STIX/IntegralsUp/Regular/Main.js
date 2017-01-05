/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/fonts/STIX/IntegralsUp/Regular/Main.js
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

MathJax.OutputJax['HTML-CSS'].FONTDATA.FONTS['STIXIntegralsUp'] = {
  directory: 'IntegralsUp/Regular',
  family: 'STIXIntegralsUp',
  Ranges: [
    [0x20,0x20,"All"],
    [0xA0,0xA0,"All"],
    [0x222B,0x2233,"All"],
    [0x2A0B,0x2A1C,"All"]
  ],
  0x222B: [824,320,366,59,408],      // INTEGRAL
  0x222E: [824,320,397,35,486]       // CONTOUR INTEGRAL
};

MathJax.OutputJax["HTML-CSS"].initFont("STIXIntegralsUp");

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].fontDir + "/IntegralsUp/Regular/Main.js");
