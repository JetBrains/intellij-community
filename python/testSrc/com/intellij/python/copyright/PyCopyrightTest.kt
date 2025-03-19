// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.copyright

import com.jetbrains.python.fixtures.PyTestCase
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory
import java.util.*

class PyCopyrightTest : PyTestCase() {


  fun testCopyright() {
    doTest("""def foo():
  pass
""", """#  Copyright ${year()}
#  All rights reserved

def foo():
  pass
""")
  }


  fun testCopyrightShebang() {
    doTest("""#!/usr/bin/env python

def foo():
  pass
""", """#!/usr/bin/env python

#  Copyright ${year()}
#  All rights reserved

def foo():
  pass
""")
  }

  fun testCopyrightEncoding() {
    doTest("""# coding=utf-8

def foo():
  pass
""", """# coding=utf-8

#  Copyright ${year()}
#  All rights reserved

def foo():
  pass
""")
  }

  fun testCopyrightShebangAndEncoding() {
    doTest("""#!/usr/bin/env python
# -*- coding: utf-8 -*-

def foo():
  pass
""", """#!/usr/bin/env python
# -*- coding: utf-8 -*-

#  Copyright ${year()}
#  All rights reserved

def foo():
  pass
""")
  }


  fun testExistingCopyrightUpdate() {
    doTest("""#  Copyright 2013
#  All rights reserved

def foo():
  pass
""", """#  Copyright ${year()}
#  All rights reserved

def foo():
  pass
""")
  }


  private fun doTest(before: String, after: String) {
    myFixture.configureByText(getTestName(false) + ".py", before)
    updateCopyright()
    myFixture.checkResult(after)
  }

  private fun year(): Int = Calendar.getInstance().get(Calendar.YEAR)

  @Throws(Exception::class)
  private fun updateCopyright() {
    val options = CopyrightProfile()
    options.notice = "Copyright \$today.year\nAll rights reserved"
    options.keyword = "Copyright"
    options.allowReplaceRegexp = "Copyright"
    val updateCopyright = UpdateCopyrightFactory.createUpdateCopyright(myFixture.project, myFixture.module,
                                                                       myFixture.file, options)
    updateCopyright!!.prepare()
    updateCopyright.complete()
  }

}