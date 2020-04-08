/*
 * Copyright 2005-2008 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.lang.xpath.xslt.impl;

import com.intellij.util.xml.NanoXmlBuilder;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.xslt.XsltSupport;

public class XsltChecker implements NanoXmlBuilder {
    public enum LanguageLevel { NONE(null), V1(XPathVersion.V1), V2(XPathVersion.V2);
      private final XPathVersion myVersion;

      LanguageLevel(XPathVersion version) {
        myVersion = version;
      }

      public XPathVersion getXPathVersion() {
        return myVersion;
      }
    }

    enum State {
        YES, SIMPLIFIED, NO, POSSIBLY, POSSIBLY_SIMPLIFIED_SYNTAX, VERSION2
    }

    private State myState;

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
        if (("stylesheet".equals(name) || "transform".equals(name)) && XsltSupport.XSLT_NS.equals(nsURI)) {
            myState = State.POSSIBLY;
        } else {
            myState = State.POSSIBLY_SIMPLIFIED_SYNTAX;
        }
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
        if (myState == State.POSSIBLY) {
            if ("version".equals(key) && (nsURI == null || nsURI.length() == 0)) {
                checkVersion(value, State.YES);
                NanoXmlBuilder.stop();
            }
        } else if (myState == State.POSSIBLY_SIMPLIFIED_SYNTAX) {
            if ("version".equals(key) && XsltSupport.XSLT_NS.equals(nsURI)) {
                checkVersion(value, State.SIMPLIFIED);
                NanoXmlBuilder.stop();
            }
        }
    }

    private void checkVersion(String value, State yes) {
        if (isVersion1(value)) {
            myState = yes;
        } else if (isVersion2(value) || isVersion3(value)) {
            myState = State.VERSION2;
        } else {
            myState = State.NO;
        }
    }

    private static boolean isVersion1(String value) {
        return "1.0".equals(value) || "1.1".equals(value);
    }

    private static boolean isVersion2(String value) {
        return "2.0".equals(value);
    }

    private static boolean isVersion3(String value) {
        return "3.0".equals(value);
    }

    @Override
    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
        NanoXmlBuilder.stop();  // the first element (or its attrs) decides - stop here
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
        NanoXmlBuilder.stop();  // the first element (or its attrs) decides - stop here
    }

    public boolean isFullySupportedXsltFile() {
        return myState == State.YES || myState == State.SIMPLIFIED;
    }

    public static LanguageLevel getLanguageLevel(String value) {
        if (value == null) {
            return LanguageLevel.NONE;
        }
        if (isVersion1(value)) {
            return LanguageLevel.V1;
        }
        if (isVersion2(value)) {
            return LanguageLevel.V2;
        }
        if (isVersion3(value)) {
            return LanguageLevel.V2;
        }
        return LanguageLevel.NONE;
    }

    public LanguageLevel getLanguageLevel() {
        return myState == State.VERSION2 ? LanguageLevel.V2 :
                (isFullySupportedXsltFile() ? LanguageLevel.V1 : LanguageLevel.NONE);
    }
}