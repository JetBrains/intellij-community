package com.jetbrains.python.defaultProjectAwareService

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * This interface helps reduce the amount of boilerplate code when migrating services from the module-level to the project-level.
 *
 * It can be used along with the default implementation [PyModuleServiceFactoryImpl] to delegate service instancing:
 *
 * ```kotlin
 * @Service(Service.Level.PROJECT)
 * internal class PyDocumentationSettingsFactory
 *   : PyModuleServiceFactory<PyDocumentationSettings.ModuleService>
 *     by PyModuleServiceFactoryImpl(PyDocumentationSettings::ModuleService)
 * ```
 *
 * However, if there are specific needs for instancing, it is not required to use this interface.
 */
@ApiStatus.Internal
interface PyModuleServiceFactory<T> {

  fun getService(module: Module): T
}

@ApiStatus.Internal
class PyModuleServiceFactoryImpl<T : Any>(
  private val serviceFactory: (Module) -> T,
) : PyModuleServiceFactory<T> {
  private val instances = ConcurrentHashMap<Module, T>()

  override fun getService(module: Module): T {
    return instances.computeIfAbsent(module) {
      val service = serviceFactory(module)
      Disposer.register(module) {
        instances.remove(module)
      }
      if (service is Disposable) {
        Disposer.register(module, service)
      }
      service
    }
  }
}