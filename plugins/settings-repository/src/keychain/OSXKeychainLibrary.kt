/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.keychain

import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Pointer
import java.nio.ByteBuffer
import java.nio.CharBuffer

val isOSXCredentialsStoreSupported: Boolean
  get() = SystemInfo.isMacIntel64 && SystemInfo.isMacOSLeopard

// http://developer.apple.com/mac/library/DOCUMENTATION/Security/Reference/keychainservices/Reference/reference.html
// It is very, very important to use CFRelease/SecKeychainItemFreeContent You must do it, otherwise you can get "An invalid record was encountered."
interface OSXKeychainLibrary : com.sun.jna.Library {
  companion object {
    private val LIBRARY = com.sun.jna.Native.loadLibrary("Security", OSXKeychainLibrary::class.java) as OSXKeychainLibrary

    fun saveGenericPassword(serviceName: ByteArray, accountName: String, password: CharArray) {
      saveGenericPassword(serviceName, accountName, Charsets.UTF_8.encode(CharBuffer.wrap(password)))
    }

    fun saveGenericPassword(serviceName: ByteArray, accountName: String, password: String) {
      saveGenericPassword(serviceName, accountName, Charsets.UTF_8.encode(password))
    }

    private fun saveGenericPassword(serviceName: ByteArray, accountName: String, passwordBuffer: ByteBuffer) {
      val passwordData: ByteArray
      val passwordDataSize = passwordBuffer.limit()
      if (passwordBuffer.hasArray() && passwordBuffer.arrayOffset() == 0) {
        passwordData = passwordBuffer.array()
      }
      else {
        passwordData = ByteArray(passwordDataSize)
        passwordBuffer.get(passwordData)
      }
      saveGenericPassword(serviceName, accountName, passwordData, passwordDataSize)
    }

    fun findGenericPassword(serviceName: ByteArray, accountName: String): String? {
      val accountNameBytes = accountName.toByteArray()
      val passwordSize = IntArray(1);
      val passwordData = arrayOf<Pointer?>(null);
      checkForError("find", LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, passwordSize, passwordData))
      val pointer = passwordData[0] ?: return null

      val result = String(pointer.getByteArray(0, passwordSize[0]))
      LIBRARY.SecKeychainItemFreeContent(null, pointer)
      return result
    }

    private fun saveGenericPassword(serviceName: ByteArray, accountName: String, password: ByteArray, passwordSize: Int) {
      val accountNameBytes = accountName.toByteArray()
      val itemRef = arrayOf<Pointer?>(null)
      checkForError("find (for save)", LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, null, null, itemRef))
      val pointer = itemRef[0]
      if (pointer == null) {
        checkForError("save (new)", LIBRARY.SecKeychainAddGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, passwordSize, password))
      }
      else {
        checkForError("save (update)", LIBRARY.SecKeychainItemModifyContent(pointer, null, passwordSize, password))
        LIBRARY.CFRelease(pointer)
      }
    }

    fun deleteGenericPassword(serviceName: ByteArray, accountName: String) {
      val itemRef = arrayOf<Pointer?>(null)
      val accountNameBytes = accountName.toByteArray()
      checkForError("find (for delete)", LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, null, null, itemRef))
      val pointer = itemRef[0]
      if (pointer != null) {
        checkForError("delete", LIBRARY.SecKeychainItemDelete(pointer))
        LIBRARY.CFRelease(pointer)
      }
    }

    fun checkForError(message: String, code: Int) {
      if (code != 0 && code != /* errSecItemNotFound, always returned from find it seems */-25300) {
        val translated = LIBRARY.SecCopyErrorMessageString(code, null);
        val builder = StringBuilder(message).append(": ")
        if (translated == null) {
          builder.append(code);
        }
        else {
          val buf = CharArray(LIBRARY.CFStringGetLength(translated).toInt())
          for (i in 0..buf.size - 1) {
            buf[i] = LIBRARY.CFStringGetCharacterAtIndex(translated, i.toLong())
          }
          LIBRARY.CFRelease(translated)
          builder.append(buf).append(" (").append(code).append(')')
        }
        LOG.error(builder.toString())
      }
    }
  }

  fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceNameLength: Int, serviceName: ByteArray, accountNameLength: Int, accountName: ByteArray, passwordLength: Int, passwordData: ByteArray, itemRef: Pointer? = null): Int

  fun SecKeychainItemModifyContent(/*SecKeychainItemRef*/ itemRef: Pointer, /*SecKeychainAttributeList**/ attrList: Pointer?, length: Int, data: ByteArray): Int

  fun SecKeychainFindGenericPassword(keychainOrArray: Pointer?,
                                            serviceNameLength: Int,
                                            serviceName: ByteArray,
                                            accountNameLength: Int,
                                            accountName: ByteArray,
                                            passwordLength: IntArray? = null,
                                            passwordData: Array<Pointer?>? = null,
                                            itemRef: Array<Pointer?/*SecKeychainItemRef*/>? = null): Int

  fun SecKeychainItemDelete(itemRef: Pointer): Int

  fun /*CFString*/ SecCopyErrorMessageString(status: Int, reserved: Pointer?): Pointer?

  // http://developer.apple.com/library/mac/#documentation/CoreFoundation/Reference/CFStringRef/Reference/reference.html

  fun /*CFIndex*/ CFStringGetLength(/*CFStringRef*/ theString: Pointer): Long

  fun /*UniChar*/ CFStringGetCharacterAtIndex(/*CFStringRef*/ theString: Pointer, /*CFIndex*/ idx: Long): Char

  fun CFRelease(/*CFTypeRef*/ cf: Pointer)

  fun SecKeychainItemFreeContent(/*SecKeychainAttributeList*/attrList: Pointer?, data: Pointer?)
}
