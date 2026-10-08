import org.mockito.Mock

internal class Service

internal class InjectedTest {
    @Mock
    private lateinit var service: Service

    fun current(): Service {
        return service
    }
}

internal class PackagePrivateTest {
    @Mock
    lateinit var service: Service

    fun current(): Service {
        return service
    }
}

internal class ResetTest {
    @Mock
    private var service: Service? = null

    fun reset() {
        service = null
    }
}
