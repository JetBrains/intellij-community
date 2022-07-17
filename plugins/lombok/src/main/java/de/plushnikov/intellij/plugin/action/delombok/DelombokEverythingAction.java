package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.clazz.*;
import de.plushnikov.intellij.plugin.processor.clazz.builder.*;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsOldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsPredefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.*;
import de.plushnikov.intellij.plugin.processor.field.*;
import de.plushnikov.intellij.plugin.processor.method.BuilderClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.DelegateMethodProcessor;

public class DelombokEverythingAction extends AbstractDelombokAction {

  @Override
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true,
                               ApplicationManager.getApplication().getService(RequiredArgsConstructorProcessor.class),
                               ApplicationManager.getApplication().getService(AllArgsConstructorProcessor.class),
                               ApplicationManager.getApplication().getService(NoArgsConstructorProcessor.class),

                               ApplicationManager.getApplication().getService(DataProcessor.class),
                               ApplicationManager.getApplication().getService(GetterProcessor.class),
                               ApplicationManager.getApplication().getService(ValueProcessor.class),
                               ApplicationManager.getApplication().getService(WitherProcessor.class),
                               ApplicationManager.getApplication().getService(SetterProcessor.class),
                               ApplicationManager.getApplication().getService(EqualsAndHashCodeProcessor.class),
                               ApplicationManager.getApplication().getService(ToStringProcessor.class),

                               ApplicationManager.getApplication().getService(CommonsLogProcessor.class),
                               ApplicationManager.getApplication().getService(JBossLogProcessor.class),
                               ApplicationManager.getApplication().getService(Log4jProcessor.class),
                               ApplicationManager.getApplication().getService(Log4j2Processor.class),
                               ApplicationManager.getApplication().getService(LogProcessor.class),
                               ApplicationManager.getApplication().getService(Slf4jProcessor.class),
                               ApplicationManager.getApplication().getService(XSlf4jProcessor.class),
                               ApplicationManager.getApplication().getService(FloggerProcessor.class),
                               ApplicationManager.getApplication().getService(CustomLogProcessor.class),

                               ApplicationManager.getApplication().getService(GetterFieldProcessor.class),
                               ApplicationManager.getApplication().getService(SetterFieldProcessor.class),
                               ApplicationManager.getApplication().getService(WitherFieldProcessor.class),
                               ApplicationManager.getApplication().getService(DelegateFieldProcessor.class),
                               ApplicationManager.getApplication().getService(DelegateMethodProcessor.class),

                               ApplicationManager.getApplication().getService(FieldNameConstantsOldProcessor.class),
                               ApplicationManager.getApplication().getService(FieldNameConstantsFieldProcessor.class),
                               ApplicationManager.getApplication().getService(FieldNameConstantsProcessor.class),
                               ApplicationManager.getApplication().getService(FieldNameConstantsPredefinedInnerClassFieldProcessor.class),

                               ApplicationManager.getApplication().getService(UtilityClassProcessor.class),
                               ApplicationManager.getApplication().getService(StandardExceptionProcessor.class),

                               ApplicationManager.getApplication().getService(BuilderPreDefinedInnerClassFieldProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderPreDefinedInnerClassMethodProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderClassProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderClassMethodProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderMethodProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderProcessor.class),

                               ApplicationManager.getApplication().getService(SuperBuilderPreDefinedInnerClassFieldProcessor.class),
                               ApplicationManager.getApplication().getService(SuperBuilderPreDefinedInnerClassMethodProcessor.class),
                               ApplicationManager.getApplication().getService(SuperBuilderClassProcessor.class),
                               ApplicationManager.getApplication().getService(SuperBuilderProcessor.class));
  }

}
