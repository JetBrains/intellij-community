annotation class AnnoOnAnno(vararg val strings: String)

@AnnoOnAnno("v1", "v2")
@Retention(AnnotationRetention.SOURCE)
annotation class Anno
