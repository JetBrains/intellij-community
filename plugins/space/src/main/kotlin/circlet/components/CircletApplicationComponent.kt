package circlet.components

import circlet.klogging.impl.*
import circlet.runtime.*
import com.intellij.openapi.components.*
import klogging.impl.*
import runtime.*

class CircletApplicationComponent : ApplicationComponent {
    init {
        KLoggerStaticFactory.customFactory = ErrorToWarningKLoggers

        mutableUiDispatch = ApplicationUiDispatch
    }
}
