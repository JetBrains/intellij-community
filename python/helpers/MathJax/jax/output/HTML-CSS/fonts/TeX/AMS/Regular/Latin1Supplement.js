/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/fonts/TeX/AMS/Regular/Latin1Supplement.js
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
  MathJax.OutputJax['HTML-CSS'].FONTDATA.FONTS['MathJax_AMS'],
  {
    0xA0: [0,0,250,0,0],               // NO-BREAK SPACE
    0xA5: [683,0,750,11,738],          // YEN SIGN
    0xAE: [709,175,947,32,915],        // REGISTERED SIGN
    0xF0: [749,21,556,42,509]          // LATIN SMALL LETTER ETH
  }
);

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].fontDir + "/AMS/Regular/Latin1Supplement.js");
