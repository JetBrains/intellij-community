package com.intellij.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import org.mockito.Matchers
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
        r<caret>
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
    }

    override fun tearDown() {
        val name = CompletionLoggerProvider::class.java.name
        container.unregisterComponent(name)
        container.registerComponentInstance(name, realLoggerProvider)
        super.tearDown()
    }
    
    fun `test item selected on just typing`() {
        myFixture.type('.')
        myFixture.completeBasic()
        myFixture.type("run(")
        //todo check in real world works another way
        verify(mockLogger, times(1)).itemSelectedCompletionFinished(Matchers.anyInt(), Matchers.anyString())
    }
    
    fun `test typing`() {
        myFixture.type('.')
        myFixture.completeBasic()

        myFixture.type('r')
        myFixture.type('u')
        verify(mockLogger).charTyped(eq('r'), Matchers.anyListOf(LookupStringWithRelevance::class.java))
        verify(mockLogger).charTyped(eq('u'), Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
    fun `test up buttons`() {
        myFixture.type('.')
        myFixture.completeBasic()
        
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
        verify(mockLogger).upPressed(Matchers.anyInt(), Matchers.anyString())
    }
    
    fun `test down button`() {
        myFixture.type('.')
        myFixture.completeBasic()

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        verify(mockLogger).downPressed(Matchers.anyInt(), Matchers.anyString())
    }
    
    fun `test completion started`() {
        myFixture.type('.')
        myFixture.completeBasic()
        verify(mockLogger).completionStarted(Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
    fun `test backspace`() {
        myFixture.type('.')
        myFixture.completeBasic()
        
        myFixture.type('\b')
        verify(mockLogger).backspacePressed(Matchers.anyInt(), Matchers.anyString(), Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
    fun `test enter`() {
        myFixture.type('.')
        myFixture.completeBasic()
        
        myFixture.type('r')
        myFixture.type('\n')
        verify(mockLogger).itemSelectedCompletionFinished(Matchers.anyInt(), Matchers.anyString())
    }
    
    fun `test completion cancelled`() {
        myFixture.type('.')
        myFixture.completeBasic()

        lookup.hide()
        verify(mockLogger).completionCancelled()
    }
    
    fun `test if typed prefix is correct completion variant, pressing dot will select it`() {
        myFixture.completeBasic()
        myFixture.type('.')
        verify(mockLogger, times(1)).completionStarted(Matchers.anyListOf(LookupStringWithRelevance::class.java))
        verify(mockLogger, times(1)).itemSelectedCompletionFinished(Matchers.anyInt(), Matchers.anyString())
    }
    
}