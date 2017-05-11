/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/fonts/Neo-Euler/Arrows/Regular/Main.js
 *  
 *  Copyright (c) 2013-2015 The MathJax Consortium
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
 */

MathJax.OutputJax['HTML-CSS'].FONTDATA.FONTS['NeoEulerMathJax_Arrows'] = {
  directory: 'Arrows/Regular',
  family: 'NeoEulerMathJax_Arrows',
  testString: '\u00A0\u21A4\u27FB\u27FD\u27FE',
  0x20: [0,0,333,0,0],
  0xA0: [0,0,333,0,0],
  0x21A4: [500,0,1000,56,944],
  0x27FB: [500,0,1690,56,1634],
  0x27FD: [598,98,1700,76,1643],
  0x27FE: [598,98,1700,75,1643]
};

MathJax.Callback.Queue(
  ["initFont",MathJax.OutputJax["HTML-CSS"],"NeoEulerMathJax_Arrows"],
  ["loadComplete",MathJax.Ajax,MathJax.OutputJax["HTML-CSS"].fontDir+"/Arrows/Regular/Main.js"]
);
