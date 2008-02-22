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

import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.PyExceptBlock;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 13.06.2005
 * Time: 13:49:18
 * To change this template use File | Settings | File Templates.
 */
public class TryExceptAnnotator extends PyAnnotator {
    @Override public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
        PyExceptBlock[] exceptBlocks = node.getExceptBlocks();
        boolean haveDefaultExcept = false;
        for(PyExceptBlock block: exceptBlocks) {
            if (haveDefaultExcept) {
                getHolder().createErrorAnnotation(block, "default 'except': must be last");
            }
            if (block.getExceptClass() == null) {
                haveDefaultExcept = true;
            }
        }
    }
}
