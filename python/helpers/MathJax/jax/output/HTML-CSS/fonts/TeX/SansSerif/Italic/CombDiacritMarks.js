/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/fonts/TeX/SansSerif/Italic/CombDiacritMarks.js
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
  MathJax.OutputJax['HTML-CSS'].FONTDATA.FONTS['MathJax_SansSerif-italic'],
  {
    0x300: [694,-527,0,-270,-87],      // COMBINING GRAVE ACCENT
    0x301: [694,-527,0,-190,63],       // COMBINING ACUTE ACCENT
    0x302: [694,-527,0,-310,33],       // COMBINING CIRCUMFLEX ACCENT
    0x303: [677,-543,0,-301,60],       // COMBINING TILDE
    0x304: [631,-552,0,-314,64],       // COMBINING MACRON
    0x306: [694,-508,0,-284,73],       // COMBINING BREVE
    0x307: [680,-576,0,-180,-54],      // COMBINING DOT ABOVE
    0x308: [680,-582,0,-273,40],       // COMBINING DIAERESIS
    0x30A: [693,-527,0,-227,-2],       // COMBINING RING ABOVE
    0x30B: [694,-527,0,-287,63],       // COMBINING DOUBLE ACUTE ACCENT
    0x30C: [654,-487,0,-283,60]        // COMBINING CARON
  }
);

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].fontDir + "/SansSerif/Italic/CombDiacritMarks.js");
