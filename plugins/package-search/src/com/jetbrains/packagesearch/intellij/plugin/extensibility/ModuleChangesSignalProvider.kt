package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    fun registerModuleChangesListener(project: Project, listener: Supplier<Unit>): Subscription
}

internal operator fun <T> Supplier<T>.invoke() = get()

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
