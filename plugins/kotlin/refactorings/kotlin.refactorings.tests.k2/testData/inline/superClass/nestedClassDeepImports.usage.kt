package usage

import base.Base.Nested.Deep as ImportedDeep

fun create(): ImportedDeep = ImportedDeep()
fun answer() = create().answer()
