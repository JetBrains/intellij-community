package de.plushnikov.intellij.plugin.agent;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * This is a java-agent that patches some of idea's classes.
 */
public class IdeaPatcher {

  public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Throwable {
    System.out.println("EXECUTED AGENT_MAIN");
    new IdeaPatcher().runAgent(agentArgs, instrumentation, true);
  }

  public static void premain(String agentArgs, Instrumentation instrumentation) throws Throwable {
    System.out.println("EXECUTED AGENT_PRE_MAIN");
    new IdeaPatcher().runAgent(agentArgs, instrumentation, false);
  }

  protected void runAgent(String agentArgs, Instrumentation instrumentation, boolean injected) throws Exception {
    System.out.println("Enter runAgent");
//    ScriptManager sm = new ScriptManager();
//    sm.registerTransformer(instrumentation);

    System.out.println("Before patching");
//    patchIntellij(sm);

//    instrumentation.appendToSystemClassLoaderSearch();
    instrumentation.addTransformer(new ClassFileTransformer() {
      @Override
      public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        return doClass(className, classBeingRedefined, classfileBuffer);
      }
    }, true);
    instrumentation.retransformClasses(String.class);

    System.out.println("After patching");

//    sm.reloadClasses(instrumentation);
//    sm.registerTransformer(instrumentation);
    System.out.println("Finished runAgent");
  }

  private byte[] doClass(String name, Class clazz, byte[] b) {
    System.out.println("modufy! "+name);
    ClassPool pool = ClassPool.getDefault();
    CtClass cl = null;
    try {
      cl = pool.makeClass(new java.io.ByteArrayInputStream(b));
      CtMethod m = cl.getDeclaredMethod(GET_WM_NAME);
      m.setBody("{ return \""+wmName+"\"; }");
      b = cl.toBytecode();
    } catch (Exception e) {
      System.err.println("Could not instrument  " + name + ",  exception : " + e.getMessage());
    } finally {
      if (cl != null) {
        cl.detach();
      }
    }
    return b;
  }

//  private static void patchIntellij(ScriptManager sm) {
//    System.out.println("patching ....");
//
//    sm.addScript(ScriptBuilder.wrapReturnValue()
//        .target(new MethodTarget("com.intellij.codeInsight.ExceptionUtil", "isHandled", "boolean", "com.intellij.psi.PsiElement", "com.intellij.psi.PsiClassType", "com.intellij.psi.PsiElement"))
//        .wrapMethod(new Hook("de.plushnikov.intellij.plugin.handler.SneakyTrowsExceptionHandler", "wrapReturnValue", "boolean", "boolean", "com.intellij.psi.PsiElement", "com.intellij.psi.PsiClassType", "com.intellij.psi.PsiElement"))
//        .request(StackRequest.RETURN_VALUE, StackRequest.PARAM1, StackRequest.PARAM2, StackRequest.PARAM3)
//        .build());
//
////    sm.addScript(ScriptBuilder.exitEarly()
////        .target(new MethodTarget("com.intellij.codeInsight.ExceptionUtil", "isHandled", "boolean", "com.intellij.psi.PsiElement", "com.intellij.psi.PsiClassType", "com.intellij.psi.PsiElement"))
////        .decisionMethod(new Hook("de.plushnikov.intellij.plugin.handler.ExtraExceptionHandlerLogik", "decisionMethod", "boolean", "com.intellij.psi.PsiElement", "com.intellij.psi.PsiClassType", "com.intellij.psi.PsiElement"))
////        .valueMethod(new Hook("de.plushnikov.intellij.plugin.handler.ExtraExceptionHandlerLogik", "valueMethod", "boolean", "com.intellij.psi.PsiElement", "com.intellij.psi.PsiClassType", "com.intellij.psi.PsiElement"))
////        .request(StackRequest.PARAM1, StackRequest.PARAM2, StackRequest.PARAM3)
////        .build());
//
////    sm.addScript(ScriptBuilder.replaceMethodCall()
////        .target(new MethodTarget("com.intellij.codeInsight.ExceptionUtil", "collectUnhandledExceptions"))
////        .target(new MethodTarget("com.intellij.codeInsight.ExceptionUtil", "getUnhandledExceptions"))
////        .target(new MethodTarget("com.intellij.codeInsight.ExceptionUtil", "getUnhandledExceptions"))
////        .target(new MethodTarget("com.intellij.codeInsight.isHandled", "getUnhandledExceptions", "boolean", "com.intellij.psi.PsiClassType", "com.intellij.psi.PsiElement"))
////        .methodToReplace(new Hook("com.intellij.codeInsight.ExceptionUtil", "isHandled", "boolean", "com.intellij.psi.PsiElement", "com.intellij.psi.PsiClassType", "com.intellij.psi.PsiElement"))
////        .replacementMethod(new Hook("de.plushnikov.intellij.plugin.handler.ExtraExceptionHandlerLogik", "replacementMethod", "boolean", "com.intellij.psi.PsiElement", "com.intellij.psi.PsiClassType", "com.intellij.psi.PsiElement"))
////        .build());
////
////    sm.addScript(ScriptBuilder.replaceMethodCall()
////        .target(new MethodTarget("de.plushnikov.intellij.plugin.patcher.MeinTest", "calcSumme"))
////        .methodToReplace(new Hook("de.plushnikov.intellij.plugin.patcher.SummeCalculator", "calcSummePrimitive", "int", "int", "int"))
////        .replacementMethod(new Hook("de.plushnikov.intellij.plugin.patcher.MeinAgentTest", "calcDifferencePrimitive", "int", "de.plushnikov.intellij.plugin.patcher.SummeCalculator", "int", "int"))
////        .build());
//  }
}
