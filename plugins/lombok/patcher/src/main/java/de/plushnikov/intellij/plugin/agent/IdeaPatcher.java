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
    System.out.println("Started Agent main");
    new IdeaPatcher().runAgent(agentArgs, instrumentation, true);
    System.out.println("Finished Agent");
  }

  public static void premain(String agentArgs, Instrumentation instrumentation) throws Throwable {
    System.out.println("Started Agent pre main");
    new IdeaPatcher().runAgent(agentArgs, instrumentation, false);
    System.out.println("Finished Agent");
  }

  protected void runAgent(String agentArgs, Instrumentation instrumentation, boolean injected) throws Exception {
    instrumentation.addTransformer(new ModifierVisibilityClassFileTransformer(), true);

//    final Class<?> classForName = Class.forName("com.intellij.psi.impl.source.PsiModifierListImpl");
//    instrumentation.retransformClasses(classForName);
  }

  private static class ModifierVisibilityClassFileTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
      if (className.equals("com/intellij/psi/impl/source/PsiModifierListImpl")) {
        return doClass(className, classBeingRedefined, classfileBuffer);
      } else {
        return null;
      }
    }

    private byte[] doClass(String name, Class clazz, byte[] b) {
      System.out.println("Modifying class! " + name);
      ClassPool pool = ClassPool.getDefault();
      CtClass cl = null;
      try {
        cl = pool.makeClass(new java.io.ByteArrayInputStream(b));
        CtMethod m = cl.getDeclaredMethod("hasModifierProperty");
//          m.insertBefore("System.out.println(\"Inside of hasModifierProperty: \" + $1);");
        m.insertBefore(
            "{\n" +
                "      com.intellij.openapi.extensions.ExtensionPointName pointName = com.intellij.openapi.extensions.ExtensionPointName.create(\"com.intellij.lang.psiAugmentProvider\");\n" +
                "      java.lang.Object[] extensions = com.intellij.openapi.extensions.Extensions.getExtensions(pointName);\n" +
                "      for (int i = 0; i < extensions.length; i++) {\n" +
                "        Object extension = extensions[i];\n" +
                "        if(extension.getClass().getName().equals(\"de.plushnikov.intellij.plugin.provider.LombokAugmentProvider\")) {\n" +
                "         try {\n" +
                "            java.lang.reflect.Method method = extension.getClass().getDeclaredMethod(\"doItHard\", new java.lang.Class[]{com.intellij.psi.PsiModifierList.class, java.lang.String.class});\n" +
                "            java.lang.Boolean augmentation = (java.lang.Boolean)method.invoke(extension, new Object[]{$0, $1});\n" +
                "            if (augmentation != null) {" +
                "               return augmentation.booleanValue();" +
                "            }" +
                "          } catch (Exception e) {\n" +
                "            System.err.println(e.getMessage());\n" +
                "          }" +
                "        }" +
                "      }\n" +
                "    }");

        return cl.toBytecode();
      } catch (Exception e) {
        System.err.println("Could not instrument  " + name + ",  exception : " + e.getMessage());
        return null;
      } finally {
        if (cl != null) {
          cl.detach();
        }
      }
    }
  }
}
