package com.intellij.openapi.vfs

import com.intellij.openapi.util.text.StringUtil
import java.io.*
import java.nio.charset.Charset
import java.nio.file.Files

private const val TEST_LOCAL_FS_PROTOCOL = "test_local"

class TestLocalVirtualFileSystem: VirtualFileSystem() {

  private val myRoots: MutableMap<String, TestVirtualFile> = mutableMapOf()

  override fun getProtocol(): String = TEST_LOCAL_FS_PROTOCOL

  override fun findFileByPath(path: String): VirtualFile? {
    var normalized = path.replace(File.separatorChar, '/')
    var file = resolveRoot(normalized)
    if (StringUtil.startsWithChar(normalized, '/')) normalized = normalized.substring(1)
    for (component in StringUtil.split(normalized, "/")) {
      file = file.getOrCreate(component)
    }
    return file
  }

  private fun resolveRoot(path: String): TestVirtualFile {
    val rootPath = path.split('/', limit = 2)[0] + File.separatorChar
    return myRoots.getOrPut(rootPath) { TestVirtualFile(File(rootPath), null) }
  }

  override fun refresh(asynchronous: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun refreshAndFindFileByPath(path: String): VirtualFile? =
    findFileByPath(path)

  override fun addVirtualFileListener(listener: VirtualFileListener) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun removeVirtualFileListener(listener: VirtualFileListener) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun copyFile(requestor: Any?, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isReadOnly(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  fun createTextVirtualFile(name: String, text: String): VirtualFile = TextVirtualFile(name, text)

  private inner class TestVirtualFile(private val myDelegate: File, private val myParent: TestVirtualFile?): VirtualFile() {

    private val myChildren: MutableMap<String, TestVirtualFile> = mutableMapOf()

    private val myPath = myDelegate.absolutePath.replace(File.pathSeparatorChar, '/')
    private var myContent: String? = if (myDelegate.exists() && !myDelegate.isDirectory) myDelegate.readText(charset) else null
    private var myLastModified: Long = myDelegate.lastModified()

    fun getOrCreate(path: String): TestVirtualFile = myChildren.getOrPut(path) { TestVirtualFile(myDelegate.resolve(path), this) }

    override fun getName(): String = myDelegate.name

    override fun getFileSystem(): VirtualFileSystem = this@TestLocalVirtualFileSystem

    override fun getPath(): String = myPath

    override fun isWritable(): Boolean = true

    override fun isDirectory(): Boolean = myDelegate.isDirectory

    override fun isValid(): Boolean = myDelegate.exists()

    override fun getParent(): VirtualFile? = myParent

    override fun getChildren(): Array<VirtualFile> {
      return myChildren.values.toTypedArray()
    }

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream =
      VfsUtilCore.outputStreamAddingBOM(object: ByteArrayOutputStream() {
        override fun close() {
          try {
            myContent = toString(charset.name())
            myLastModified = newModificationStamp
          } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(e)
          }
        }
      }, this)

    override fun contentsToByteArray(): ByteArray = myContent?.toByteArray(charset) ?: byteArrayOf()

    override fun getModificationStamp(): Long = myLastModified

    override fun getTimeStamp(): Long = 0L

    override fun getLength(): Long = contentsToByteArray().size.toLong()

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
    }

    override fun getInputStream(): InputStream = VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this)

    override fun findFileByRelativePath(relPath: String): VirtualFile? = findFileByPath("$myPath/$relPath")

    override fun getCharset(): Charset = Charset.defaultCharset()
  }

  private inner class TextVirtualFile(private val myName: String, private var myText: String): VirtualFile() {
    override fun getName(): String = myName

    override fun getFileSystem(): VirtualFileSystem = this@TestLocalVirtualFileSystem

    override fun getPath(): String = ""

    override fun isWritable(): Boolean = true

    override fun isDirectory(): Boolean = false

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? = null

    override fun getChildren(): Array<VirtualFile> = emptyArray()

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream =
      VfsUtilCore.outputStreamAddingBOM(object: ByteArrayOutputStream() {
        override fun close() {
          myText = toString(charset.name())
        }
      }, this)

    override fun contentsToByteArray(): ByteArray = myText.toByteArray(charset)

    override fun getTimeStamp(): Long = 0

    override fun getLength(): Long = myText.length.toLong()

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
    }

    override fun getInputStream(): InputStream = VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this)

    override fun getCharset(): Charset = Charset.defaultCharset()
  }
}