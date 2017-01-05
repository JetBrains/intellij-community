/*************************************************************
 *
 *  MathJax/jax/output/CommonHTML/fonts/TeX/Script-Regular.js
 *
 *  Copyright (c) 2015 The MathJax Consortium
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

(function (CHTML) {

var font = 'MathJax_Script';

CHTML.FONTDATA.FONTS[font] = {
  className: CHTML.FONTDATA.familyName(font),
  centerline: 211, ascent: 735, descent: 314,
  skew: {
    0x41: 0.389,
    0x42: 0.194,
    0x43: 0.278,
    0x44: 0.111,
    0x45: 0.139,
    0x46: 0.222,
    0x47: 0.25,
    0x48: 0.333,
    0x49: 0.333,
    0x4A: 0.417,
    0x4B: 0.361,
    0x4C: 0.306,
    0x4D: 0.444,
    0x4E: 0.389,
    0x4F: 0.167,
    0x50: 0.222,
    0x51: 0.278,
    0x52: 0.194,
    0x53: 0.333,
    0x54: 0.222,
    0x55: 0.25,
    0x56: 0.222,
    0x57: 0.25,
    0x58: 0.278,
    0x59: 0.194,
    0x5A: 0.306
  },
  0x20: [0,0,250,0,0],               // SPACE
  0x41: [717,8,803,35,1016],         // LATIN CAPITAL LETTER A
  0x42: [708,28,908,31,928],         // LATIN CAPITAL LETTER B
  0x43: [728,26,666,26,819],         // LATIN CAPITAL LETTER C
  0x44: [708,31,774,68,855],         // LATIN CAPITAL LETTER D
  0x45: [707,8,562,46,718],          // LATIN CAPITAL LETTER E
  0x46: [735,36,895,39,990],         // LATIN CAPITAL LETTER F
  0x47: [717,37,610,12,738],         // LATIN CAPITAL LETTER G
  0x48: [717,36,969,29,1241],        // LATIN CAPITAL LETTER H
  0x49: [717,17,809,59,946],         // LATIN CAPITAL LETTER I
  0x4A: [717,314,1052,92,1133],      // LATIN CAPITAL LETTER J
  0x4B: [717,37,914,29,1204],        // LATIN CAPITAL LETTER K
  0x4C: [717,17,874,14,1035],        // LATIN CAPITAL LETTER L
  0x4D: [721,50,1080,30,1216],       // LATIN CAPITAL LETTER M
  0x4E: [726,36,902,29,1208],        // LATIN CAPITAL LETTER N
  0x4F: [707,8,738,96,805],          // LATIN CAPITAL LETTER O
  0x50: [716,37,1013,90,1031],       // LATIN CAPITAL LETTER P
  0x51: [717,17,883,54,885],         // LATIN CAPITAL LETTER Q
  0x52: [717,17,850,-2,887],         // LATIN CAPITAL LETTER R
  0x53: [708,36,868,29,1016],        // LATIN CAPITAL LETTER S
  0x54: [735,37,747,92,996],         // LATIN CAPITAL LETTER T
  0x55: [717,17,800,55,960],         // LATIN CAPITAL LETTER U
  0x56: [717,17,622,56,850],         // LATIN CAPITAL LETTER V
  0x57: [717,17,805,46,1026],        // LATIN CAPITAL LETTER W
  0x58: [717,17,944,103,1131],       // LATIN CAPITAL LETTER X
  0x59: [716,17,710,57,959],         // LATIN CAPITAL LETTER Y
  0x5A: [717,16,821,83,1032],        // LATIN CAPITAL LETTER Z
  0xA0: [0,0,250,0,0]                // NO-BREAK SPACE
};

CHTML.fontLoaded("TeX/"+font.substr(8));

})(MathJax.OutputJax.CommonHTML);
