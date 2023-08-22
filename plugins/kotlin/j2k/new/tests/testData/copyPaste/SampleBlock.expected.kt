// ERROR: Unresolved reference: PsiElement
// ERROR: Unresolved reference: code
// ERROR: Unresolved reference: code
// ERROR: Unresolved reference: code
// ERROR: Unresolved reference: getSelectedElements
// ERROR: Unresolved reference: Project
// ERROR: Unresolved reference: file
// ERROR: Unresolved reference: Converter
// ERROR: Unresolved reference: J2kPackage
// ERROR: Unresolved reference: Converter
// ERROR: Unresolved reference: StringUtil
fun main(args: Array<String>) {
    val buffer: List<PsiElement> = getSelectedElements(code.getFile(), code.getStartOffsets(), code.getEndOffsets())

    val project: Project = file.getProject()
    val converter: Converter = Converter(project, J2kPackage.getPluginSettings())
    val result = StringBuilder()
    for (e in buffer) {
        result.append(converter.elementToKotlin(e))
    }

    return StringUtil.convertLineSeparators(result.toString())
}
