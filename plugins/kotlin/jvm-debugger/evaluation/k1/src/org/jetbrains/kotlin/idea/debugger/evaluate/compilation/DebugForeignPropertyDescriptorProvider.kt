// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.AdditionalContextProvider
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.backend.common.SimpleMemberScope
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.idea.core.util.externalDescriptors
import org.jetbrains.kotlin.idea.debugger.evaluate.getClassDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.org.objectweb.asm.Type

class DebugForeignPropertyDescriptorProvider(val codeFragment: KtCodeFragment, val debugProcess: DebugProcessImpl) {

    private val moduleDescriptor = DebugForeignPropertyModuleDescriptor

    fun supplyDebugForeignProperties() {
        val packageFragment = object : PackageFragmentDescriptorImpl(moduleDescriptor, FqName.ROOT) {
            val properties = createForeignPropertyDescriptors(this)
            override fun getMemberScope() = SimpleMemberScope(properties)
        }

        codeFragment.externalDescriptors = packageFragment.properties
    }

    private fun createForeignPropertyDescriptors(containingDeclaration: PackageFragmentDescriptor): List<PropertyDescriptor> {
        val result = mutableListOf<PropertyDescriptor>()
        val additionalContextElements = AdditionalContextProvider
            .getAllAdditionalContextElements(codeFragment.project, codeFragment.context)

        for ((name, signature, _, _) in additionalContextElements) {
            val kotlinType = convertType(signature)
            result += createForeignPropertyDescriptor(name, kotlinType, containingDeclaration)
        }

        return result
    }

    private fun createForeignPropertyDescriptor(
        name: String,
        type: KotlinType,
        containingDeclaration: PackageFragmentDescriptor
    ): PropertyDescriptor {
        val propertyDescriptor = ForeignPropertyDescriptor(containingDeclaration, name)
        propertyDescriptor.setType(type, emptyList(), null, null, emptyList())

        val getterDescriptor = PropertyGetterDescriptorImpl(
            propertyDescriptor,
            Annotations.EMPTY,
            Modality.FINAL,
            DescriptorVisibilities.PUBLIC,
            /* isDefault = */ false,
            /* isExternal = */ false,
            /* isInline = */ false,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            /* original = */ null,
            SourceElement.NO_SOURCE
        ).apply { initialize(type) }

        propertyDescriptor.initialize(getterDescriptor, null)
        return propertyDescriptor
    }

    private fun convertType(signature: String): KotlinType = convertType(Type.getType(signature))

    private fun convertType(asmType: Type): KotlinType {
        val builtIns = moduleDescriptor.builtIns
        return when (asmType.sort) {
            Type.VOID -> builtIns.unitType
            Type.LONG -> builtIns.longType
            Type.DOUBLE -> builtIns.doubleType
            Type.CHAR -> builtIns.charType
            Type.FLOAT -> builtIns.floatType
            Type.BYTE -> builtIns.byteType
            Type.INT -> builtIns.intType
            Type.BOOLEAN -> builtIns.booleanType
            Type.SHORT -> builtIns.shortType
            Type.ARRAY -> {
                when (asmType.elementType.sort) {
                    Type.VOID -> builtIns.getArrayType(Variance.INVARIANT, builtIns.unitType)
                    Type.LONG -> builtIns.getPrimitiveArrayKotlinType(PrimitiveType.LONG)
                    Type.DOUBLE -> builtIns.getPrimitiveArrayKotlinType(PrimitiveType.DOUBLE)
                    Type.CHAR -> builtIns.getPrimitiveArrayKotlinType(PrimitiveType.CHAR)
                    Type.FLOAT -> builtIns.getPrimitiveArrayKotlinType(PrimitiveType.FLOAT)
                    Type.BYTE -> builtIns.getPrimitiveArrayKotlinType(PrimitiveType.BYTE)
                    Type.INT -> builtIns.getPrimitiveArrayKotlinType(PrimitiveType.INT)
                    Type.BOOLEAN -> builtIns.getPrimitiveArrayKotlinType(PrimitiveType.BOOLEAN)
                    Type.SHORT -> builtIns.getPrimitiveArrayKotlinType(PrimitiveType.SHORT)
                    else -> builtIns.getArrayType(Variance.INVARIANT, convertReferenceType(asmType.elementType))
                }
            }
            Type.OBJECT -> convertReferenceType(asmType)
            else -> builtIns.anyType
        }
    }

    private fun convertReferenceType(asmType: Type): KotlinType {
        require(asmType.sort == Type.OBJECT)
        val project = codeFragment.project
        val classDescriptor = asmType.getClassDescriptor(GlobalSearchScope.allScope(project), mapBuiltIns = false)
            ?: return moduleDescriptor.builtIns.nullableAnyType
        return classDescriptor.defaultType
    }
}

private object DebugForeignPropertyModuleDescriptor : DeclarationDescriptorImpl(Annotations.EMPTY, Name.identifier("DebugLabelExtensions")),
                                                      ModuleDescriptor {
    override val builtIns: KotlinBuiltIns
        get() = DefaultBuiltIns.Instance

    override val stableName: Name
        get() = name

    override fun shouldSeeInternalsOf(targetModule: ModuleDescriptor) = false

    override fun getPackage(fqName: FqName): PackageViewDescriptor {
        return object : PackageViewDescriptor, DeclarationDescriptorImpl(Annotations.EMPTY, FqName.ROOT.shortNameOrSpecial()) {
            override fun getContainingDeclaration(): PackageViewDescriptor? = null

            override val fqName: FqName
                get() = FqName.ROOT

            override val memberScope: MemberScope
                get() = MemberScope.Empty

            override val module: ModuleDescriptor
                get() = this@DebugForeignPropertyModuleDescriptor

            override val fragments: List<PackageFragmentDescriptor>
                get() = emptyList()

            override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>, data: D): R {
                return visitor.visitPackageViewDescriptor(this, data)
            }
        }
    }

    override val platform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): Collection<FqName> {
        return emptyList()
    }

    override val allDependencyModules: List<ModuleDescriptor>
        get() = emptyList()

    override val expectedByModules: List<ModuleDescriptor>
        get() = emptyList()

    override val allExpectedByModules: Set<ModuleDescriptor>
        get() = emptySet()

    override fun <T> getCapability(capability: ModuleCapability<T>): T? = null

    override val isValid: Boolean
        get() = true

    override fun assertValid() {}
}

internal class ForeignPropertyDescriptor(
    containingDeclaration: DeclarationDescriptor,
    val propertyName: String
) : PropertyDescriptorImpl(
    containingDeclaration,
    null,
    Annotations.EMPTY,
    Modality.FINAL,
    DescriptorVisibilities.PUBLIC,
    /*isVar = */false,
    Name.identifier(propertyName),
    CallableMemberDescriptor.Kind.SYNTHESIZED,
    SourceElement.NO_SOURCE,
    /*lateInit = */false,
    /*isConst = */false,
    /*isExpect = */false,
    /*isActual = */false,
    /*isExternal = */false,
    /*isDelegated = */false
)
