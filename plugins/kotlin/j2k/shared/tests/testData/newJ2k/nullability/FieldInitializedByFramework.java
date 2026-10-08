// ADD_MOCKITO_ANNOTATIONS
import org.mockito.Mock;

class Service {
}

class InjectedTest {
    @Mock
    private Service service;

    Service current() {
        return service;
    }
}

class PackagePrivateTest {
    @Mock
    Service service;

    Service current() {
        return service;
    }
}

class ResetTest {
    @Mock
    private Service service;

    void reset() {
        service = null;
    }
}
