/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.stats.completion

import com.google.common.net.HttpHeaders
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.intellij.stats.storage.FilePathProvider
import com.intellij.util.Alarm
import com.intellij.util.Time
import org.apache.commons.codec.binary.Base64OutputStream
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import javax.swing.SwingUtilities


