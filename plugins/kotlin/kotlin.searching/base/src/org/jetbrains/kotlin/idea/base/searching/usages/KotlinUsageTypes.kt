// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages

import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

object KotlinUsageTypes {

    internal fun UsageTypeEnum.toUsageType(): UsageType = when (this) {
        UsageTypeEnum.TYPE_CONSTRAINT -> TYPE_CONSTRAINT
        UsageTypeEnum.VALUE_PARAMETER_TYPE -> VALUE_PARAMETER_TYPE
        UsageTypeEnum.NON_LOCAL_PROPERTY_TYPE -> NON_LOCAL_PROPERTY_TYPE
        UsageTypeEnum.FUNCTION_RETURN_TYPE -> FUNCTION_RETURN_TYPE
        UsageTypeEnum.SUPER_TYPE -> SUPER_TYPE
        UsageTypeEnum.IS -> IS
        UsageTypeEnum.CLASS_OBJECT_ACCESS -> CLASS_OBJECT_ACCESS
        UsageTypeEnum.COMPANION_OBJECT_ACCESS -> COMPANION_OBJECT_ACCESS
        UsageTypeEnum.EXTENSION_RECEIVER_TYPE -> EXTENSION_RECEIVER_TYPE
        UsageTypeEnum.SUPER_TYPE_QUALIFIER -> SUPER_TYPE_QUALIFIER
        UsageTypeEnum.TYPE_ALIAS -> TYPE_ALIAS

        UsageTypeEnum.FUNCTION_CALL -> FUNCTION_CALL
        UsageTypeEnum.IMPLICIT_GET -> IMPLICIT_GET
        UsageTypeEnum.IMPLICIT_SET -> IMPLICIT_SET
        UsageTypeEnum.IMPLICIT_INVOKE -> IMPLICIT_INVOKE
        UsageTypeEnum.IMPLICIT_ITERATION -> IMPLICIT_ITERATION
        UsageTypeEnum.PROPERTY_DELEGATION -> PROPERTY_DELEGATION
        UsageTypeEnum.SUPER_DELEGATION -> SUPER_DELEGATION

        UsageTypeEnum.RECEIVER -> RECEIVER
        UsageTypeEnum.DELEGATE -> DELEGATE

        UsageTypeEnum.PACKAGE_DIRECTIVE -> PACKAGE_DIRECTIVE
        UsageTypeEnum.PACKAGE_MEMBER_ACCESS -> PACKAGE_MEMBER_ACCESS

        UsageTypeEnum.CALLABLE_REFERENCE -> CALLABLE_REFERENCE

        UsageTypeEnum.READ -> UsageType.READ
        UsageTypeEnum.WRITE -> UsageType.WRITE
        UsageTypeEnum.CLASS_IMPORT -> UsageType.CLASS_IMPORT
        UsageTypeEnum.CLASS_LOCAL_VAR_DECLARATION -> UsageType.CLASS_LOCAL_VAR_DECLARATION
        UsageTypeEnum.TYPE_PARAMETER -> UsageType.TYPE_PARAMETER
        UsageTypeEnum.CLASS_CAST_TO -> UsageType.CLASS_CAST_TO
        UsageTypeEnum.ANNOTATION -> UsageType.ANNOTATION
        UsageTypeEnum.CLASS_NEW_OPERATOR -> UsageType.CLASS_NEW_OPERATOR
        UsageTypeEnum.NAMED_ARGUMENT -> NAMED_ARGUMENT

        UsageTypeEnum.USAGE_IN_STRING_LITERAL -> UsageType.LITERAL_USAGE

        UsageTypeEnum.CONSTRUCTOR_DELEGATION_REFERENCE -> CONSTRUCTOR_DELEGATION_REFERENCE
    }

    // types
    private val TYPE_CONSTRAINT = UsageType(KotlinBundle.messagePointer("find.usages.type.type.constraint"))
    private val VALUE_PARAMETER_TYPE = UsageType(KotlinBundle.messagePointer("find.usages.type.value.parameter.type"))
    private val NON_LOCAL_PROPERTY_TYPE = UsageType(KotlinBundle.messagePointer("find.usages.type.nonLocal.property.type"))
    private val FUNCTION_RETURN_TYPE = UsageType(KotlinBundle.messagePointer("find.usages.type.function.return.type"))
    private val SUPER_TYPE = UsageType(KotlinBundle.messagePointer("find.usages.type.superType"))
    val IS = UsageType(KotlinBundle.messagePointer("find.usages.type.is"))
    private val CLASS_OBJECT_ACCESS = UsageType(KotlinBundle.messagePointer("find.usages.type.class.object"))
    private val COMPANION_OBJECT_ACCESS = UsageType(KotlinBundle.messagePointer("find.usages.type.companion.object"))
    private val EXTENSION_RECEIVER_TYPE = UsageType(KotlinBundle.messagePointer("find.usages.type.extension.receiver.type"))
    private val SUPER_TYPE_QUALIFIER = UsageType(KotlinBundle.messagePointer("find.usages.type.super.type.qualifier"))
    private val TYPE_ALIAS = UsageType(KotlinBundle.messagePointer("find.usages.type.type.alias"))

    // functions
    private val FUNCTION_CALL = UsageType(KotlinBundle.messagePointer("find.usages.type.function.call"))
    private val IMPLICIT_GET = UsageType(KotlinBundle.messagePointer("find.usages.type.implicit.get"))
    private val IMPLICIT_SET = UsageType(KotlinBundle.messagePointer("find.usages.type.implicit.set"))
    private val IMPLICIT_INVOKE = UsageType(KotlinBundle.messagePointer("find.usages.type.implicit.invoke"))
    private val IMPLICIT_ITERATION = UsageType(KotlinBundle.messagePointer("find.usages.type.implicit.iteration"))
    private val PROPERTY_DELEGATION = UsageType(KotlinBundle.messagePointer("find.usages.type.property.delegation"))
    private val SUPER_DELEGATION = UsageType(UsageViewBundle.messagePointer("usage.type.delegate.to.super.method"))

    // values
    val RECEIVER = UsageType(KotlinBundle.messagePointer("find.usages.type.receiver"))
    private val DELEGATE = UsageType(KotlinBundle.messagePointer("find.usages.type.delegate"))

    // packages
    val PACKAGE_DIRECTIVE = UsageType(KotlinBundle.messagePointer("find.usages.type.packageDirective"))
    private val PACKAGE_MEMBER_ACCESS = UsageType(KotlinBundle.messagePointer("find.usages.type.packageMemberAccess"))

    // common usage types
    private val CALLABLE_REFERENCE = UsageType(KotlinBundle.messagePointer("find.usages.type.callable.reference"))
    private val CONSTRUCTOR_DELEGATION_REFERENCE = UsageType(KotlinBundle.messagePointer("find.usages.type.constructor.delegation.reference"))
    private val NAMED_ARGUMENT = UsageType(KotlinBundle.messagePointer("find.usages.type.named.argument"))
}
