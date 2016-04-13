package de.plushnikov.intellij.plugin.agent;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

import de.plushnikov.intellij.plugin.agent.transformer.IdeaPatcherTransformer;
import de.plushnikov.intellij.plugin.agent.transformer.ModifierVisibilityClassFileTransformer;

/**
 * This is a java-agent that patches some of idea's classes.
 */
public class IdeaPatcher {

  private static List<IdeaPatcherTransformer> KNOWN_TRANSFORMERS = new ArrayList<IdeaPatcherTransformer>();

  static {
    KNOWN_TRANSFORMERS.add(new ModifierVisibilityClassFileTransformer());
  }


  public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Throwable {
    System.out.println("Started IntelliJ Lombok Agent main");
    runAgent(agentArgs, instrumentation, true);
    System.out.println("Finished IntelliJ Lombok Agent");
  }

  public static void premain(String agentArgs, Instrumentation instrumentation) throws Throwable {
    System.out.println("Started IntelliJ Lombok Agent pre main");
    runAgent(agentArgs, instrumentation, false);
    System.out.println("Finished IntelliJ Lombok Agent");
  }

  static void runAgent(String agentArgs, Instrumentation instrumentation, boolean injected) throws Exception {

    IdeaPatcherOptionsHolder optionsHolder = IdeaPatcherOptionsHolder.getInstance();
    optionsHolder.addAll(agentArgs);

    for (IdeaPatcherTransformer transformer: KNOWN_TRANSFORMERS) {
      if (transformer.supported()) {
        instrumentation.addTransformer(transformer, transformer.canRetransform());
      }
    }
  }
}
