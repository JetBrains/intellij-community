/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/qqq/MathMenu.js
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

MathJax.Localization.addTranslation("qqq","MathMenu",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          Show: "'Show math as' menu item. MathJax uses 'Math' as a distinct UI choice. Please translate it literally whenever possible.\n\nFollowed by the following menu subitems:\n* {{msg-mathjax|Mathmenu-MathMLcode}}\n* {{msg-mathjax|Mathmenu-Original}}\n* {{msg-mathjax|Mathmenu-Annotation}}\n* {{msg-mathjax|Mathmenu-texHints}} - checkbox label",
          MathMLcode: "This menu item from 'Show math as' shows the MathML code that MathJax has produced internally (sanitized, indented etc.)\n\nThe parent menu item is {{msg-mathjax|Mathmenu-Show}}.",
          OriginalMathML: "This menu item from 'Show math as' shows the MathML code as if that was originally in the page source",
          TeXCommands: "This menu item from 'Show math as' shows the TeX code if that was originally in the page source",
          AsciiMathInput: "This menu item from 'Show math as' shows the asciimath code if that was originally in the page source",
          Original: "This menu item from 'Show math as' shows the code that was originally in the page source but has no registered type.\n\nThis can happen when extensions add new input formats but fail to provide an adequate format name.\n\nThe parent menu item is {{msg-mathjax|Mathmenu-Show}}.\n{{Identical|Original form}}",
          ErrorMessage: "This menu item from 'Show math as' shows the error message if MathJax fails to process the source.\n{{Identical|Error message}}",
          Annotation: "This menu item from 'Show math as' allows to access possible annotations attached to a MathML formula.\n{{Identical|Annotation}}",
          TeX: "This is a menu item from the 'Annotation Menu' to show a TeX annotation.",
          StarMath: "This is a menu item from the 'Annotation Menu' to show a StarMath annotation (StarOffice, OpenOffice, LibreOffice).\n\nProgramming language used in MathJax.",
          Maple: "This is a menu item from the 'Annotation Menu' to show a Maple annotation.",
          ContentMathML: "This is a menu item from the 'Annotation Menu' to show a Content MathML annotation.\n\nThe MathML specification defines two versions: 'presentation' MathML (used in MathJax) and 'content' MathML (describes the semantics of the formula).",
          OpenMath: "This is a menu item from the 'Annotation Menu' to show the OpenMath annotation, an XML representation similar to Content MathML.",
          texHints: "This menu option from 'Show math as' adds comments to the code produced by 'MathMLCode'.\n\nUsed as checkbox label in the menu.",
          Settings: "'Math settings' menu item.",
          ZoomTrigger: "This menu from 'Math Settings' determines how MathJax's zoom is triggered.\n\nFollowed by the following menu items:\n* {{msg-mathjax|Mathmenu-Hover}}\n* {{msg-mathjax|Mathmenu-Click}}\n* {{msg-mathjax|Mathmenu-DoubleClick}}\n* {{msg-mathjax|Mathmenu-NoZoom}}\n* {{msg-mathjax|Mathmenu-TriggerRequires}} - label for the following checkboxes\n* {{msg-mathjax|Mathmenu-Option}} - checkbox label, for Mac\n* {{msg-mathjax|Mathmenu-Alt}} - checkbox label, for Windows\n* {{msg-mathjax|Mathmenu-Command}} - checkbox label, for Mac\n* {{msg-mathjax|Mathmenu-Control}} - checkbox label, for non-mac\n* {{msg-mathjax|Mathmenu-Shift}} - checkbox label",
          Hover: "This menu option from 'ZoomTrigger' indicates that the zoom is triggered when the mouse pass over a formula.",
          Click: "This menu option from 'ZoomTrigger' indicates that the zoom is triggered when one clicks on a formula.\n{{Identical|Click}}",
          DoubleClick: "This menu option from 'ZoomTrigger' indicates that the zoom is triggered when one double-clicks on a formula.",
          NoZoom: "This menu option from 'ZoomTrigger' indicates that the zoom is never triggered.",
          TriggerRequires: "This menu text from {{msg-mathjax|Mathmenu-ZoomTrigger}} describes if the ZoomTrigger requires additional keys.\n\nThe label is followed by the following menu items:\n* {{msg-mathjax|Mathmenu-Option}} - checkbox label, for Mac\n* {{msg-mathjax|Mathmenu-Alt}} - checkbox label, for Windows\n* {{msg-mathjax|Mathmenu-Command}} - checkbox label, for Mac\n* {{msg-mathjax|Mathmenu-Control}} - checkbox label, for non-Mac\n* {{msg-mathjax|Mathmenu-Shift}} - checkbox label",
          Option: "This menu option from {{msg-mathjax|Mathmenu-ZoomTrigger}} indicates that the OPTION key is needed (Apple-style).\n{{Identical|Options}}",
          Alt: "This menu option from {{msg-mathjax|Mathmenu-ZoomTrigger}} indicates that the ALT key is needed (Windows-style)",
          Command: "This menu option from {{msg-mathjax|Mathmenu-ZoomTrigger}} indicates that the COMMAND key is needed (Apple-style).\n{{Identical|Command}}",
          Control: "This menu option from {{msg-mathjax|Mathmenu-ZoomTrigger}} indicates that the CONTROL key is needed\n\n\"Control key\" is also known as \"Ctrl key\".",
          Shift: "This menu option from {{msg-mathjax|Mathmenu-ZoomTrigger}} indicates that the SHIFT key is needed",
          ZoomFactor: "Used as menu item which has the following sub menu items: 125%%, 133%%, 150%%, 175%%, 200%%, 250%%, 300%%, 400%%",
          Renderer: "Used as menu item which has the following sub menu items:\n* HTML-CSS\n* MathML\n* SVG",
          MPHandles: "Used as label in the menu.\n\nFollowed by the following menu items:\n* {{msg-mathjax|Mathmenu-MenuEvents}}\n* {{msg-mathjax|Mathmenu-MouseEvents}}\n* {{msg-mathjax|Mathmenu-MenuAndMouse}}",
          MenuEvents: "Option to let MathPlayer handle the contextual menu selections",
          MouseEvents: "Option to let MathPlayer handle the mouse clicks",
          MenuAndMouse: "Option to let MathPlayer handle Mouse and Menu Events",
          FontPrefs: "This menu item from 'Math Settings' allows selection of the font to use (and is mostly for development purposes) e.g. STIX",
          ForHTMLCSS: "Used as label in the menu.\n\nFollowed by the following radio box label:\n* {{msg-mathjax|Mathmenu-Auto}}",
          Auto: "{{Identical|Automatic}}",
          TeXLocal: "Used as label for the radio box in the menu.\n{{Related|Mathmenu-fonts}}",
          TeXWeb: "Used as label for the radio box in the menu.\n{{Related|Mathmenu-fonts}}",
          TeXImage: "Used as label for the radio box in the menu.\n{{Related|Mathmenu-fonts}}",
          STIXLocal: "Used as label for the radio box in the menu.\n{{Related|Mathmenu-fonts}}",
          STIXWeb: "{{Related|Mathmenu-fonts}}",
          AsanaMathWeb: "{{Related|Mathmenu-fonts}}",
          GyrePagellaWeb: "{{Related|Mathmenu-fonts}}",
          GyreTermesWeb: "{{Related|Mathmenu-fonts}}",
          LatinModernWeb: "{{Related|Mathmenu-fonts}}",
          NeoEulerWeb: "{{Related|Mathmenu-fonts}}",
          ContextMenu: "Used as menu item.\n\nFollowed by the following sub menu items:\n* MathJax - radio box label\n* {{msg-mathjax|Mathmenu-Browser}} - radio box label",
          Browser: "Used as menu item.\n\nThe parent menu item is {{msg-mathjax|Mathmenu-ContextMenu}}.\n{{Identical|Browser}}",
          Scale: "This menu item from 'Math Settings' allows users to set a scaling factor for the MathJax output (relative to the surrounding content).",
          Discoverable: "This menu option indicates whether the formulas should be highlighted when you pass the mouse over them.\n\nUsed as checkbox label in the menu.",
          Locale: "This menu item from 'Math Settings' allows to select a language. The language names are specified by the 'menuTitle' properties.\n\nThis menu item has the following sub menu items:\n* en\n* {{msg-mathjax|Mathmenu-LoadLocale}}\n{{Identical|Language}}",
          LoadLocale: "This allows the user to load the translation from a given URL.\n\nUsed as the menu item which has the parent menu item {{msg-mathjax|Mathmenu-Locale}}.",
          About: "This opens the 'About MathJax' popup.\n\nUsed as menu item.",
          Help: "This opens the 'MathJax Help' popup",
          localTeXfonts: "This is from the 'About MathJax' popup and is displayed when MathJax uses local MathJax TeX fonts.\n{{Related|Mathmenu-using}}",
          webTeXfonts: "This is from the 'About MathJax' popup and is displayed when MathJax uses Web versions of MathJax TeX fonts.\n{{Related|Mathmenu-using}}",
          imagefonts: "This is from the 'About MathJax' popup and is displayed when MathJax uses Image versions of MathJax TeX fonts.\n{{Related|Mathmenu-using}}",
          localSTIXfonts: "This is from the 'About MathJax' popup and is displayed when MathJax uses local MathJax STIX fonts.\n{{Related|Mathmenu-using}}",
          webSVGfonts: "This is from the 'About MathJax' popup and is displayed when MathJax uses SVG MathJax TeX fonts.\n{{Related|Mathmenu-using}}",
          genericfonts: "This is from the 'About MathJax' popup and is displayed when MathJax uses local generic fonts.\n{{Related|Mathmenu-using}}",
          wofforotffonts: "This is from the 'About MathJax' popup. woff/otf are names of font formats",
          eotffonts: "This is from the 'About MathJax' popup. eot is a name of font format",
          svgfonts: "This is from the 'About MathJax' popup. svg is a name of font format",
          WebkitNativeMMLWarning: "This is the WebKit warning displayed when a user changes the rendering output to native MathML via the MathJax menu.",
          MSIENativeMMLWarning: "This is the IE warning displayed when a user changes the rendering output to native MathML via the MathJax menu and does not have MathPlayer installed.",
          OperaNativeMMLWarning: "This is the Opera warning displayed when a user changes the rendering output to native MathML via the MathJax menu.",
          SafariNativeMMLWarning: "This is the Safari warning displayed when a user changes the rendering output to native MathML via the MathJax menu.",
          FirefoxNativeMMLWarning: "This is the Firefox warning displayed when a user changes the rendering output to native MathML via the MathJax menu.",
          MSIESVGWarning: "This is the IE warning displayed when a user changes the rendering output to SVG via the MathJax menu and uses an versions of IE.",
          LoadURL: "This is the prompt message for the 'LoadLocale' menu entry",
          BadURL: "This is the alert message when a bad URL is specified for 'LoadLocale'.",
          BadData: "This is the alert message when the translation data specified 'LoadLocale' fails to be loaded. The argument is the URL specified.",
          SwitchAnyway: "This is appended at the end of switch warnings.\n\nUsed for JavaScript \u003Ccode\u003Econfirm()\u003C/code\u003E.",
          ScaleMath: "This is the prompt message for the 'Scale all math' menu entry.\n\nUsed for JavaScript \u003Ccode\u003Eprompt()\u003C/code\u003E.",
          NonZeroScale: "This is the alert message when the scale specified to 'ScaleMath' is zero",
          PercentScale: "This is the alert message when the scale specified to 'ScaleMath' is not a percentage",
          IE8warning: "This this the confirm message displayed for when the user chooses to let MathPlayer control the contextual menu (IE8)",
          IE9warning: "This this the alert message displayed for when the user chooses to let MathPlayer control the contextual menu (IE9)",
          NoOriginalForm: "This is the alert box displayed when there are missing source formats for {{Msg-mathjax|Mathmenu-Show}}; see also {{Msg-mathjax|Mathmenu-Original}}.",
          Close: "Closing button in the 'Show math as' window.\n{{Identical|Close}}",
          EqSource: "This is the title of the 'Show math as' button.\n\nUsed in the \u003Ccode\u003E\u003Cnowiki\u003E\u003Ctitle\u003E\u003C/nowiki\u003E\u003C/code\u003E tag of the new window."
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/qqq/MathMenu.js");
