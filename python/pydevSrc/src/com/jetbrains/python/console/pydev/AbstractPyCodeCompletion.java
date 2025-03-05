// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.console.pydev;


import com.intellij.openapi.util.text.StringUtil;

public final class AbstractPyCodeCompletion  {
    public static final int LOOKING_FOR_INSTANCE_UNDEFINED=0;
    public static final int LOOKING_FOR_INSTANCED_VARIABLE=1;
    public static final int LOOKING_FOR_UNBOUND_VARIABLE=2;
    public static final int LOOKING_FOR_CLASSMETHOD_VARIABLE=3;
    public static final int LOOKING_FOR_ASSIGN = 4;

    /**
     * @return a string with the arguments to be shown for the given element.
     *
     * E.g.: >>(self, a, b)<< Returns (a, b)
     */
    public static String getArgs(String argsReceived, int type, int lookingFor) {
        String args = "";
        boolean lookingForInstance = lookingFor==LOOKING_FOR_INSTANCE_UNDEFINED ||
                                     lookingFor==LOOKING_FOR_INSTANCED_VARIABLE ||
                                     lookingFor==LOOKING_FOR_ASSIGN;
        String trimmed = argsReceived.trim();
        if(!trimmed.isEmpty()) {
            StringBuilder buffer = new StringBuilder("(");

            char c = trimmed.charAt(0);
            if (c == '(') {
                trimmed = trimmed.substring(1);
            }
            if (!trimmed.isEmpty()) {
                c = trimmed.charAt(trimmed.length() - 1);
                if (c == ')') {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
            }
            trimmed = trimmed.trim();


            //Now, if it starts with self or cls, we may have to remove it.
            String temp;
            if (lookingForInstance && trimmed.startsWith("self")) {
                temp = trimmed.substring(4);
            }
            else if (trimmed.startsWith("cls")) {
                temp = trimmed.substring(3);
            }
            else {
                temp = trimmed;
            }
            temp = temp.trim();
            if (!temp.isEmpty()) {
                //but only if it wasn't a self or cls followed by a valid identifier part.
                if (!Character.isJavaIdentifierPart(temp.charAt(0))) {
                    trimmed = temp;
                }
            }
            else {
                trimmed = temp;
            }


            trimmed = trimmed.trim();
            trimmed = StringUtil.trimStart(trimmed, ",");
            trimmed = trimmed.trim();
            buffer.append(trimmed);

            buffer.append(")");
            args = buffer.toString();
        } else if (type == IToken.TYPE_FUNCTION){
            args = "()";
        }

        return args;
    }


}