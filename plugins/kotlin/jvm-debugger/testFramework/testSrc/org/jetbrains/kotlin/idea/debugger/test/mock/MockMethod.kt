// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test.mock

import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.VirtualMachine

class MockMethod(
    private val name: String,
    private val virtualMachine: VirtualMachine,
) : Method {
    private var allLineLocations = listOf<Location>()
    private var variables = listOf<LocalVariable>()
    fun updateContents(allLineLocations: List<Location>, variables: List<LocalVariable>) {
        this.allLineLocations = allLineLocations
        this.variables = variables
    }

    override fun name() = name
    override fun allLineLocations() = allLineLocations
    override fun virtualMachine() = virtualMachine

    // JDI will filter out local variables with names that - in Java - would correspond
    // to dispatch receivers of the current or enclosing class. Compare from
    // createVariables and createVariable1_4 in [com.sun.tools.jdi.ConcreteMethodImpl].
    override fun variables() = variables.filter { variable ->
        !variable.name().startsWith("this$") && variable.name() != "this"
    }

    override fun isSynthetic() = throw UnsupportedOperationException()
    override fun isFinal() = throw UnsupportedOperationException()
    override fun isStatic() = throw UnsupportedOperationException()
    override fun declaringType() = throw UnsupportedOperationException()
    override fun signature() = throw UnsupportedOperationException()
    override fun genericSignature() = throw UnsupportedOperationException()
    override fun variablesByName(name: String?) = throw UnsupportedOperationException()
    override fun bytecodes() = throw UnsupportedOperationException()
    override fun isBridge() = throw UnsupportedOperationException()
    override fun isObsolete() = throw UnsupportedOperationException()
    override fun isSynchronized() = throw UnsupportedOperationException()
    override fun allLineLocations(stratum: String?, sourceName: String?) = throw UnsupportedOperationException()
    override fun isNative() = throw UnsupportedOperationException()
    override fun locationOfCodeIndex(codeIndex: Long) = throw UnsupportedOperationException()
    override fun arguments() = throw UnsupportedOperationException()
    override fun isAbstract() = throw UnsupportedOperationException()
    override fun isVarArgs() = throw UnsupportedOperationException()
    override fun returnTypeName() = throw UnsupportedOperationException()
    override fun argumentTypes() = throw UnsupportedOperationException()
    override fun isConstructor() = throw UnsupportedOperationException()
    override fun locationsOfLine(lineNumber: Int) = throw UnsupportedOperationException()
    override fun locationsOfLine(stratum: String?, sourceName: String?, lineNumber: Int) = throw UnsupportedOperationException()
    override fun argumentTypeNames() = throw UnsupportedOperationException()
    override fun returnType() = throw UnsupportedOperationException()
    override fun isStaticInitializer() = throw UnsupportedOperationException()
    override fun location() = throw UnsupportedOperationException()
    override fun compareTo(other: Method?) = throw UnsupportedOperationException()
    override fun isPackagePrivate() = throw UnsupportedOperationException()
    override fun isPrivate() = throw UnsupportedOperationException()
    override fun isProtected() = throw UnsupportedOperationException()
    override fun isPublic() = throw UnsupportedOperationException()
    override fun modifiers() = throw UnsupportedOperationException()
}
