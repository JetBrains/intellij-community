package com.intellij.debugger.streams.core.action

import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.action.ChainResolver.StreamChainWithLibrary
import com.intellij.debugger.streams.core.diagnostic.ex.TraceCompilationException
import com.intellij.debugger.streams.core.diagnostic.ex.TraceEvaluationException
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.DebuggerPositionResolver
import com.intellij.debugger.streams.core.psi.impl.DebuggerPositionResolverImpl
import com.intellij.debugger.streams.core.trace.EvaluateExpressionTracer
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.impl.TraceResultInterpreterImpl
import com.intellij.debugger.streams.core.ui.ChooserOption
import com.intellij.debugger.streams.core.ui.ElementChooser
import com.intellij.debugger.streams.core.ui.impl.ElementChooserImpl
import com.intellij.debugger.streams.core.ui.impl.EvaluationAwareTraceWindow
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.Nls
import java.util.stream.Stream
import kotlin.coroutines.CoroutineContext

@Service(Service.Level.PROJECT)
class TraceStreamRunner(val cs: CoroutineScope) {
  private val myPositionResolver: DebuggerPositionResolver = DebuggerPositionResolverImpl()

  fun getChainStatus(session: XDebugSession?): ChainStatus {
    val element = if (session == null) null else myPositionResolver.getNearestElementToBreakpoint(session)
    if (element == null) {
      return ChainStatus.NOT_FOUND
    }
    else {
      return CHAIN_RESOLVER.tryFindChain(element)
    }
  }

  fun actionPerformed(session: XDebugSession?): Job = cs.launch(Dispatchers.Default) {
    if (session == null) {
      LOG.info("Session is null")
      return@launch
    }

    val chains = getChains(session)
    displayChains(session, chains)
  }

  private suspend fun displayChains(
    session: XDebugSession,
    chains: List<StreamChainWithLibrary>,
  ) {
    if (chains.isEmpty()) {
      LOG.warn("Stream chain is not built")
      return
    }

    withContext(Dispatchers.EDT) {
      if (chains.size == 1) {
        runTrace(chains.first().chain, chains.first().provider, session)
      }
      else {
        val project = session.getProject()
        val file = chains.first().chain.context.containingFile.virtualFile
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
                     ?: error("Cannot open editor for file: ${file.getName()}")

        yield()

        MyStreamChainChooser(editor).show(
          chains.map { StreamChainOption(it) },
          ElementChooser.CallBack { provider: StreamChainOption ->
            cs.launch(Dispatchers.EDT) {
              runTrace(provider.chain, provider.provider, session)
            }
          })
      }
    }
  }

  private suspend fun getChains(session: XDebugSession): List<StreamChainWithLibrary> {
    val chains = readAction {
      runBlockingCancellable {
        val element = myPositionResolver.getNearestElementToBreakpoint(session)
        if (element == null) {
          LOG.info("Element at cursor is not found")
          emptyList()
        }
        else {
          withBackgroundProgress(session.project, StreamDebuggerBundle.message("action.calculating.chains.background.progress.title")) {
            CHAIN_RESOLVER.getChains(element)
          }
        }
      }
    }
    return chains
  }

  private class MyStreamChainChooser(editor: Editor) : ElementChooserImpl<StreamChainOption?>(editor)

  private class StreamChainOption(chain: StreamChainWithLibrary) : ChooserOption {
    val chain: StreamChain = chain.chain
    val provider: LibrarySupportProvider = chain.provider

    override fun rangeStream(): Stream<TextRange?> {
      return Stream.of<TextRange?>(
        TextRange(chain.getQualifierExpression().textRange.startOffset,
                  chain.getTerminationCall().getTextRange().endOffset))
    }

    override fun getText(): String {
      return chain.getCompactText()
    }
  }

  companion object {
    fun getInstance(project: Project): TraceStreamRunner = project.getService(TraceStreamRunner::class.java)

    private val LOG = Logger.getInstance(TraceStreamRunner::class.java)

    private val CHAIN_RESOLVER = ChainResolver()

    private suspend fun runTrace(chain: StreamChain, provider: LibrarySupportProvider, session: XDebugSession) = coroutineScope {
      val window = EvaluationAwareTraceWindow(session, chain)
      Disposer.register(window.disposable) {
        cancel()
      }
      suspend fun showError(message: @Nls String) {
        withContext(Dispatchers.EDT) {
          window.setFailMessage(message)
        }
      }
      withContext(Dispatchers.EDT) {
        yield()
        window.show()
      }
      withContext(Dispatchers.Default + TraceStreamUIScope(window.disposable)) {
        val project = session.getProject()
        val expressionBuilder = provider.getExpressionBuilder(project)
        val resultInterpreter = TraceResultInterpreterImpl(provider.getLibrarySupport().interpreterFactory)
        val xValueInterpreter = provider.getXValueInterpreter(project)
        val debuggerLauncher = provider.getDebuggerCommandLauncher(session)
        val tracer: StreamTracer = EvaluateExpressionTracer(session, expressionBuilder, resultInterpreter, xValueInterpreter)

        debuggerLauncher.launchDebuggerCommand {
          val result = tracer.trace(chain)
          when (result) {
            is StreamTracer.Result.Evaluated -> {
              val resolvedTrace = result.result.resolve(provider.getLibrarySupport().resolverFactory)
              withContext(Dispatchers.EDT) {
                window.setTrace(resolvedTrace, debuggerLauncher, result.evaluationContext, provider.getCollectionTreeBuilder(project))
              }
            }
            is StreamTracer.Result.EvaluationFailed -> {
              showError(result.message)
              throw TraceEvaluationException(result.message, result.traceExpression)
            }
            is StreamTracer.Result.CompilationFailed -> {
              showError(result.message)
              throw TraceCompilationException(result.message, result.traceExpression)
            }
            StreamTracer.Result.Unknown -> {
              LOG.error("Unknown result")
            }
          }
        }
      }
    }
  }
}

/**
 * This is a lifetime of a Trace Stream window. It is used to release some memory-heavy resources after the window is closed.
 */
class TraceStreamUIScope(val disposable: Disposable) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<TraceStreamUIScope>

  override val key: CoroutineContext.Key<*> = Key
}