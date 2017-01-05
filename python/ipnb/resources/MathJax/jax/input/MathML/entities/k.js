/*************************************************************
 *
 *  MathJax/jax/output/HTML-CSS/entities/k.js
 *
 *  Copyright (c) 2010-2015 The MathJax Consortium
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

(function (MATHML) {
  MathJax.Hub.Insert(MATHML.Parse.Entity,{
    'KHcy': '\u0425',
    'KJcy': '\u040C',
    'Kappa': '\u039A',
    'Kcedil': '\u0136',
    'Kcy': '\u041A',
    'kcedil': '\u0137',
    'kcy': '\u043A',
    'kgreen': '\u0138',
    'khcy': '\u0445',
    'kjcy': '\u045C'
  });

  MathJax.Ajax.loadComplete(MATHML.entityDir+"/k.js");

})(MathJax.InputJax.MathML);
