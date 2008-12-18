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

import org.intellij.lang.xpath.xslt.XsltSupport;

import com.intellij.util.xml.NanoXmlUtil;

public class XsltChecker extends NanoXmlUtil.IXMLBuilderAdapter {
    enum State {
        YES, SIMPLIFIED, NO, POSSIBLY, POSSIBLY_SIMPLIFIED_SYNTAX
    }

    private static final RuntimeException STOP = new NanoXmlUtil.ParserStoppedException();

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
            if ("version".equals(key) && (nsURI == null || nsURI.equals(""))) {
                checkVersion(value, State.YES);
                stopFast();
            }
        } else if (myState == State.POSSIBLY_SIMPLIFIED_SYNTAX) {
            if ("version".equals(key) && nsURI.equals(XsltSupport.XSLT_NS)) {
                checkVersion(value, State.SIMPLIFIED);
                stopFast();
            }
        }
    }

    private static void stopFast() {
        // same as stop(), but pre-allocated instance avoids filling stacktrace information each time
        throw STOP;
    }

    private void checkVersion(String value, State yes) {
        if (isSupportedVersion(value)) {
            myState = yes;
        } else {
            myState = State.NO;
        }
    }

    public static boolean isSupportedVersion(String value) {
        return "1.0".equals(value) || "1.1".equals(value);
    }

    @Override
    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
        stopFast();  // the first element (or its attrs) decides - stop here
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
        stopFast();  // the first element (or its attrs) decides - stop here
    }

    public boolean isSupportedXsltFile() {
        return myState == State.YES || myState == State.SIMPLIFIED;
    }

    public boolean isSimplifiedSyntax() {
        return myState == State.SIMPLIFIED;
    }
}