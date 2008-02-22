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

import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyClass;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.07.2005
 * Time: 17:20:57
 * To change this template use File | Settings | File Templates.
 */
public class ImportAnnotator extends PyAnnotator {
    @Override public void visitPyFromImportStatement(final PyFromImportStatement node) {
        if (node.isStarImport()) {
            if (node.getContainingElement(PyFunction.class) != null ||
                    node.getContainingElement(PyClass.class) != null) {
                getHolder().createWarningAnnotation(node, "import * only allowed at module level");
            }
        }
    }
}
