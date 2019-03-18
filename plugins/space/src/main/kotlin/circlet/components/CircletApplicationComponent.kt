package circlet.components

import circlet.arenas.*
import circlet.client.api.impl.*
import circlet.klogging.impl.*
import circlet.platform.client.serialization.*
import circlet.runtime.*
import com.intellij.openapi.components.*
import klogging.impl.*
import runtime.*

class CircletApplicationComponent : ApplicationComponent {
    init {
        KLoggerStaticFactory.customFactory = KLoggerFactoryIdea

        mutableUiDispatch = ApplicationUiDispatch

        initCircletArenas()

        ApiClassesDeserializer(ExtendableSerializationRegistry.global).registerDeserializers()
    }
}
