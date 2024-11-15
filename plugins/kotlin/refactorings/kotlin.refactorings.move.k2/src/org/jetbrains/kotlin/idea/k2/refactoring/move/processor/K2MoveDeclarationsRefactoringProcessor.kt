// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor

class K2MoveDeclarationsRefactoringProcessor(
    operationDescriptor: K2MoveOperationDescriptor.Declarations
) : K2BaseMoveDeclarationsRefactoringProcessor<K2MoveOperationDescriptor.Declarations>(operationDescriptor)