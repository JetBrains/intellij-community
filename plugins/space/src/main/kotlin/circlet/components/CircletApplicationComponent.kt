package circlet.components

import circlet.klogging.impl.*
import circlet.runtime.*
import circlet.utils.*
import com.intellij.openapi.components.*
import klogging.impl.*
import runtime.*

class CircletApplicationComponent : ApplicationComponent {
    init {
        KLoggerStaticFactory.customFactory = KLoggerApplicationFactory

        mutableUiDispatch = ApplicationDispatcher(application)
    }
}
