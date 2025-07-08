// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts

@OptIn(KaExperimentalApi::class)
fun KaSession.createReplacementForContextArgument(receiverValue: KaReceiverValue): String? {
    return when (val symbol = (receiverValue.unwrapSmartCasts() as? KaImplicitReceiverValue)?.symbol) {
        is KaReceiverParameterSymbol -> symbol.containingSymbol?.name?.asString()?.let { "this@$it" } ?: "this"

        is KaContextParameterSymbol -> {
            val name = symbol.name
            if (!name.isSpecial) {
                name.asString()
            } else {
                val returnTypeSymbol = symbol.returnType.symbol
                val superOfAnonymous = (returnTypeSymbol as? KaAnonymousObjectSymbol)?.superTypes?.firstOrNull()?.symbol
                val className =
                    ((superOfAnonymous ?: returnTypeSymbol) as? KaNamedClassSymbol)?.name
                        ?.takeUnless { it.isSpecial }?.asString()
                if (className != null) "contextOf<$className>()" else "contextOf()"
            }
        }

        else -> {
            null
        }
    }
}
