package circlet.components

import circlet.client.api.impl.*
import circlet.klogging.impl.*
import circlet.platform.client.serialization.*
import circlet.runtime.*
import com.intellij.openapi.components.*
import klogging.impl.*
import runtime.*

class CircletApplicationComponent : ApplicationComponent {
    init {
        KLoggerStaticFactory.customFactory = ErrorToWarningKLoggers

        mutableUiDispatch = ApplicationUiDispatch

        ApiClassesDeserializer(ExtendableSerializationRegistry.global).registerDeserializers()
    }
}
