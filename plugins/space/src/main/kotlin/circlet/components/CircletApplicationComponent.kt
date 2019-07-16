package circlet.components

import circlet.arenas.*
import circlet.client.api.impl.*
import circlet.klogging.impl.*
import circlet.platform.api.serialization.*
import circlet.runtime.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import libraries.klogging.*
import runtime.*

class CircletApplicationComponent : ApplicationComponent {
    init {
        val application = ApplicationManager.getApplication()
        if (!application.isUnitTestMode && !application.isHeadlessEnvironment) {
            KLoggerStaticFactory.customFactory = KLoggerFactoryIdea
        }

        mutableUiDispatch = ApplicationUiDispatch

        initCircletArenas()

        ApiClassesDeserializer(ExtendableSerializationRegistry.global).registerDeserializers()
    }
}
