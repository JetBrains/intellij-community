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

class Generator {
  private val patch = this.javaClass.classLoader.getResourceAsStream("patch/patch.jar").use { it.readBytes() }

  fun generateXml(productCode: String, buildId: String, eap: Boolean): String {
    val status = if (eap) "eap" else "release"
    return """
      <!DOCTYPE products SYSTEM "updates.dtd">
      <products>
        <product name="Mock Product">
          <code>$productCode</code>
          <channel id="MOCK" status="$status" licensing="$status">
            <build number="9999.0.0" version="Mock">
              <message><![CDATA[A mock update. For testing purposes only.]]></message>
              <button name="Download" url="https://www.jetbrains.com/" download="true"/>
              <patch from="$buildId" size="from 0 to ∞"/>
            </build>
          </channel>
        </product>
      </products>""".trimIndent()
  }

  fun generatePatch(): ByteArray = patch
}