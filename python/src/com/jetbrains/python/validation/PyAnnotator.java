/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.validation;

import com.jetbrains.python.psi.PyElementVisitor;
import com.intellij.lang.annotation.AnnotationHolder;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.06.2005
 * Time: 13:43:02
 * To change this template use File | Settings | File Templates.
 */
public abstract class PyAnnotator extends PyElementVisitor {
    private AnnotationHolder _holder;

    public AnnotationHolder getHolder() {
        return _holder;
    }

    public void setHolder(AnnotationHolder holder) {
        _holder = holder;
    }
}
