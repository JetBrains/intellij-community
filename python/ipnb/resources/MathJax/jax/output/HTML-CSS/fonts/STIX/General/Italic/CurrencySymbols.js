/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/fonts/STIX/General/Italic/CurrencySymbols.js
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

MathJax.Hub.Insert(
  MathJax.OutputJax['HTML-CSS'].FONTDATA.FONTS['STIXGeneral-italic'],
  {
    0x20A3: [653,0,611,8,645],         // FRENCH FRANC SIGN
    0x20A4: [670,8,500,10,517],        // LIRA SIGN
    0x20A7: [653,13,1149,0,1126],      // PESETA SIGN
    0x20AC: [664,12,500,16,538]        // EURO SIGN
  }
);

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].fontDir + "/General/Italic/CurrencySymbols.js");
