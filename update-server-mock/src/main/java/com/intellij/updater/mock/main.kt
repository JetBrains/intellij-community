/*
 * Copyright (c) 2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater.mock

import org.apache.logging.log4j.LogManager

fun main(args: Array<String>) {
  if (args.size != 1) {
    println("usage: java -jar update-server-mock.jar <port>")
    System.exit(1)
  }

  val log = LogManager.getLogger(Server::class.java)
  val port = args[0]
  try {
    log.info("starting the server on port '$port' ...")
    val generator = Generator()
    Server(port.toInt(), generator).start()
    log.info("ready")
  }
  catch(e: Exception) {
    log.error("failed to start the server", e)
    System.exit(2)
  }
}