package com.intellij.debugger.streams.action

import com.intellij.debugger.streams.StreamDebuggerBundle
import com.intellij.debugger.streams.action.ChainResolver.StreamChainWithLibrary
import com.intellij.debugger.streams.diagnostic.ex.TraceCompilationException
import com.intellij.debugger.streams.diagnostic.ex.TraceEvaluationException
import com.intellij.debugger.streams.lib.LibrarySupportProvider
import com.intellij.debugger.streams.psi.DebuggerPositionResolver
import com.intellij.debugger.streams.psi.impl.DebuggerPositionResolverImpl
import com.intellij.debugger.streams.trace.EvaluateExpressionTracer
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.impl.TraceResultInterpreterImpl
import com.intellij.debugger.streams.ui.ChooserOption
import com.intellij.debugger.streams.ui.ElementChooser
import com.intellij.debugger.streams.ui.impl.ElementChooserImpl
import com.intellij.debugger.streams.ui.impl.EvaluationAwareTraceWindow
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import java.util.stream.Stream

@Service(Service.Level.PROJECT)
class TraceStreamRunner(val cs: CoroutineScope) {
  private val myPositionResolver: DebuggerPositionResolver = DebuggerPositionResolverImpl()

  fun getChainStatus(e: AnActionEvent): ChainStatus {
    val session = DebuggerUIUtil.getSession(e)
    val element = if (session == null) null else myPositionResolver.getNearestElementToBreakpoint(session)
    if (element == null) {
      return ChainStatus.NOT_FOUND
    }
    else {
      return CHAIN_RESOLVER.tryFindChain(element)
    }
  }

  fun showChains(e: AnActionEvent): Job = cs.launch(Dispatchers.Default) {
    val session = DebuggerUIUtil.getSession(e)
    if (session == null) {
      LOG.info("Session is null")
      return@launch
    }

    val position = session.getCurrentPosition()
    if (position == null) {
      LOG.info("Position is null")
      return@launch
    }

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

    if (chains.isEmpty()) {
      LOG.warn("Stream chain is not built")
      return@launch
    }

    withContext(Dispatchers.EDT) {
      if (chains.size == 1) {
        runTrace(chains.first().chain, chains.first().provider, session)
      }
      else {
        val project = session.getProject()
        val file = position.getFile()
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
    fun getInstance(project: com.intellij.openapi.project.Project): TraceStreamRunner = project.getService(TraceStreamRunner::class.java)

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
      withContext(Dispatchers.Default) {
        val project = session.getProject()
        val expressionBuilder = provider.getExpressionBuilder(project)
        val resultInterpreter = TraceResultInterpreterImpl(provider.getLibrarySupport().interpreterFactory)
        val xValueInterpreter = provider.getXValueInterpreter(project)
        val tracer: StreamTracer = EvaluateExpressionTracer(session, expressionBuilder, resultInterpreter, xValueInterpreter)
        val result = tracer.trace(chain)
        when (result) {
          is StreamTracer.Result.Evaluated -> {
            val resolvedTrace = result.result.resolve(provider.getLibrarySupport().resolverFactory)
            withContext(Dispatchers.EDT) {
              window.setTrace(resolvedTrace, result.context, provider.getCollectionTreeBuilder(result.context.project))
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