package com.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import org.mockito.Mockito.*
import org.picocontainer.MutablePicoContainer

class CompletionTypingTest : LightFixtureCompletionTestCase() {
    lateinit var mockLogger: CompletionLogger
    lateinit var realLoggerProvider: CompletionLoggerProvider
    lateinit var mockLoggerProvider: CompletionLoggerProvider
    lateinit var container: MutablePicoContainer
    
    val text = """
class Test {
    public void run() {
        Runnable r = new Runnable() {
            public void run() {}
        };
        r.<caret>
    }
}
"""
    
    private val runnable = "interface Runnable { void run();  void notify(); void wait(); void notifyAll(); }"
    
    override fun setUp() {
        super.setUp()
        container = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        mockLoggerProvider = mock(CompletionLoggerProvider::class.java)
        mockLogger = mock(CompletionLogger::class.java)
        `when`(mockLoggerProvider.newCompletionLogger()).thenReturn(mockLogger)
        
        val name = CompletionLoggerProvider::class.java.name
        realLoggerProvider = container.getComponentInstance(name) as CompletionLoggerProvider
        
        container.unregisterComponent(name)
        container.registerComponentInstance(name, mockLoggerProvider)
        
        myFixture.addClass(runnable)
        myFixture.configureByText(JavaFileType.INSTANCE, text)
        myFixture.completeBasic()
    }

    override fun tearDown() {
        val name = CompletionLoggerProvider::class.java.name
        container.unregisterComponent(name)
        container.registerComponentInstance(name, realLoggerProvider)
        super.tearDown()
    }
    
    fun `test item selected on just typing`() {
        myFixture.type("run(")
        verify(mockLogger, times(1)).itemSelectedCompletionFinished()
    }
    
    fun `test typing`() {
        myFixture.type('r')
        myFixture.type('u')
        verify(mockLogger).charTyped('r')
        verify(mockLogger).charTyped('u')
    }
    
    fun `test up buttons`() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
        verify(mockLogger).upPressed()
    }
    
    fun `test down button`() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        verify(mockLogger).downPressed()
    }
    
    fun `test completion started`() {
        //setUp -> completeBasic()
        verify(mockLogger).completionStarted()
    }
    
    fun `test backspace`() {
        myFixture.type('\b')
        verify(mockLogger).backspacePressed()
    }
    
    fun `test enter`() {
        myFixture.type('r')
        myFixture.type('\n')
        verify(mockLogger).itemSelectedCompletionFinished()
    }
    
    fun `test completion cancelled`() {
        lookup.hide()
        verify(mockLogger).completionCancelled()
    }
    
}