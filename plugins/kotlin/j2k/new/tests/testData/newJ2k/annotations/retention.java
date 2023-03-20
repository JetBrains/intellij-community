import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@interface Ann1 { }

@Retention(RetentionPolicy.CLASS)
@interface Ann2 { }

@Retention(RetentionPolicy.RUNTIME)
@interface Ann3 { }