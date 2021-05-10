// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.resolve.DescriptorUtils;

import java.util.Collection;

public class RecursiveDescriptorProcessor {

    public static <D> boolean process(
            @NotNull DeclarationDescriptor descriptor,
            D data,
            @NotNull DeclarationDescriptorVisitor<Boolean, D> visitor
    ) {
        return descriptor.accept(new RecursiveVisitor<>(visitor), data);
    }

    private static class RecursiveVisitor<D> implements DeclarationDescriptorVisitor<Boolean, D> {

        private final DeclarationDescriptorVisitor<Boolean, D> worker;

        private RecursiveVisitor(@NotNull DeclarationDescriptorVisitor<Boolean, D> worker) {
            this.worker = worker;
        }

        private boolean visitChildren(Collection<? extends DeclarationDescriptor> descriptors, D data) {
            for (DeclarationDescriptor descriptor : descriptors) {
                if (!descriptor.accept(this, data)) return false;
            }
            return true;
        }

        private boolean visitChildren(@Nullable DeclarationDescriptor descriptor, D data) {
            if (descriptor == null) return true;

            return descriptor.accept(this, data);
        }

        private boolean applyWorker(@NotNull DeclarationDescriptor descriptor, D data) {
            return descriptor.accept(worker, data);
        }

        private boolean processCallable(CallableDescriptor descriptor, D data) {
            return applyWorker(descriptor, data)
                   && visitChildren(descriptor.getTypeParameters(), data)
                   && visitChildren(descriptor.getExtensionReceiverParameter(), data)
                   && visitChildren(descriptor.getValueParameters(), data);
        }

        @Override
        public Boolean visitPackageFragmentDescriptor(PackageFragmentDescriptor descriptor, D data) {
            return applyWorker(descriptor, data)
                   && visitChildren(DescriptorUtils.getAllDescriptors(descriptor.getMemberScope()), data);
        }

        @Override
        public Boolean visitPackageViewDescriptor(PackageViewDescriptor descriptor, D data) {
            return applyWorker(descriptor, data)
                   && visitChildren(DescriptorUtils.getAllDescriptors(descriptor.getMemberScope()), data);
        }

        @Override
        public Boolean visitVariableDescriptor(VariableDescriptor descriptor, D data) {
            return processCallable(descriptor, data);
        }

        @Override
        public Boolean visitPropertyDescriptor(PropertyDescriptor descriptor, D data) {
            return processCallable(descriptor, data)
                   && visitChildren(descriptor.getGetter(), data)
                   && visitChildren(descriptor.getSetter(), data);
        }

        @Override
        public Boolean visitFunctionDescriptor(FunctionDescriptor descriptor, D data) {
            return processCallable(descriptor, data);
        }

        @Override
        public Boolean visitTypeParameterDescriptor(TypeParameterDescriptor descriptor, D data) {
            return applyWorker(descriptor, data);
        }

        @Override
        public Boolean visitClassDescriptor(ClassDescriptor descriptor, D data) {
            return applyWorker(descriptor, data)
                   && visitChildren(descriptor.getThisAsReceiverParameter(), data)
                   && visitChildren(descriptor.getConstructors(), data)
                   && visitChildren(descriptor.getTypeConstructor().getParameters(), data)
                   && visitChildren(DescriptorUtils.getAllDescriptors(descriptor.getDefaultType().getMemberScope()), data);
        }

        @Override
        public Boolean visitTypeAliasDescriptor(TypeAliasDescriptor descriptor, D data) {
            return applyWorker(descriptor, data)
                   && visitChildren(descriptor.getDeclaredTypeParameters(), data);
        }

        @Override
        public Boolean visitModuleDeclaration(ModuleDescriptor descriptor, D data) {
            return applyWorker(descriptor, data)
                   && visitChildren(descriptor.getPackage(FqName.ROOT), data);
        }

        @Override
        public Boolean visitConstructorDescriptor(ConstructorDescriptor constructorDescriptor, D data) {
            return visitFunctionDescriptor(constructorDescriptor, data);
        }

        @Override
        public Boolean visitScriptDescriptor(ScriptDescriptor scriptDescriptor, D data) {
            return visitClassDescriptor(scriptDescriptor, data);
        }

        @Override
        public Boolean visitValueParameterDescriptor(ValueParameterDescriptor descriptor, D data) {
            return visitVariableDescriptor(descriptor, data);
        }

        @Override
        public Boolean visitPropertyGetterDescriptor(PropertyGetterDescriptor descriptor, D data) {
            return visitFunctionDescriptor(descriptor, data);
        }

        @Override
        public Boolean visitPropertySetterDescriptor(PropertySetterDescriptor descriptor, D data) {
            return visitFunctionDescriptor(descriptor, data);
        }

        @Override
        public Boolean visitReceiverParameterDescriptor(ReceiverParameterDescriptor descriptor, D data) {
            return applyWorker(descriptor, data);
        }
    }
}
