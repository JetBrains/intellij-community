/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/jax/output/NativeMML/config.js
 *  
 *  Initializes the NativeMML OutputJax (the main definition is in
 *  MathJax/jax/input/NativeMML/jax.js, which is loaded when needed).
 *
 *  ---------------------------------------------------------------------
 *  
 *  Copyright (c) 2009-2015 The MathJax Consortium
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

MathJax.OutputJax.NativeMML = MathJax.OutputJax({
  id: "NativeMML",
  version: "2.6.1",
  directory: MathJax.OutputJax.directory + "/NativeMML",
  extensionDir: MathJax.OutputJax.extensionDir + "/NativeMML",
  
  config: {
    matchFontHeight: true,   // try to match math font height to surrounding font?
    scale: 100,              // scaling factor for all math
    minScaleAdjust: 50,      // minimum scaling to adjust to surrounding text
                             //  (since the code for that is a bit delicate)

    styles: {
      "div.MathJax_MathML": {
        "text-align": "center",
        margin: ".75em 0px"
      }
    }
  }
});

if (!MathJax.Hub.config.delayJaxRegistration)
  MathJax.OutputJax.NativeMML.Register("jax/mml");

MathJax.OutputJax.NativeMML.loadComplete("config.js");
