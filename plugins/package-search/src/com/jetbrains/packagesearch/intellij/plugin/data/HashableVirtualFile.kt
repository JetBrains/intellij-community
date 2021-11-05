package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.openapi.vfs.VirtualFile

internal class HashableVirtualFile(val delegate: VirtualFile) : VirtualFile() {

    override fun getName() = delegate.name

    override fun getFileSystem() = delegate.fileSystem

    override fun getPath() = delegate.path

    override fun isWritable() = delegate.isWritable

    override fun isDirectory() = delegate.isDirectory

    override fun isValid() = delegate.isValid

    override fun getParent(): VirtualFile = delegate.parent

    override fun getChildren(): Array<VirtualFile> = delegate.children

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) =
        delegate.getOutputStream(requestor, newModificationStamp, newTimeStamp)

    override fun contentsToByteArray(): ByteArray = delegate.contentsToByteArray()

    override fun getTimeStamp() = delegate.timeStamp

    override fun getLength() = delegate.length

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) =
        delegate.refresh(asynchronous, recursive, postRunnable)

    override fun getInputStream() = delegate.inputStream

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HashableVirtualFile

        if (delegate != other.delegate) return false

        return path == other.path && fileSystem == other.fileSystem
    }

    override fun hashCode() = path.hashCode() * 17 / (fileSystem.hashCode() * 32) + 18
}

