/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/fonts/STIX-Web/Shapes/BoldItalic/Main.js
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

MathJax.OutputJax['HTML-CSS'].FONTDATA.FONTS['STIXMathJax_Shapes-bold-italic'] = {
  directory: 'Shapes/BoldItalic',
  family: 'STIXMathJax_Shapes',
  weight: 'bold',
  style: 'italic',
  testString: '\u00A0\u2423',
  0x20: [0,0,250,0,0],
  0xA0: [0,0,250,0,0],
  0x2423: [31,120,500,40,460]
};

MathJax.Callback.Queue(
  ["initFont",MathJax.OutputJax["HTML-CSS"],"STIXMathJax_Shapes-bold-italic"],
  ["loadComplete",MathJax.Ajax,MathJax.OutputJax["HTML-CSS"].fontDir+"/Shapes/BoldItalic/Main.js"]
);
