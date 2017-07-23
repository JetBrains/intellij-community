/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/fonts/TeX/SansSerif/Regular/CombDiacritMarks.js
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
  MathJax.OutputJax['HTML-CSS'].FONTDATA.FONTS['MathJax_SansSerif'],
  {
    0x300: [694,-527,0,-417,-199],     // COMBINING GRAVE ACCENT
    0x301: [694,-527,0,-302,-84],      // COMBINING ACUTE ACCENT
    0x302: [694,-527,0,-422,-79],      // COMBINING CIRCUMFLEX ACCENT
    0x303: [677,-543,0,-417,-84],      // COMBINING TILDE
    0x304: [631,-552,0,-431,-70],      // COMBINING MACRON
    0x306: [694,-508,0,-427,-74],      // COMBINING BREVE
    0x307: [680,-576,0,-302,-198],     // COMBINING DOT ABOVE
    0x308: [680,-582,0,-397,-104],     // COMBINING DIAERESIS
    0x30A: [694,-527,0,-319,-99],      // COMBINING RING ABOVE
    0x30B: [694,-527,0,-399,-84],      // COMBINING DOUBLE ACUTE ACCENT
    0x30C: [654,-487,0,-422,-79]       // COMBINING CARON
  }
);

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].fontDir + "/SansSerif/Regular/CombDiacritMarks.js");
