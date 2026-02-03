package action.delombok.onx;

import lombok.RequiredArgsConstructor;
import lombok.NonNull;

import javax.inject.Inject;
import javax.inject.Named;

@RequiredArgsConstructor(onConstructor_ = {@Inject, @Named("myName1")})
public class TestOnX {
    @NonNull
    private final Integer someIntField;
}
