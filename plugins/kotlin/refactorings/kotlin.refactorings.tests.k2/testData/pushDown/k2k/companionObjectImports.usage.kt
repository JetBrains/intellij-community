package usage

import base.Base.Companion.ANSWER
import base.Base.Companion.Token as ImportedToken
import base.Base.Companion.answer as importedAnswer

fun answer() = ANSWER + importedAnswer()
fun token(): ImportedToken = ImportedToken()
