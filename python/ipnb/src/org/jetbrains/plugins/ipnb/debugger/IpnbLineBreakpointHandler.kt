// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.jetbrains.python.debugger.AbstractLineBreakpointHandler
import com.jetbrains.python.debugger.PyDebugProcess

class IpnbLineBreakpointHandler(debugProcess: PyDebugProcess) : AbstractLineBreakpointHandler(IpnbLineBreakpointType::class.java,
                                                                                              debugProcess)