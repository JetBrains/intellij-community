package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.DataProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.EqualsAndHashCodeProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsPredefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.SetterProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ValueProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.WitherProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderPreDefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderPreDefinedInnerClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.CommonsLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.FloggerProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.JBossLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4j2Processor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.LogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.XSlf4jProcessor;
import de.plushnikov.intellij.plugin.processor.field.DelegateFieldProcessor;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.DelegateMethodProcessor;

import static de.plushnikov.intellij.plugin.util.ExtensionsUtil.findExtension;

public class DelombokEverythingAction extends AbstractDelombokAction {

  protected DelombokHandler createHandler() {
    return new DelombokHandler(true,
      findExtension(RequiredArgsConstructorProcessor.class),
      findExtension(AllArgsConstructorProcessor.class),
      findExtension(NoArgsConstructorProcessor.class),

      findExtension(DataProcessor.class),
      findExtension(GetterProcessor.class),
      findExtension(ValueProcessor.class),
      findExtension(WitherProcessor.class),
      findExtension(SetterProcessor.class),
      findExtension(EqualsAndHashCodeProcessor.class),
      findExtension(ToStringProcessor.class),

      findExtension(CommonsLogProcessor.class), findExtension(JBossLogProcessor.class), findExtension(Log4jProcessor.class),
      findExtension(Log4j2Processor.class), findExtension(LogProcessor.class), findExtension(Slf4jProcessor.class),
      findExtension(XSlf4jProcessor.class), findExtension(FloggerProcessor.class),

      findExtension(GetterFieldProcessor.class),
      findExtension(SetterFieldProcessor.class),
      findExtension(WitherFieldProcessor.class),
      findExtension(DelegateFieldProcessor.class),
      findExtension(DelegateMethodProcessor.class),

      findExtension(FieldNameConstantsProcessor.class),
      findExtension(FieldNameConstantsPredefinedInnerClassFieldProcessor.class),

      findExtension(BuilderPreDefinedInnerClassFieldProcessor.class),
      findExtension(BuilderPreDefinedInnerClassMethodProcessor.class),
      findExtension(BuilderClassProcessor.class),
      findExtension(BuilderClassMethodProcessor.class),
      findExtension(BuilderMethodProcessor.class),
      findExtension(BuilderProcessor.class));
  }

}
