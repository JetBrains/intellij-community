interface IReport {
    val id: Int
    val pub<caret>licId: Int
        get() = id
}

class BReport {
    private fun IReport.f1() = publicId
    private fun f2(report: IReport) {
        val i = report.publicId
    }
}

