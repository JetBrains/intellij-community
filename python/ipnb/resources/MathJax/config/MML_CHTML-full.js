/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  /MathJax/unpacked/config/MML_CHTML-full.js
 *  
 *  Copyright (c) 2010-2015 The MathJax Consortium
 *
 *  Part of the MathJax library.
 *  See http://www.mathjax.org for details.
 * 
 *  Licensed under the Apache License, Version 2.0;
 *  you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

MathJax.Hub.Config({
  extensions: ["mml2jax.js","MathEvents.js","MathZoom.js","MathMenu.js","toMathML.js","fast-preview.js","AssistiveMML.js"],
  jax: ["input/MathML","output/CommonHTML","output/PreviewHTML"]
});

MathJax.Ajax.loadComplete("[MathJax]/config/MML_CHTML-full.js");
