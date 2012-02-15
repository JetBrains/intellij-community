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

import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.xslt.XsltSupport;

import com.intellij.util.xml.NanoXmlUtil;

public class XsltChecker extends NanoXmlUtil.IXMLBuilderAdapter {
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
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
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
                stop();
            }
        } else if (myState == State.POSSIBLY_SIMPLIFIED_SYNTAX) {
            if ("version".equals(key) && XsltSupport.XSLT_NS.equals(nsURI)) {
                checkVersion(value, State.SIMPLIFIED);
                stop();
            }
        }
    }

    private void checkVersion(String value, State yes) {
        if (isVersion1(value)) {
            myState = yes;
        } else if (isVersion2(value)) {
            myState = State.VERSION2;
        } else {
            myState = State.NO;
        }
    }

    public static boolean isSupportedVersion(String value) {
        return isVersion1(value) || isVersion2(value);
    }

    public static boolean isVersion1(String value) {
        return "1.0".equals(value) || "1.1".equals(value);
    }

    public static boolean isVersion2(String value) {
        return "2.0".equals(value);
    }

    @Override
    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
        stop();  // the first element (or its attrs) decides - stop here
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
        stop();  // the first element (or its attrs) decides - stop here
    }

    public boolean isFullySupportedXsltFile() {
        return myState == State.YES || myState == State.SIMPLIFIED;
    }

    public boolean isSimplifiedSyntax() {
        return myState == State.SIMPLIFIED;
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
        return LanguageLevel.NONE;
    }

    public LanguageLevel getLanguageLevel() {
        return myState == State.VERSION2 ? LanguageLevel.V2 :
                (isFullySupportedXsltFile() ? LanguageLevel.V1 : LanguageLevel.NONE);
    }
}