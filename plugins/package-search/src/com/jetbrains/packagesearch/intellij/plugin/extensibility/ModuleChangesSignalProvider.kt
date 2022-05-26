package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.stream.consumeAsFlow
import java.util.function.Supplier
import kotlin.streams.toList

/**
 * Extension point that allows to listen to module changes.
 */
interface ModuleChangesSignalProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<ModuleChangesSignalProvider> =
            ExtensionPointName.create("com.intellij.packagesearch.moduleChangesSignalProvider")

        /**
         * Returns a [Flow] of signals combining all registered [ModuleChangesSignalProvider].
         */
        internal fun listenToModuleChanges(project: Project): Flow<Unit> = callbackFlow {
            val subs = extensionPointName.extensions(project).toList().map {
                it.registerModuleChangesListener(project) { trySend(Unit) }
            }
            awaitClose { subs.unsubscribeAll() }
        }
    }

    /**
     * Register a [listener] that is invoked every time the implemented build systems signals a change
     * in the module structure. See [AbstractMessageBusModuleChangesSignalProvider] and its implementations
     * for examples on how to register module changes.
     */
    fun registerModuleChangesListener(project: Project, listener: Runnable): Subscription
}

/**
 * Extension point that allows to listen to module changes using Kotlin [Flow]s.
 */
interface FlowModuleChangesSignalProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<FlowModuleChangesSignalProvider> =
            ExtensionPointName.create("com.intellij.packagesearch.flowModuleChangesSignalProvider")

        /**
         * Returns a [Flow] of signals combining all registered [ModuleChangesSignalProvider].
         */
        internal fun listenToModuleChanges(project: Project): Flow<Unit> =
            extensionPointName.extensions(project).consumeAsFlow()
                .map { it.registerModuleChangesListener(project) }
                .flattenMerge()
    }

    /**
     * Returns a [Flow]<[Unit]> that emits  every time the build systems has made a change
     * in the module structure.
     */
    fun registerModuleChangesListener(project: Project): Flow<Unit>
}

operator fun <T> Supplier<T>.invoke() = get()

operator fun Runnable.invoke() = run()

/**
 * Functional interface used to unsubscribe listeners for [ModuleChangesSignalProvider].
 */
fun interface Subscription {

    /**
     * Stops the listeners that generated this subscription.
     */
    fun unsubscribe()
}

/**
 * Unsubscribe all the [Subscription]s in this collection.
 */
fun Iterable<Subscription>.unsubscribeAll() = forEach { it.unsubscribe() }
