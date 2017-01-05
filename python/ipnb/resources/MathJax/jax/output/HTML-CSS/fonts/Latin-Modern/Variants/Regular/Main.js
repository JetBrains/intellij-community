/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/fonts/Latin-Modern/Variants/Regular/Main.js
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

MathJax.OutputJax['HTML-CSS'].FONTDATA.FONTS['LatinModernMathJax_Variants'] = {
  directory: 'Variants/Regular',
  family: 'LatinModernMathJax_Variants',
  testString: '\u00A0\u2032\u2033\u2034\u2035\u2036\u2037\u2057',
  0x20: [0,0,332,0,0],
  0xA0: [0,0,332,0,0],
  0x2032: [549,-96,407,67,340],
  0x2033: [549,-96,647,67,580],
  0x2034: [549,-96,887,67,820],
  0x2035: [549,-96,407,67,340],
  0x2036: [549,-96,647,67,580],
  0x2037: [549,-96,887,67,820],
  0x2057: [549,-96,1127,67,1060]
};

MathJax.Callback.Queue(
  ["initFont",MathJax.OutputJax["HTML-CSS"],"LatinModernMathJax_Variants"],
  ["loadComplete",MathJax.Ajax,MathJax.OutputJax["HTML-CSS"].fontDir+"/Variants/Regular/Main.js"]
);
