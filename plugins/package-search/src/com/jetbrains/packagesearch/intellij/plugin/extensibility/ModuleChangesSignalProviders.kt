package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.util.dumbService
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

abstract class AbstractMessageBusModuleChangesSignalProvider : ModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project, listener: Runnable): Subscription {
        val isSubscribed = AtomicBoolean(true)
        val simpleConnect: SimpleMessageBusConnection = project.messageBus.simpleConnect()
        registerModuleChangesListener(project, simpleConnect) { if (isSubscribed.get()) listener() }
        return Subscription {
            isSubscribed.set(false)
            simpleConnect.disconnect()
        }
    }

    protected abstract fun registerModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable)
}

abstract class DumbAwareMessageBusModuleChangesSignalProvider : AbstractMessageBusModuleChangesSignalProvider() {

    override fun registerModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable) =
        registerDumbAwareModuleChangesListener(project, bus) { project.dumbService.runWhenSmart(listener) }

    abstract fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable)
}

open class FileWatcherSignalProvider(private val paths: List<Path>) : FlowModuleChangesSignalProvider {

    constructor(vararg paths: Path) : this(paths.toList())

    private val absolutePathStrings = paths.map { it.absolutePathString() }

    override fun registerModuleChangesListener(project: Project): Flow<Unit> {
        val localFs: LocalFileSystem = LocalFileSystem.getInstance()
        val watchRequests = absolutePathStrings.asSequence()
            .onEach { localFs.findFileByPath(it) }
            .mapNotNull { localFs.addRootToWatch(it, false) }
        return channelFlow {
            project.filesChangedEventFlow.flatMapMerge { it.asFlow() }
                .filter { vFileEvent -> paths.any { it.name == vFileEvent.file?.name } } // check the file name before resolving the absolute path string
                .filter { vFileEvent -> absolutePathStrings.any { it == vFileEvent.file?.toNioPath()?.absolutePathString() } }
                .onEach { send(Unit) }
                .launchIn(this)
            awaitClose { watchRequests.forEach { request -> localFs.removeWatchedRoot(request) } }
        }
    }
}
