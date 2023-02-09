package com.jetbrains.packagesearch.intellij.plugin.util

import com.jetbrains.packagesearch.intellij.plugin.data.LoadingContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

internal data class CombineLatest2<A, B>(val a: A, val b: B)
internal data class CombineLatest3<A, B, C>(val a: A, val b: B, val c: C)
internal data class CombineLatest4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
internal data class CombineLatest5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
internal data class CombineLatest6<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
internal data class CombineLatest7<A, B, C, D, E, F, G>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G)
internal data class CombineLatest8<A, B, C, D, E, F, G, H>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G, val h: H)
internal data class CombineLatest9<A, B, C, D, E, F, G, H, I>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
    val f: F,
    val g: G,
    val h: H,
    val i: I
)

internal fun <A, B, Z> combineLatest(
    flow1: Flow<A>,
    flow2: Flow<B>,
    loadingContainer: LoadingContainer? = null,
    transform: suspend (A, B) -> Z
): Flow<Z> {
    val loadingFlow = loadingContainer?.addLoadingState()
    return combine(flow1, flow2) { a, b -> CombineLatest2(a, b) }
        .mapLatest { loadingFlow?.whileLoading { transform(it.a, it.b) } ?: transform(it.a, it.b) }
}

internal fun <A, B, C, Z> combineLatest(
    flowA: Flow<A>,
    flowB: Flow<B>,
    flowC: Flow<C>,
    loadingContainer: LoadingContainer? = null,
    transform: suspend (A, B, C) -> Z
): Flow<Z> {
    val loadingFlow = loadingContainer?.addLoadingState()
    return combine(flowA, flowB, flowC) { a, b, c -> CombineLatest3(a, b, c) }
        .mapLatest { loadingFlow?.whileLoading { transform(it.a, it.b, it.c) } ?: transform(it.a, it.b, it.c) }
}

internal fun <A, B, C, D, Z> combineLatest(
    flowA: Flow<A>,
    flowB: Flow<B>,
    flowC: Flow<C>,
    flowD: Flow<D>,
    transform: suspend (A, B, C, D) -> Z
) = combine(flowA, flowB, flowC, flowD) { a, b, c, d -> CombineLatest4(a, b, c, d) }
    .mapLatest { transform(it.a, it.b, it.c, it.d) }

internal fun <A, B, C, D, E, Z> combineLatest(
    flowA: Flow<A>,
    flowB: Flow<B>,
    flowC: Flow<C>,
    flowD: Flow<D>,
    flowE: Flow<E>,
    transform: suspend (A, B, C, D, E) -> Z
) = combine(flowA, flowB, flowC, flowD, flowE) { a, b, c, d, e -> CombineLatest5(a, b, c, d, e) }
    .mapLatest { transform(it.a, it.b, it.c, it.d, it.e) }

@Suppress("UNCHECKED_CAST")
internal fun <A, B, C, D, E, F, Z> combineLatest(
    flowA: Flow<A>,
    flowB: Flow<B>,
    flowC: Flow<C>,
    flowD: Flow<D>,
    flowE: Flow<E>,
    flowF: Flow<F>,
    transform: suspend (A, B, C, D, E, F) -> Z
) = combine(flowA, flowB, flowC, flowD, flowE, flowF) { array ->
    CombineLatest6(
        array[0]!! as A,
        array[1]!! as B,
        array[2]!! as C,
        array[3]!! as D,
        array[4]!! as E,
        array[5]!! as F
    )
}.mapLatest { transform(it.a, it.b, it.c, it.d, it.e, it.f) }

@Suppress("UNCHECKED_CAST")
internal fun <A, B, C, D, E, F, G, Z> combineLatest(
    flowA: Flow<A>,
    flowB: Flow<B>,
    flowC: Flow<C>,
    flowD: Flow<D>,
    flowE: Flow<E>,
    flowF: Flow<F>,
    flowG: Flow<G>,
    transform: suspend (A, B, C, D, E, F, G) -> Z
) = combine(flowA, flowB, flowC, flowD, flowE, flowF, flowG) { array ->
    CombineLatest7(
        array[0]!! as A,
        array[1]!! as B,
        array[2]!! as C,
        array[3]!! as D,
        array[4]!! as E,
        array[5]!! as F,
        array[6]!! as G
    )
}.mapLatest { transform(it.a, it.b, it.c, it.d, it.e, it.f, it.g) }

@Suppress("UNCHECKED_CAST")
internal fun <A, B, C, D, E, F, G, H, Z> combineLatest(
    flowA: Flow<A>,
    flowB: Flow<B>,
    flowC: Flow<C>,
    flowD: Flow<D>,
    flowE: Flow<E>,
    flowF: Flow<F>,
    flowG: Flow<G>,
    flowH: Flow<H>,
    loadingContainer: LoadingContainer? = null,
    transform: suspend (A, B, C, D, E, F, G, H) -> Z
): Flow<Z> {
    val loadingFLow = loadingContainer?.addLoadingState()
    return combine(flowA, flowB, flowC, flowD, flowE, flowF, flowG, flowH) { array ->
        CombineLatest8(
            array[0]!! as A,
            array[1]!! as B,
            array[2]!! as C,
            array[3]!! as D,
            array[4]!! as E,
            array[5]!! as F,
            array[6]!! as G,
            array[7]!! as H
        )
    }.mapLatest {
        loadingFLow?.whileLoading { transform(it.a, it.b, it.c, it.d, it.e, it.f, it.g, it.h) }
            ?: transform(it.a, it.b, it.c, it.d, it.e, it.f, it.g, it.h)
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <A, B, C, D, E, F, G, H, I, Z> combineLatest(
    flowA: Flow<A>,
    flowB: Flow<B>,
    flowC: Flow<C>,
    flowD: Flow<D>,
    flowE: Flow<E>,
    flowF: Flow<F>,
    flowG: Flow<G>,
    flowH: Flow<H>,
    flowI: Flow<I>,
    transform: suspend (A, B, C, D, E, F, G, H, I) -> Z
) = combine(flowA, flowB, flowC, flowD, flowE, flowF, flowG, flowH) { array ->
    CombineLatest9(
        array[0]!! as A,
        array[1]!! as B,
        array[2]!! as C,
        array[3]!! as D,
        array[4]!! as E,
        array[5]!! as F,
        array[6]!! as G,
        array[7]!! as H,
        array[8]!! as I
    )
}.mapLatest { transform(it.a, it.b, it.c, it.d, it.e, it.f, it.g, it.h, it.i) }

