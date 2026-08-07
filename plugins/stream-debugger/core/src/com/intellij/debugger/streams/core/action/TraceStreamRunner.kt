package com.intellij.debugger.streams.core.action

import com.intellij.debugger.streams.core.ChainStatus
import com.intellij.debugger.streams.core.StreamChainWithLibrary
import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.ChainDetectionStateManager
import com.intellij.debugger.streams.core.diagnostic.ex.TraceCompilationException
import com.intellij.debugger.streams.core.diagnostic.ex.TraceEvaluationException
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.statistics.StreamDebuggerStatisticsCollector
import com.intellij.debugger.streams.core.statistics.TraceEntryPoint
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.formatResolvedTrace
import com.intellij.debugger.streams.core.trace.formatTrace
import com.intellij.debugger.streams.core.ui.ChooserOption
import com.intellij.debugger.streams.core.ui.ElementChooser
import com.intellij.debugger.streams.core.ui.impl.ElementChooserImpl
import com.intellij.debugger.streams.core.ui.impl.EvaluationAwareTraceWindow
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.cancelOnDispose
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.Nls
import java.util.stream.Stream
import kotlin.coroutines.CoroutineContext

@Service(Service.Level.PROJECT)
class TraceStreamRunner(val cs: CoroutineScope) {
  fun actionPerformed(session: XDebugSession?, entryPoint: TraceEntryPoint): Job = cs.launch(Dispatchers.Default) {
    if (session == null) {
      LOG.info("Session is null")
      return@launch
    }
    StreamDebuggerStatisticsCollector.logTraceStarted(session.project, entryPoint)

    val chainsState = withBackgroundProgress(session.project, StreamDebuggerBundle.message("action.calculating.chains.background.progress.title"), true) {
      ChainDetectionStateManager
        .getInstance(session.project)
        .chainStateFlow(session)
        .first { it.status !is ChainStatus.Computing }
    }
    LOG.info("Action was triggered with stream chains state: $chainsState")
    if (chainsState.status is ChainStatus.Found) {
      displayChains(session, chainsState.status.chains)
    }
  }

  private suspend fun displayChains(
    session: XDebugSession,
    chains: List<StreamChainWithLibrary>,
  ) {
    if (chains.isEmpty()) {
      LOG.warn("Stream chain is not built")
      return
    }

    if (chains.size == 1) {
      runTrace(chains.first().chain, chains.first().provider, session)
    }
    else {
      withContext(Dispatchers.EDT) {
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

    @OptIn(AwaitCancellationAndInvoke::class)
    private suspend fun runTrace(chain: StreamChain, provider: LibrarySupportProvider, session: XDebugSession) = coroutineScope {
      val window = withContext(Dispatchers.EDT) {
        EvaluationAwareTraceWindow(session, chain).also {
          coroutineContext.job.cancelOnDispose(it.disposable)
        }
      }

      awaitCancellationAndInvoke(Dispatchers.EDT) {
        window.close(DialogWrapper.CANCEL_EXIT_CODE)
      }

      withContext(Dispatchers.EDT) {
        yield()
        window.show()
      }

      suspend fun showError(message: @Nls String) {
        withContext(Dispatchers.EDT) {
          window.setFailMessage(message)
        }
      }

      withContext(Dispatchers.Default + TraceStreamUIScope(window.disposable)) {
        val project = session.getProject()
        val debuggerLauncher = provider.getDebuggerCommandLauncher(session)

        val tracer: StreamTracer = provider.getTracerFor(chain, session)
        val result = tracer.trace(chain)

        StreamDebuggerStatisticsCollector.logTraceFinished(project, provider, tracer, result)

        when (result) {
          is StreamTracer.Result.Evaluated -> {
            val resolvedTrace = result.result.resolve(provider.getLibrarySupport().resolverFactory)
            LOG.debug {
              """
                |Stream chain:
                |${chain.text}
                |Stream trace:
                |${formatTrace(result.result.trace)}
                |Resolved stream trace:
                |${formatResolvedTrace(resolvedTrace)}
              """.trimMargin()
            }
            withContext(Dispatchers.EDT) {
              window.setTrace(resolvedTrace, debuggerLauncher, result.evaluationContext, provider.getCollectionTreeBuilder(project))
            }
          }
          is StreamTracer.Result.EvaluationFailed -> {
            showError(result.message)
            throw TraceEvaluationException(result.message, result.traceExpression, result.cause)
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

/**
 * This is a lifetime of a Trace Stream window. It is used to release some memory-heavy resources after the window is closed.
 */
class TraceStreamUIScope(val disposable: Disposable) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<TraceStreamUIScope>

  override val key: CoroutineContext.Key<*> = Key
}