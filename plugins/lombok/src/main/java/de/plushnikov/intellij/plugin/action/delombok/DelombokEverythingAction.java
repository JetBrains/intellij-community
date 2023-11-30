package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;

public class DelombokEverythingAction extends AbstractDelombokAction {

  @Override
  protected DelombokHandler createHandler() {
    LombokProcessorManager manager = LombokProcessorManager.getInstance();

    return new DelombokHandler(true,
                               manager.getRequiredArgsConstructorProcessor(),
                               manager.getAllArgsConstructorProcessor(),
                               manager.getNoArgsConstructorProcessor(),

                               manager.getDataProcessor(),
                               manager.getGetterProcessor(),
                               manager.getValueProcessor(),
                               manager.getWitherProcessor(),
                               manager.getSetterProcessor(),
                               manager.getEqualsAndHashCodeProcessor(),
                               manager.getToStringProcessor(),

                               manager.getCommonsLogProcessor(),
                               manager.getJBossLogProcessor(),
                               manager.getLog4jProcessor(),
                               manager.getLog4j2Processor(),
                               manager.getLogProcessor(),
                               manager.getSlf4jProcessor(),
                               manager.getXSlf4jProcessor(),
                               manager.getFloggerProcessor(),
                               manager.getCustomLogProcessor(),

                               manager.getGetterFieldProcessor(),
                               manager.getSetterFieldProcessor(),
                               manager.getWitherFieldProcessor(),
                               manager.getDelegateFieldProcessor(),
                               manager.getDelegateMethodProcessor(),

                               manager.getFieldNameConstantsOldProcessor(),
                               manager.getFieldNameConstantsFieldProcessor(),
                               manager.getFieldNameConstantsProcessor(),
                               manager.getFieldNameConstantsPredefinedInnerClassFieldProcessor(),

                               manager.getUtilityClassProcessor(),
                               manager.getStandardExceptionProcessor(),

                               manager.getBuilderPreDefinedInnerClassFieldProcessor(),
                               manager.getBuilderPreDefinedInnerClassMethodProcessor(),
                               manager.getBuilderClassProcessor(),
                               manager.getBuilderClassMethodProcessor(),
                               manager.getBuilderMethodProcessor(),
                               manager.getBuilderProcessor(),

                               manager.getSuperBuilderPreDefinedInnerClassFieldProcessor(),
                               manager.getSuperBuilderPreDefinedInnerClassMethodProcessor(),
                               manager.getSuperBuilderClassProcessor(),
                               manager.getSuperBuilderProcessor());
  }
}
