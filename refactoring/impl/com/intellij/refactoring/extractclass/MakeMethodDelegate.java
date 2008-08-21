package com.intellij.refactoring.extractclass;

import com.intellij.psi.*;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

class MakeMethodDelegate extends RefactorJUsageInfo {
    private final PsiMethod method;
    private final String delegate;

    MakeMethodDelegate(PsiMethod method, String delegate) {
        super(method);
        this.method = method;
        this.delegate = delegate;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiCodeBlock body = method.getBody();
        assert body != null;
        final PsiStatement[] statements = body.getStatements();
        for(PsiStatement statement : statements){
            statement.delete();
        }
        @NonNls final StringBuffer delegation = new StringBuffer();
        final PsiType returnType = method.getReturnType();
        if(!PsiType.VOID.equals(returnType))
        {
           delegation.append("return ");
        }
        final String methodName = method.getName();
        delegation.append(delegate + '.' + methodName + '(');
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        boolean first = true;
        for (PsiParameter parameter : parameters) {
            if(!first)
            {
                delegation.append(',');
            }
            first = false;
            final String parameterName = parameter.getName();
            delegation.append(parameterName);
        }
        delegation.append(");");
        final PsiManager manager = method.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final String delegationText = delegation.toString();
        final PsiStatement delegationStatement =
                factory.createStatementFromText(delegationText, body);
        body.add(delegationStatement);
    }
}
