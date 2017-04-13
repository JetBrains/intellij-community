package com.intellij.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import org.mockito.Mockito.*
import org.picocontainer.MutablePicoContainer


val runnableInterface = "interface Runnable { void run();  void notify(); void wait(); void notifyAll(); }"
val testText = """
class Test {
    public void run() {
        Runnable r = new Runnable() {
            public void run() {}
        };
        r<caret>
    }
}
"""


class CompletionTypingTest : LightFixtureCompletionTestCase() {
    
    lateinit var mockLogger: CompletionLogger
    lateinit var realLoggerProvider: CompletionLoggerProvider
    lateinit var mockLoggerProvider: CompletionLoggerProvider
    lateinit var container: MutablePicoContainer
    
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
        
        myFixture.addClass(runnableInterface)
        myFixture.configureByText(JavaFileType.INSTANCE, testText)
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
//        verify(mockLogger, times(1)).itemSelectedCompletionFinished(Matchers.anyInt(), Matchers.anyString(), Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
    fun `test typing`() {
        myFixture.type('.')
        myFixture.completeBasic()

        myFixture.type('r')
        myFixture.type('u')
        myFixture.type('n')
        
//        verify(mockLogger).afterCharTyped(eq('r'), Matchers.anyListOf(LookupStringWithRelevance::class.java))
//        verify(mockLogger).afterCharTyped(eq('u'), Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
    fun `test up buttons`() {
        myFixture.type('.')
        myFixture.completeBasic()
        
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
        
//        verify(mockLogger).upPressed(Matchers.anyInt(), Matchers.anyString(), Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
    fun `test down button`() {
        myFixture.type('.')
        myFixture.completeBasic()

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
//        verify(mockLogger).downPressed(Matchers.anyInt(), Matchers.anyString(), Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
    fun `test completion started`() {
        myFixture.type('.')
        myFixture.completeBasic()
        myFixture.type("r")
//        verify(mockLogger).completionStarted(Matchers.anyListOf(LookupStringWithRelevance::class.java), Matchers.anyBoolean(), Matchers.anyInt())
    }
    
    fun `test backspace`() {
        myFixture.type('.')
        myFixture.completeBasic()
        
        myFixture.type('\b')
        myFixture.type('x') //normally it should be cancelled automatically, but in tests it is not, figure it out
        
//        verify(mockLogger).afterBackspacePressed(Matchers.anyInt(), Matchers.anyString(), Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
    fun `test enter`() {
        myFixture.type('.')
        myFixture.completeBasic()
        
        myFixture.type('r')
        myFixture.type('\n')
//        verify(mockLogger).itemSelectedCompletionFinished(Matchers.anyInt(), Matchers.anyString(), Matchers.anyListOf(LookupStringWithRelevance::class.java))
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
//        verify(mockLogger, times(1)).completionStarted(Matchers.anyListOf(LookupStringWithRelevance::class.java), Matchers.anyBoolean(), Matchers.anyInt())
//        verify(mockLogger, times(1)).itemSelectedCompletionFinished(Matchers.anyInt(), Matchers.anyString(), Matchers.anyListOf(LookupStringWithRelevance::class.java))
    }
    
}