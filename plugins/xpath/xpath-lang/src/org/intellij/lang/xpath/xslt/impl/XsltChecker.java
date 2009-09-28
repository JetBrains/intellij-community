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
    public enum SupportLevel { FULL, NONE, PARTIAL }

    enum State {
        YES, SIMPLIFIED, NO, POSSIBLY, POSSIBLY_SIMPLIFIED_SYNTAX, PARTIAL
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
        if (isFullySupportedVersion(value)) {
            myState = yes;
        } else if (isPartiallySupportedVersion(value)) {
            myState = State.PARTIAL;
        } else {
            myState = State.NO;
        }
    }

    public static boolean isSupportedVersion(String value) {
        return isFullySupportedVersion(value) || isPartiallySupportedVersion(value);
    }

    public static boolean isFullySupportedVersion(String value) {
        return "1.0".equals(value) || "1.1".equals(value);
    }

    public static boolean isPartiallySupportedVersion(String value) {
        return "2.0".equals(value);
    }

    @Override
    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
        stopFast();  // the first element (or its attrs) decides - stop here
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
        stopFast();  // the first element (or its attrs) decides - stop here
    }

    public boolean isFullySupportedXsltFile() {
        return myState == State.YES || myState == State.SIMPLIFIED;
    }

    public boolean isSimplifiedSyntax() {
        return myState == State.SIMPLIFIED;
    }

    public static SupportLevel getSupportLevel(String value) {
        if (value == null) {
            return SupportLevel.NONE;
        }
        if (isFullySupportedVersion(value)) {
            return SupportLevel.FULL;
        }
        if (isPartiallySupportedVersion(value)) {
            return SupportLevel.PARTIAL;
        }
        return SupportLevel.NONE;
    }

    public SupportLevel getSupportLevel() {
        return myState == State.PARTIAL ? SupportLevel.PARTIAL :
                (isFullySupportedXsltFile() ? SupportLevel.FULL : SupportLevel.NONE);
    }
}