/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package com.jetbrains.python.console.pydev;

public class InterpreterResponse {
    public final boolean more;

    public final boolean need_input;

    public InterpreterResponse(boolean more, boolean need_input) {
        this.more = more;
        this.need_input = need_input;
    }

}