import one.SingleFacade;

public class SingleFileMultifileFacade {
    void t() {
        SingleFacade.topLevelFunct<caret>ion();
    }
}


// REF: (one).topLevelFunction()
// CLS_REF: (one).topLevelFunction()