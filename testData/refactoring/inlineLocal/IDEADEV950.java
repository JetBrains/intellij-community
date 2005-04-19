public class IDEADEV950 {
    void foo () {
        int door_1 =  0;
        float doorP = door_1 ;
        //Cast should be inserted when inlining, otherwise semantics changes
        float d1 = (<caret>doorP / NOF_LOOPS);
    }

    private static final int NOF_LOOPS = 2;
}
