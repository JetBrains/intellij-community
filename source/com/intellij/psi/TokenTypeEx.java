/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.xml.DTDTokenType;
import com.intellij.psi.xml.XmlTokenType;

public interface TokenTypeEx extends
                             TokenType,
                           JavaTokenType,
                           JavaDocTokenType, 
                           XmlTokenType,
                           DTDTokenType {
}
