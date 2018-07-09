@interface Annotation{}

@Annotation
class A{}

@interface AnnotationInner{

  Annotation value();

}

@AnnotationArray(@Annotation)
class B1{}

@AnnotationArray(value = @Annotation)
class B2{}

@interface AnnotationArray{

  Annotation[] value();

}

@AnnotationArray({@Annotation})
class C{}