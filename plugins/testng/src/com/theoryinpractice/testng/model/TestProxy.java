package com.theoryinpractice.testng.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.testng.remote.strprotocol.TestResultMessage;
import org.testng.remote.strprotocol.MessageHelper;
import com.theoryinpractice.testng.TestNGConsoleView;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.openapi.application.ApplicationManager;

/**
 * @author Hani Suleiman Date: Jul 28, 2005 Time: 10:52:51 PM
 */
public class TestProxy
{
    private List<TestProxy> results = new ArrayList<TestProxy>();
    private TestResultMessage resultMessage;
    private String name;
    private TestProxy parent;
    private List<TestNGConsoleView.Chunk> output;
    private PsiElement psiElement;
    private boolean inProgress;
    
    public TestProxy() {

    }

    public TestProxy(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }

    public void setPsiElement(PsiElement psiElement) {
        this.psiElement = psiElement;
    }

    public boolean isResult() {
        return resultMessage != null;
    }

    public List<TestProxy> getResults(TestFilter filter) {
        return filter.select(results);
    }

    public List<TestNGConsoleView.Chunk> getOutput() {
        if(output != null) return output;
        List<TestNGConsoleView.Chunk> total = new ArrayList<TestNGConsoleView.Chunk>();
        for(TestProxy child : results) {
            total.addAll(child.getOutput());
        }
        return total;
    }

    public List<TestProxy> getResults() {
        return results;
    }

    public TestResultMessage getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(final TestResultMessage resultMessage) {
        //if we have a result, then our parent is a class, so we can look up our method
        //this is a bit fragile as it assumes parent is set first and correctly
        ApplicationManager.getApplication().runReadAction(new Runnable()
        {
            public void run() {
                PsiClass psiClass = (PsiClass)getParent().getPsiElement();
                PsiMethod[] methods = psiClass.getMethods();
                for (PsiMethod method : methods) {
                    if (method.getName().equals(resultMessage.getMethod())) {
                        TestProxy.this.psiElement = method;
                        break;
                    }
                }
            }
        });
        this.resultMessage = resultMessage;
        TestProxy current = this;
        while(current != null) {
            current.inProgress = resultMessage.getResult() == MessageHelper.TEST_STARTED;
            current = current.getParent();
        }
        
        this.name = resultMessage.getMethod();
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public TestProxy[] getPathFromRoot()
    {
        ArrayList<TestProxy> arraylist = new ArrayList<TestProxy>();
        TestProxy testproxy = this;
        do
            arraylist.add(testproxy);
        while((testproxy = testproxy.getParent()) != null);
        Collections.reverse(arraylist);
        return arraylist.toArray(new TestProxy[arraylist.size()]);
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;

        final TestProxy testProxy = (TestProxy)o;

        if(name != null ? !name.equals(testProxy.name) : testProxy.name != null) return false;
        if(resultMessage != null ? !resultMessage.equals(testProxy.resultMessage) : testProxy.resultMessage != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = resultMessage != null ? resultMessage.hashCode() : 0;
        result = 29 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return name + ' ' + results;
    }
    
    public void addResult(TestProxy proxy) {
        results.add(proxy);
        proxy.setParent(this);
    }

    public void setParent(TestProxy parent) {
        this.parent = parent;
    }

    public TestProxy getParent() {
        return parent;
    }

    public void setOutput(List<TestNGConsoleView.Chunk> output) {
        this.output = output;
    }

    public boolean isNotPassed() {
        if(resultNotPassed()) return true;
        //we just added the node, so we don't know if it has passes or fails
        if(resultMessage == null && results.size() == 0) return true;
        for(TestProxy child : results) {
            if(child.isNotPassed()) return true;
        }
        return false;
    }
    
    private boolean resultNotPassed() {
        return resultMessage != null && resultMessage.getResult() != MessageHelper.PASSED_TEST;
    }

    public List<TestProxy> getAllTests() {
        List<TestProxy> total = new ArrayList<TestProxy>();
        total.add(this);
        for(TestProxy child : results) {
            total.addAll(child.getAllTests());
        }
        return total;
    }

    public int getChildCount() {
        return results.size();
    }

    public TestProxy getChildAt(int i) {
        return results.get(i);
    }
    
    public TestProxy getFirstDefect() {
        for(TestProxy child : results) {
            if(child.isNotPassed() && child.isResult()) return child;
            TestProxy firstDefect = child.getFirstDefect();
            if(firstDefect != null)
                return firstDefect;
        }
        return null;
    }
}
