package com.intellij.openapi.application.impl

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.mock.MockFileDocumentManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.serviceContainer.PlatformComponentManagerImpl
import com.intellij.testFramework.registerServiceInstance
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.Component
import java.util.concurrent.Callable
import java.util.concurrent.Future
import javax.swing.JComponent
import javax.swing.SwingUtilities

class MockPsiApplication(parentDisposable: Disposable) : PlatformComponentManagerImpl(null), ApplicationEx {

  private val DOCUMENT_KEY: Key<Document> = Key.create("MOCK_DOCUMENT_KEY")

  init {
    Disposer.register(parentDisposable, this)
    myPicoContainer.registerComponentInstance(Application::class.java, this)
    registerServiceInstance(FileDocumentManager::class.java,
                            MockFileDocumentManagerImpl({ seq: CharSequence -> DocumentImpl(seq) }, DOCUMENT_KEY))
  }

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor =
    pluginDescriptor.app

  override fun runReadAction(action: Runnable) {
    action.run()
  }

  override fun <T : Any?> runReadAction(computation: Computable<T>): T = computation.compute()

  override fun <T : Any?, E : Throwable?> runReadAction(computation: ThrowableComputable<T, E>): T = computation.compute()

  override fun runWriteAction(action: Runnable) {
    action.run()
  }

  override fun <T : Any?> runWriteAction(computation: Computable<T>): T = computation.compute()

  override fun <T : Any?, E : Throwable?> runWriteAction(computation: ThrowableComputable<T, E>): T = computation.compute()

  override fun hasWriteAction(actionClass: Class<*>): Boolean = false

  override fun assertReadAccessAllowed() {
  }

  override fun assertWriteAccessAllowed() {
  }

  override fun assertIsDispatchThread() {
  }

  override fun addApplicationListener(listener: ApplicationListener) {
  }

  override fun addApplicationListener(listener: ApplicationListener, parent: Disposable) {
  }

  override fun removeApplicationListener(listener: ApplicationListener) {
  }

  override fun saveAll() {
  }

  override fun saveSettings() {
  }

  override fun exit() {
  }

  override fun isWriteAccessAllowed(): Boolean = false

  override fun isReadAccessAllowed(): Boolean = false

  override fun isDispatchThread(): Boolean = SwingUtilities.isEventDispatchThread()

  override fun getInvokator(): ModalityInvokator {
    throw UnsupportedOperationException()
  }

  override fun invokeLater(runnable: Runnable) {
  }

  override fun invokeLater(runnable: Runnable, expired: Condition<*>) {
  }

  override fun invokeLater(runnable: Runnable, state: ModalityState) {
  }

  override fun invokeLater(runnable: Runnable, state: ModalityState, expired: Condition<*>) {
  }

  override fun invokeAndWait(runnable: Runnable, modalityState: ModalityState) {
    SwingUtilities.invokeAndWait(runnable)
  }

  override fun invokeAndWait(runnable: Runnable) {
    invokeAndWait(runnable, defaultModalityState)
  }

  override fun getCurrentModalityState(): ModalityState = noneModalityState

  override fun getModalityStateForComponent(c: Component): ModalityState = noneModalityState

  override fun getDefaultModalityState(): ModalityState = noneModalityState

  override fun getNoneModalityState(): ModalityState = ModalityState.NON_MODAL

  override fun getAnyModalityState(): ModalityState = AnyModalityState.ANY

  override fun getStartTime(): Long = 0

  override fun getIdleTime(): Long = 0

  override fun isUnitTestMode(): Boolean = true

  override fun isHeadlessEnvironment(): Boolean = true

  override fun isCommandLine(): Boolean = true

  override fun executeOnPooledThread(action: Runnable): Future<*> = PooledThreadExecutor.INSTANCE.submit(action)

  override fun <T : Any?> executeOnPooledThread(action: Callable<T>): Future<T> = PooledThreadExecutor.INSTANCE.submit(action)

  override fun isDisposeInProgress(): Boolean = false

  override fun isRestartCapable(): Boolean = false

  override fun restart() {
  }

  override fun isActive(): Boolean = true

  override fun acquireReadActionLock(): AccessToken = AccessToken.EMPTY_ACCESS_TOKEN

  override fun acquireWriteActionLock(marker: Class<*>): AccessToken = AccessToken.EMPTY_ACCESS_TOKEN

  override fun isInternal(): Boolean = false

  override fun isEAP(): Boolean = false

  override fun load(configPath: String?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun holdsReadLock(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isWriteActionInProgress(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isWriteActionPending(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isSaveAllowed(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun setSaveAllowed(value: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun runProcessWithProgressSynchronouslyInReadAction(project: Project?,
                                                               progressTitle: String,
                                                               canBeCanceled: Boolean,
                                                               cancelText: String?,
                                                               parentComponent: JComponent?,
                                                               process: Runnable): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun exit(force: Boolean, exitConfirmed: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun restart(exitConfirmed: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun restart(exitConfirmed: Boolean, elevate: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun runProcessWithProgressSynchronously(process: Runnable,
                                                   progressTitle: String,
                                                   canBeCanceled: Boolean,
                                                   project: Project?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun runProcessWithProgressSynchronously(process: Runnable,
                                                   progressTitle: String,
                                                   canBeCanceled: Boolean,
                                                   project: Project?,
                                                   parentComponent: JComponent?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun runProcessWithProgressSynchronously(process: Runnable,
                                                   progressTitle: String,
                                                   canBeCanceled: Boolean,
                                                   project: Project?,
                                                   parentComponent: JComponent?,
                                                   cancelText: String?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun assertIsDispatchThread(component: JComponent?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun assertTimeConsuming() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun tryRunReadAction(action: Runnable): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}