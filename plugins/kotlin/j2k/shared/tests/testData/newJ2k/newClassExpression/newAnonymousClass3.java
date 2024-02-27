//file
import kotlinApi.KotlinInterface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo() {
        KotlinInterface t = new KotlinInterface() {
            @Nullable
            @Override
            public String nullableFun() {
                return null;
            }

            @NotNull
            @Override
            public String notNullableFun() {
                return "";
            }

            @Override
            public int nonAbstractFun() {
                return 0;
            }
        };
    }
}
