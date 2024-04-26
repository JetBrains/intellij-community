package highlighting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

//@AllArgsConstructor(onConstructor_ = @Deprecated)
@AllArgsConstructor(onConstructor = @__( @Deprecated))
@RequiredArgsConstructor(onConstructor_ = @Deprecated)
public class OnXExample {
    @Getter(onMethod_ = {@Deprecated, @SuppressWarnings(value = "someId")}) //JDK8
    @Setter(onMethod_ = @Deprecated, onParam_ = @SuppressWarnings(value = "someOtherId")) //JDK8
    private long unid;
}