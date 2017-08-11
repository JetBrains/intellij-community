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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.containers.Convertor;
import org.tmatesoft.svn.core.internal.util.jna.ISVNGnomeKeyringLibrary;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class SvnNativeCallsTranslator {
  private final static String ourGenericAdvice =
    "An error result is returned by native " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION +
    " call \"{0}\" done from Subversion plugin.\n" + "Error code is {1}";

  public static String getMessage(final NativeLogReader.CallInfo callInfo) {
    if (SystemInfo.isMac) {
      return forMac(callInfo);
    }
    if (SystemInfo.isWindows) {
      return forWindows(callInfo);
    }
    if (SystemInfo.isLinux) {
      return forLinux(callInfo);
    }
    // no assumptions on expected result code
    return null;
  }

  public static String forLinux(NativeLogReader.CallInfo callInfo) {
    final String translate = LinuxParser.translate(callInfo);
    if (translate != null) return translate;
    return null;
  }

  public static String forWindows(NativeLogReader.CallInfo callInfo) {
    final String translate = WindowsParser.translate(callInfo);
    if (translate != null) return translate;
    return null;
  }

  public static String forMac(NativeLogReader.CallInfo callInfo) {
    if (callInfo.getResultCode() == 0) return null; // ok code

    final String translate = MacParser.translate(callInfo);
    if (translate != null) return translate;
    return defaultMessage(callInfo);
  }

  public static String defaultMessage(NativeLogReader.CallInfo callInfo) {
    return MessageFormat.format(ourGenericAdvice, callInfo.getFunctionName(), String.valueOf(callInfo.getResultCode()));
  }

  public static String defaultMessageStr(NativeLogReader.CallInfo callInfo) {
    return MessageFormat.format(ourGenericAdvice, callInfo.getFunctionName(), callInfo.getStrResultCode());
  }

  private static class WindowsParser {
    public static String translate(NativeLogReader.CallInfo callInfo) {
      if (callInfo.getFunctionName().contains("ISVNSecurityLibrary")) {
        // for numeric codes
        if (!(String.valueOf(callInfo.getResultCode())).equals(callInfo.getStrResultCode())) return null;
        //com.sun.jna.platform.win32.W32Errors.SEC_E_OK = 0
        if (callInfo.getResultCode() == 0) return null;
        return defaultMessage(callInfo);
      }
      if (callInfo.getFunctionName().contains("ISVNWinCryptLibrary")) {
        if (callInfo.getStrResultCode().contains("false")) {
          return defaultMessageStr(callInfo);
        }
      }
      return null;
    }
  }

  private static class LinuxParser {
    private final static Map<Integer, Couple<String>> gnomeMessages = new HashMap<>();

    public static String translate(NativeLogReader.CallInfo callInfo) {
      if (! callInfo.getFunctionName().contains("ISVNGnomeKeyringLibrary")) return null;
      if (callInfo.getFunctionName().contains("gnome_keyring_is_available")) {
        if (callInfo.getStrResultCode().contains("false")) {
          return MessageFormat.format(ourGenericAdvice, callInfo.getFunctionName(),
                                      callInfo.getStrResultCode() + " (You can't communicate with the daemon (so you can't load and save passwords)).");
        }
        return null;
      }
      if (! (String.valueOf(callInfo.getResultCode())).equals(callInfo.getStrResultCode())) return null;
      if (callInfo.getResultCode() == 0) return null;
      final Couple<String> pair = gnomeMessages.get(callInfo.getResultCode());
      if (pair == null) return null;
      return MessageFormat.format(ourGenericAdvice, callInfo.getFunctionName(),
                                  String.valueOf(callInfo.getResultCode()) + " ( " + pair.getFirst() + " - " + pair.getSecond() + ")");
    }

    static {
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_OK,
                        Couple.of("GNOME_KEYRING_RESULT_OK", "The operation completed successfully."));
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_DENIED,
                        Couple.of("GNOME_KEYRING_RESULT_DENIED", "Either the user or daemon denied access."));
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_NO_KEYRING_DAEMON,
                        Couple.of("GNOME_KEYRING_RESULT_NO_KEYRING_DAEMON", "Keyring daemon is not available."));
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_ALREADY_UNLOCKED,
                        Couple.of("GNOME_KEYRING_RESULT_ALREADY_UNLOCKED", "Keyring was already unlocked."));
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_NO_SUCH_KEYRING,
                        Couple.of("GNOME_KEYRING_RESULT_NO_SUCH_KEYRING", "No such keyring exists."));

      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_BAD_ARGUMENTS,
                        Couple.of("GNOME_KEYRING_RESULT_BAD_ARGUMENTS", "Bad arguments to function."));
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_IO_ERROR,
                        Couple.of("GNOME_KEYRING_RESULT_IO_ERROR", "Problem communicating with daemon."));
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_CANCELLED,
                        Couple.of("GNOME_KEYRING_RESULT_CANCELLED", "Operation was cancelled."));
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_KEYRING_ALREADY_EXISTS,
                        Couple.of("GNOME_KEYRING_RESULT_KEYRING_ALREADY_EXISTS", "The keyring already exists."));
      gnomeMessages.put(ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_NO_MATCH,
                        Couple.of("GNOME_KEYRING_RESULT_NO_MATCH", "No such match found."));
    }
  }

  private static class MacParser {
    private final static Map<Integer, Trinity<String, String, String>> macMessages = new HashMap<>();
    private final static HashMap<Integer, Convertor<NativeLogReader.CallInfo, String>> ourAdvices =
      new HashMap<>();

    public static String translate(NativeLogReader.CallInfo callInfo) {
      final Convertor<NativeLogReader.CallInfo, String> convertor = ourAdvices.get(callInfo.getResultCode());
      if (convertor != null) {
        final String advice = convertor.convert(callInfo);
        if (advice != null) return advice;
      }

      final Trinity<String, String, String> trinity = macMessages.get(callInfo.getResultCode());
      if (trinity == null) return null;
      return MessageFormat.format(ourGenericAdvice, callInfo.getFunctionName(),
                                  String.valueOf(callInfo.getResultCode()) + " ( " + trinity.getFirst() + " - " + trinity.getSecond() + ")");
    }

    static {
      ourAdvices.put(-25293, callInfo -> {
        if (!SystemInfo.isMac) return null;
        final Trinity<String, String, String> trinity = macMessages.get(callInfo.getResultCode());
        if (trinity == null) return null;
        return MessageFormat.format(ourGenericAdvice, callInfo.getFunctionName(),
                                    String.valueOf(callInfo.getResultCode()) + " ( " + trinity.getFirst() +
                                    " - " + trinity.getSecond() + ")" + "\nYou are likely to have modified " +
                                    ApplicationInfo.getInstance().getVersionName() + " bundle.\n" +
                                    "Please try to reinstall " + ApplicationInfo.getInstance().getVersionName());
      });

    }

    // from http://developer.apple.com/library/mac/#documentation/Security/Reference/keychainservices/Reference/reference.html
    static {
      macMessages.put(0, new Trinity<>("errSecSuccess", "No error.", "Available in OS X v10.6 and later."));
      macMessages.put(-4,
                      new Trinity<>("errSecUnimplemented", "Function or operation not implemented.", "Available in OS X v10.6 and later."));
      macMessages.put(-50, new Trinity<>("errSecParam", "One or more parameters passed to the function were not valid.",
                                         "Available in OS X v10.6 and later."));
      macMessages.put(-108, new Trinity<>("errSecAllocate", "Failed to allocate memory.", "Available in OS X v10.6 and later."));
      macMessages.put(-25291, new Trinity<>("errSecNotAvailable", "No trust results are available.", "Available in OS X v10.2 and later."));
      macMessages.put(-25292, new Trinity<>("errSecReadOnly", "Read only error.", "Available in OS X v10.2 and later."));
      macMessages.put(-25293,
                      new Trinity<>("errSecAuthFailed", "Authorization/Authentication failed.", "Available in OS X v10.2 and later."));
      macMessages.put(-25294, new Trinity<>("errSecNoSuchKeychain", "The keychain does not exist.", "Available in OS X v10.2 and later."));
      macMessages.put(-25295, new Trinity<>("errSecInvalidKeychain", "The keychain is not valid.", "Available in OS X v10.2 and later."));
      macMessages.put(-25296, new Trinity<>("errSecDuplicateKeychain", "A keychain with the same name already exists.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25297, new Trinity<>("errSecDuplicateCallback", "More than one callback of the same name exists.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25298, new Trinity<>("errSecInvalidCallback", "The callback is not valid.", "Available in OS X v10.2 and later."));
      macMessages.put(-25299, new Trinity<>("errSecDuplicateItem", "The item already exists.", "Available in OS X v10.2 and later."));
      macMessages.put(-25300, new Trinity<>("errSecItemNotFound", "The item cannot be found.", "Available in OS X v10.2 and later."));
      macMessages.put(-25301, new Trinity<>("errSecBufferTooSmall", "The buffer is too small.", "Available in OS X v10.2 and later."));
      macMessages.put(-25302, new Trinity<>("errSecDataTooLarge", "The data is too large for the particular data type.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25303, new Trinity<>("errSecNoSuchAttr", "The attribute does not exist.", "Available in OS X v10.2 and later."));
      macMessages.put(-25304, new Trinity<>("errSecInvalidItemRef", "The item reference is invalid.", "Available in OS X v10.2 and later."));
      macMessages.put(-25305,
                      new Trinity<>("errSecInvalidSearchRef", "The search reference is invalid.", "Available in OS X v10.2 and later."));
      macMessages.put(-25306,
                      new Trinity<>("errSecNoSuchClass", "The keychain item class does not exist.", "Available in OS X v10.2 and later."));
      macMessages.put(-25307,
                      new Trinity<>("errSecNoDefaultKeychain", "A default keychain does not exist.", "Available in OS X v10.2 and later."));
      macMessages.put(-25308, new Trinity<>("errSecInteractionNotAllowed", "Interaction with the Security Server is not allowed.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25309, new Trinity<>("errSecReadOnlyAttr", "The attribute is read only.", "Available in OS X v10.2 and later."));
      macMessages.put(-25310, new Trinity<>("errSecWrongSecVersion", "The version is incorrect.", "Available in OS X v10.2 and later."));
      macMessages.put(-25311, new Trinity<>("errSecKeySizeNotAllowed", "The key size is not allowed.", "Available in OS X v10.2 and later."));
      macMessages.put(-25312,
                      new Trinity<>("errSecNoStorageModule", "There is no storage module available.", "Available in OS X v10.2 and later."));
      macMessages.put(-25313, new Trinity<>("errSecNoCertificateModule", "There is no certificate module available.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25314,
                      new Trinity<>("errSecNoPolicyModule", "There is no policy module available.", "Available in OS X v10.2 and later."));
      macMessages.put(-25315,
                      new Trinity<>("errSecInteractionRequired", "User interaction is required.", "Available in OS X v10.2 and later."));
      macMessages.put(-25316, new Trinity<>("errSecDataNotAvailable", "The data is not available.", "Available in OS X v10.2 and later."));
      macMessages.put(-25317, new Trinity<>("errSecDataNotModifiable", "The data is not modifiable.", "Available in OS X v10.2 and later."));
      macMessages.put(-25318, new Trinity<>("errSecCreateChainFailed", "The attempt to create a certificate chain failed.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25319, new Trinity<>("errSecInvalidPrefsDomain",
                                            "The preference domain specified is invalid. This error is available in OS X v10.3 and later.",
                                            "Available in OS X v10.3 and later."));
      macMessages.put(-25320, new Trinity<>("errSecInDarkWake",
                                            "The user interface could not be displayed because the system is in a dark wake state.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-25240, new Trinity<>("errSecACLNotSimple", "The access control list is not in standard simple form.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25241,
                      new Trinity<>("errSecPolicyNotFound", "The policy specified cannot be found.", "Available in OS X v10.2 and later."));
      macMessages.put(-25262,
                      new Trinity<>("errSecInvalidTrustSetting", "The trust setting is invalid.", "Available in OS X v10.2 and later."));
      macMessages.put(-25243, new Trinity<>("errSecNoAccessForItem", "The specified item has no access control.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25244, new Trinity<>("errSecInvalidOwnerEdit", "An invalid attempt to change the owner of an item.",
                                            "Available in OS X v10.2 and later."));
      macMessages.put(-25245,
                      new Trinity<>("errSecTrustNotAvailable", "No trust results are available.", "Available in OS X v10.3 and later."));
      macMessages.put(-25256, new Trinity<>("errSecUnsupportedFormat", "The specified import or export format is not supported.",
                                            "Available in OS X v10.4 and later."));
      macMessages.put(-25257, new Trinity<>("errSecUnknownFormat", "The item you are trying to import has an unknown format.",
                                            "Available in OS X v10.4 and later."));
      macMessages.put(-25258,
                      new Trinity<>("errSecKeyIsSensitive", "The key must be wrapped to be exported.", "Available in OS X v10.4 and later."));
      macMessages.put(-25259, new Trinity<>("errSecMultiplePrivKeys", "An attempt was made to import multiple private keys.",
                                            "Available in OS X v10.4 and later."));
      macMessages.put(-25260, new Trinity<>("errSecPassphraseRequired", "A password is required for import or export.",
                                            "Available in OS X v10.4 and later."));
      macMessages.put(-25261,
                      new Trinity<>("errSecInvalidPasswordRef", "The password reference was invalid.", "Available in OS X v10.4 and later."));
      macMessages.put(-25262, new Trinity<>("errSecInvalidTrustSettings", "The trust settings record was corrupted.",
                                            "Available in OS X v10.5 and later."));
      macMessages.put(-25263, new Trinity<>("errSecNoTrustSettings", "No trust settings were found.", "Available in OS X v10.5 and later."));
      macMessages.put(-25264, new Trinity<>("errSecPkcs12VerifyFailure", "MAC verification failed during PKCS12 Import.",
                                            "Available in OS X v10.5 and later."));
      macMessages.put(-26267, new Trinity<>("errSecNotSigner", "The certificate was not signed by its proposed parent.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-26275, new Trinity<>("errSecDecode", "Unable to decode the provided data.", "Available in OS X v10.6 and later."));
      macMessages.put(-67585, new Trinity<>("errSecServiceNotAvailable", "The required service is not available.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67586,
                      new Trinity<>("errSecInsufficientClientID", "The client ID is not correct.", "Available in OS X v10.7 and later."));
      macMessages.put(-67587, new Trinity<>("errSecDeviceReset", "A device reset has occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67588, new Trinity<>("errSecDeviceFailed", "A device failure has occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67589, new Trinity<>("errSecAppleAddAppACLSubject", "Adding an application ACL subject failed.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67590,
                      new Trinity<>("errSecApplePublicKeyIncomplete", "The public key is incomplete.", "Available in OS X v10.7 and later."));
      macMessages.put(-67591, new Trinity<>("errSecAppleSignatureMismatch", "A signature mismatch has occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67592, new Trinity<>("errSecAppleInvalidKeyStartDate", "The specified key has an invalid start date.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67593, new Trinity<>("errSecAppleInvalidKeyEndDate", "The specified key has an invalid end date.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67594,
                      new Trinity<>("errSecConversionError", "A conversion error has occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67595, new Trinity<>("errSecAppleSSLv2Rollback", "A SSLv2 rollback error has occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-34, new Trinity<>("errSecDiskFull", "The disk is full.", "Available in OS X v10.7 and later."));
      macMessages.put(-67596, new Trinity<>("errSecQuotaExceeded", "The quota was exceeded.", "Available in OS X v10.7 and later."));
      macMessages.put(-67597, new Trinity<>("errSecFileTooBig", "The file is too big.", "Available in OS X v10.7 and later."));
      macMessages.put(-67598, new Trinity<>("errSecInvalidDatabaseBlob", "The specified database has an invalid blob.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67599, new Trinity<>("errSecInvalidKeyBlob", "The specified database has an invalid key blob.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67600, new Trinity<>("errSecIncompatibleDatabaseBlob", "The specified database has an incompatible blob.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67601, new Trinity<>("errSecIncompatibleKeyBlob", "The specified database has an incompatible key blob.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67602,
                      new Trinity<>("errSecHostNameMismatch", "A host name mismatch has occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67603, new Trinity<>("errSecUnknownCriticalExtensionFlag", "There is an unknown critical extension flag.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67604,
                      new Trinity<>("errSecNoBasicConstraints", "No basic constraints were found.", "Available in OS X v10.7 and later."));
      macMessages.put(-67605, new Trinity<>("errSecNoBasicConstraintsCA", "No basic CA constraints were found.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67606, new Trinity<>("errSecInvalidAuthorityKeyID", "The authority key ID is not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67607,
                      new Trinity<>("errSecInvalidSubjectKeyID", "The subject key ID is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67608, new Trinity<>("errSecInvalidKeyUsageForPolicy", "The key usage is not valid for the specified policy.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67609, new Trinity<>("errSecInvalidExtendedKeyUsage", "The extended key usage is not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67610, new Trinity<>("errSecInvalidIDLinkage", "The ID linkage is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67611, new Trinity<>("errSecPathLengthConstraintExceeded", "The path length constraint was exceeded.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67612, new Trinity<>("errSecInvalidRoot", "The root or anchor certificate is not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67613, new Trinity<>("errSecCRLExpired", "The CRL has expired.", "Available in OS X v10.7 and later."));
      macMessages.put(-67614, new Trinity<>("errSecCRLNotValidYet", "The CRL is not yet valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67615, new Trinity<>("errSecCRLNotFound", "The CRL was not found.", "Available in OS X v10.7 and later."));
      macMessages.put(-67616, new Trinity<>("errSecCRLServerDown", "The CRL server is down.", "Available in OS X v10.7 and later."));
      macMessages.put(-67617, new Trinity<>("errSecCRLBadURI", "The CRL has a bad Uniform Resource Identifier.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67618, new Trinity<>("errSecUnknownCertExtension", "An unknown certificate extension was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67619, new Trinity<>("errSecUnknownCRLExtension", "An unknown CRL extension was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67620, new Trinity<>("errSecCRLNotTrusted", "The CRL is not trusted.", "Available in OS X v10.7 and later."));
      macMessages.put(-67621, new Trinity<>("errSecCRLPolicyFailed", "The CRL policy failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67622, new Trinity<>("errSecIDPFailure", "The issuing distribution point was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67623, new Trinity<>("errSecSMIMEEmailAddressesNotFound", "An email address mismatch was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67624, new Trinity<>("errSecSMIMEBadExtendedKeyUsage", "The appropriate extended key usage for SMIME was not found.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67625, new Trinity<>("errSecSMIMEBadKeyUsage", "The key usage is not compatible with SMIME.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67626, new Trinity<>("errSecSMIMEKeyUsageNotCritical", "The key usage extension is not marked as critical.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67627, new Trinity<>("errSecSMIMENoEmailAddress", "No email address was found in the certificate.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67628, new Trinity<>("errSecSMIMESubjAltNameNotCritical",
                                            "The subject alternative name extension is not marked as critical.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67629, new Trinity<>("errSecSSLBadExtendedKeyUsage", "The appropriate extended key usage for SSL was not found.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67630, new Trinity<>("errSecOCSPBadResponse", "The OCSP response was incorrect or could not be parsed.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67631, new Trinity<>("errSecOCSPBadRequest", "The OCSP request was incorrect or could not be parsed.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67632, new Trinity<>("errSecOCSPUnavailable", "OCSP service is unavailable.", "Available in OS X v10.7 and later."));
      macMessages.put(-67633, new Trinity<>("errSecOCSPStatusUnrecognized", "The OCSP server did not recognize this certificate.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67634, new Trinity<>("errSecEndOfData", "An end-of-data was detected.", "Available in OS X v10.7 and later."));
      macMessages.put(-67635, new Trinity<>("errSecIncompleteCertRevocationCheck", "An incomplete certificate revocation check occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67636, new Trinity<>("errSecNetworkFailure", "A network failure occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67637,
                      new Trinity<>("errSecOCSPNotTrustedToAnchor", "The OCSP response was not trusted to a root or anchor certificate.",
                                    "Available in OS X v10.7 and later."));
      macMessages.put(-67638, new Trinity<>("errSecRecordModified", "The record was modified.", "Available in OS X v10.7 and later."));
      macMessages.put(-67639, new Trinity<>("errSecOCSPSignatureError", "The OCSP response had an invalid signature.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67640, new Trinity<>("errSecOCSPNoSigner", "The OCSP response had no signer.", "Available in OS X v10.7 and later."));
      macMessages.put(-67641, new Trinity<>("errSecOCSPResponderMalformedReq", "The OCSP responder was given a malformed request.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67642, new Trinity<>("errSecOCSPResponderInternalError", "The OCSP responder encountered an internal error.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67643, new Trinity<>("errSecOCSPResponderTryLater", "The OCSP responder is busy, try again later.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67644, new Trinity<>("errSecOCSPResponderSignatureRequired", "The OCSP responder requires a signature.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67645, new Trinity<>("errSecOCSPResponderUnauthorized", "The OCSP responder rejected this request as unauthorized.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67646, new Trinity<>("errSecOCSPResponseNonceMismatch", "The OCSP response nonce did not match the request.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67647, new Trinity<>("errSecCodeSigningBadCertChainLength",
                                            "Code signing encountered an incorrect certificate chain length.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67648, new Trinity<>("errSecCodeSigningNoBasicConstraints", "Code signing found no basic constraints.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67649, new Trinity<>("errSecCodeSigningBadPathLengthConstraint",
                                            "Code signing encountered an incorrect path length constraint.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67650, new Trinity<>("errSecCodeSigningNoExtendedKeyUsage", "Code signing found no extended key usage.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67651, new Trinity<>("errSecCodeSigningDevelopment", "Code signing indicated use of a development-only certificate.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67652, new Trinity<>("errSecResourceSignBadCertChainLength",
                                            "Resource signing has encountered an incorrect certificate chain length.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67653, new Trinity<>("errSecResourceSignBadExtKeyUsage",
                                            "Resource signing has encountered an error in the extended key usage.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67654, new Trinity<>("errSecTrustSettingDeny", "The trust setting for this policy was set to Deny.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67655, new Trinity<>("errSecInvalidSubjectName", "An invalid certificate subject name was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67656,
                      new Trinity<>("errSecUnknownQualifiedCertStatement", "An unknown qualified certificate statement was encountered.",
                                    "Available in OS X v10.7 and later."));
      macMessages.put(-67657, new Trinity<>("errSecMobileMeRequestQueued", "The MobileMe request will be sent during the next connection.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67658, new Trinity<>("errSecMobileMeRequestRedirected", "The MobileMe request was redirected.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67659,
                      new Trinity<>("errSecMobileMeServerError", "A MobileMe server error occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67660, new Trinity<>("errSecMobileMeServerNotAvailable", "The MobileMe server is not available.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67661,
                      new Trinity<>("errSecMobileMeServerAlreadyExists", "The MobileMe server reported that the item already exists.",
                                    "Available in OS X v10.7 and later."));
      macMessages.put(-67662, new Trinity<>("errSecMobileMeServerServiceErr", "A MobileMe service error has occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67663, new Trinity<>("errSecMobileMeRequestAlreadyPending", "A MobileMe request is already pending.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67664, new Trinity<>("errSecMobileMeNoRequestPending", "MobileMe has no request pending.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67665, new Trinity<>("errSecMobileMeCSRVerifyFailure", "A MobileMe CSR verification failure has occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67666, new Trinity<>("errSecMobileMeFailedConsistencyCheck", "MobileMe has found a failed consistency check.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67667, new Trinity<>("errSecNotInitialized", "A function was called without initializing CSSM.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67668, new Trinity<>("errSecInvalidHandleUsage", "The CSSM handle does not match with the service type.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67669, new Trinity<>("errSecPVCReferentNotFound",
                                            "A reference to the calling module was not found in the list of authorized callers.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67670, new Trinity<>("errSecFunctionIntegrityFail", "A function address was not within the verified module.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67671, new Trinity<>("errSecInternalError", "An internal error has occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67672, new Trinity<>("errSecMemoryError", "A memory error has occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67673, new Trinity<>("errSecInvalidData", "Invalid data was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67674, new Trinity<>("errSecMDSError", "A Module Directory Service error has occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67675,
                      new Trinity<>("errSecInvalidPointer", "An invalid pointer was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67676, new Trinity<>("errSecSelfCheckFailed", "Self-check has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67677, new Trinity<>("errSecFunctionFailed", "A function has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67678, new Trinity<>("errSecModuleManifestVerifyFailed", "A module manifest verification failure has occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67679, new Trinity<>("errSecInvalidGUID", "An invalid GUID was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67680,
                      new Trinity<>("errSecInvalidHandle", "An invalid handle was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67681,
                      new Trinity<>("errSecInvalidDBList", "An invalid DB list was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67682, new Trinity<>("errSecInvalidPassthroughID", "An invalid passthrough ID was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67683, new Trinity<>("errSecInvalidNetworkAddress", "An invalid network address was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67684, new Trinity<>("errSecCRLAlreadySigned", "The certificate revocation list is already signed.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67685, new Trinity<>("errSecInvalidNumberOfFields", "An invalid number of fields were encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67686,
                      new Trinity<>("errSecVerificationFailure", "A verification failure occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67687, new Trinity<>("errSecUnknownTag", "An unknown tag was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67688,
                      new Trinity<>("errSecInvalidSignature", "An invalid signature was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67689, new Trinity<>("errSecInvalidName", "An invalid name was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67690, new Trinity<>("errSecInvalidCertificateRef", "An invalid certificate reference was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67691, new Trinity<>("errSecInvalidCertificateGroup", "An invalid certificate group was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67692, new Trinity<>("errSecTagNotFound", "The specified tag was not found.", "Available in OS X v10.7 and later."));
      macMessages.put(-67693,
                      new Trinity<>("errSecInvalidQuery", "The specified query was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67694, new Trinity<>("errSecInvalidValue", "An invalid value was detected.", "Available in OS X v10.7 and later."));
      macMessages.put(-67695, new Trinity<>("errSecCallbackFailed", "A callback has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67696,
                      new Trinity<>("errSecACLDeleteFailed", "An ACL delete operation has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67697,
                      new Trinity<>("errSecACLReplaceFailed", "An ACL replace operation has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67698, new Trinity<>("errSecACLAddFailed", "An ACL add operation has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67699,
                      new Trinity<>("errSecACLChangeFailed", "An ACL change operation has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67700, new Trinity<>("errSecInvalidAccessCredentials", "Invalid access credentials were encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67701,
                      new Trinity<>("errSecInvalidRecord", "An invalid record was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67702, new Trinity<>("errSecInvalidACL", "An invalid ACL was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67703, new Trinity<>("errSecInvalidSampleValue", "An invalid sample value was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67704, new Trinity<>("errSecIncompatibleVersion", "An incompatible version was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67705,
                      new Trinity<>("errSecPrivilegeNotGranted", "The privilege was not granted.", "Available in OS X v10.7 and later."));
      macMessages.put(-67706, new Trinity<>("errSecInvalidScope", "An invalid scope was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67707,
                      new Trinity<>("errSecPVCAlreadyConfigured", "The PVC is already configured.", "Available in OS X v10.7 and later."));
      macMessages.put(-67708, new Trinity<>("errSecInvalidPVC", "An invalid PVC was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67709, new Trinity<>("errSecEMMLoadFailed", "The EMM load has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67710, new Trinity<>("errSecEMMUnloadFailed", "The EMM unload has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67711,
                      new Trinity<>("errSecAddinLoadFailed", "The add-in load operation has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67712, new Trinity<>("errSecInvalidKeyRef", "An invalid key was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67713, new Trinity<>("errSecInvalidKeyHierarchy", "An invalid key hierarchy was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67714, new Trinity<>("errSecAddinUnloadFailed", "The add-in unload operation has failed.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67715, new Trinity<>("errSecLibraryReferenceNotFound", "A library reference was not found.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67716, new Trinity<>("errSecInvalidAddinFunctionTable", "An invalid add-in function table was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67717, new Trinity<>("errSecInvalidServiceMask", "An invalid service mask was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67718, new Trinity<>("errSecModuleNotLoaded", "A module was not loaded.", "Available in OS X v10.7 and later."));
      macMessages.put(-67719, new Trinity<>("errSecInvalidSubServiceID", "An invalid subservice ID was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67720, new Trinity<>("errSecAttributeNotInContext", "An attribute was not in the context.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67721, new Trinity<>("errSecModuleManagerInitializeFailed", "A module failed to initialize.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67722, new Trinity<>("errSecModuleManagerNotFound", "A module was not found.", "Available in OS X v10.7 and later."));
      macMessages.put(-67723, new Trinity<>("errSecEventNotificationCallbackNotFound", "An event notification callback was not found.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67724, new Trinity<>("errSecInputLengthError", "An input length error was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67725, new Trinity<>("errSecOutputLengthError", "An output length error was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67726,
                      new Trinity<>("errSecPrivilegeNotSupported", "The privilege is not supported.", "Available in OS X v10.7 and later."));
      macMessages.put(-67727, new Trinity<>("errSecDeviceError", "A device error was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67728, new Trinity<>("errSecAttachHandleBusy", "The CSP handle was busy.", "Available in OS X v10.7 and later."));
      macMessages.put(-67729, new Trinity<>("errSecNotLoggedIn", "You are not logged in.", "Available in OS X v10.7 and later."));
      macMessages.put(-67730, new Trinity<>("errSecAlgorithmMismatch", "An algorithm mismatch was encountered.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67731, new Trinity<>("errSecKeyUsageIncorrect", "The key usage is incorrect.", "Available in OS X v10.7 and later."));
      macMessages.put(-67732,
                      new Trinity<>("errSecKeyBlobTypeIncorrect", "The key blob type is incorrect.", "Available in OS X v10.7 and later."));
      macMessages.put(-67733,
                      new Trinity<>("errSecKeyHeaderInconsistent", "The key header is inconsistent.", "Available in OS X v10.7 and later."));
      macMessages.put(-67734, new Trinity<>("errSecUnsupportedKeyFormat", "The key header format is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67735,
                      new Trinity<>("errSecUnsupportedKeySize", "The key size is not supported.", "Available in OS X v10.7 and later."));
      macMessages.put(-67736,
                      new Trinity<>("errSecInvalidKeyUsageMask", "The key usage mask is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67737, new Trinity<>("errSecUnsupportedKeyUsageMask", "The key usage mask is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67738, new Trinity<>("errSecInvalidKeyAttributeMask", "The key attribute mask is not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67739, new Trinity<>("errSecUnsupportedKeyAttributeMask", "The key attribute mask is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67740, new Trinity<>("errSecInvalidKeyLabel", "The key label is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67741,
                      new Trinity<>("errSecUnsupportedKeyLabel", "The key label is not supported.", "Available in OS X v10.7 and later."));
      macMessages.put(-67742, new Trinity<>("errSecInvalidKeyFormat", "The key format is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67743, new Trinity<>("errSecUnsupportedVectorOfBuffers", "The vector of buffers is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67744,
                      new Trinity<>("errSecInvalidInputVector", "The input vector is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67745,
                      new Trinity<>("errSecInvalidOutputVector", "The output vector is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67746,
                      new Trinity<>("errSecInvalidContext", "An invalid context was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67747,
                      new Trinity<>("errSecInvalidAlgorithm", "An invalid algorithm was encountered.", "Available in OS X v10.7 and later."));
      macMessages.put(-67748,
                      new Trinity<>("errSecInvalidAttributeKey", "A key attribute was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67749,
                      new Trinity<>("errSecMissingAttributeKey", "A key attribute was missing.", "Available in OS X v10.7 and later."));
      macMessages.put(-67750, new Trinity<>("errSecInvalidAttributeInitVector", "An init vector attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67751, new Trinity<>("errSecMissingAttributeInitVector", "An init vector attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67752,
                      new Trinity<>("errSecInvalidAttributeSalt", "A salt attribute was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67753,
                      new Trinity<>("errSecMissingAttributeSalt", "A salt attribute was missing.", "Available in OS X v10.7 and later."));
      macMessages.put(-67754, new Trinity<>("errSecInvalidAttributePadding", "A padding attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67755, new Trinity<>("errSecMissingAttributePadding", "A padding attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67756, new Trinity<>("errSecInvalidAttributeRandom", "A random number attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67757, new Trinity<>("errSecMissingAttributeRandom", "A random number attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67758,
                      new Trinity<>("errSecInvalidAttributeSeed", "A seed attribute was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67759,
                      new Trinity<>("errSecMissingAttributeSeed", "A seed attribute was missing.", "Available in OS X v10.7 and later."));
      macMessages.put(-67760, new Trinity<>("errSecInvalidAttributePassphrase", "A passphrase attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67761, new Trinity<>("errSecMissingAttributePassphrase", "A passphrase attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67762, new Trinity<>("errSecInvalidAttributeKeyLength", "A key length attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67763, new Trinity<>("errSecMissingAttributeKeyLength", "A key length attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67764, new Trinity<>("errSecInvalidAttributeBlockSize", "A block size attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67765, new Trinity<>("errSecMissingAttributeBlockSize", "A block size attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67766, new Trinity<>("errSecInvalidAttributeOutputSize", "An output size attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67767, new Trinity<>("errSecMissingAttributeOutputSize", "An output size attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67768, new Trinity<>("errSecInvalidAttributeRounds", "The number of rounds attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67769, new Trinity<>("errSecMissingAttributeRounds", "The number of rounds attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67770, new Trinity<>("errSecInvalidAlgorithmParms", "An algorithm parameters attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67771, new Trinity<>("errSecMissingAlgorithmParms", "An algorithm parameters attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67772,
                      new Trinity<>("errSecInvalidAttributeLabel", "A label attribute was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67773,
                      new Trinity<>("errSecMissingAttributeLabel", "A label attribute was missing.", "Available in OS X v10.7 and later."));
      macMessages.put(-67774, new Trinity<>("errSecInvalidAttributeKeyType", "A key type attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67775, new Trinity<>("errSecMissingAttributeKeyType", "A key type attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67776,
                      new Trinity<>("errSecInvalidAttributeMode", "A mode attribute was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67777,
                      new Trinity<>("errSecMissingAttributeMode", "A mode attribute was missing.", "Available in OS X v10.7 and later."));
      macMessages.put(-67778, new Trinity<>("errSecInvalidAttributeEffectiveBits", "An effective bits attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67779, new Trinity<>("errSecMissingAttributeEffectiveBits", "An effective bits attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67780, new Trinity<>("errSecInvalidAttributeStartDate", "A start date attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67781, new Trinity<>("errSecMissingAttributeStartDate", "A start date attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67782, new Trinity<>("errSecInvalidAttributeEndDate", "An end date attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67783, new Trinity<>("errSecMissingAttributeEndDate", "An end date attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67784, new Trinity<>("errSecInvalidAttributeVersion", "A version attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67785, new Trinity<>("errSecMissingAttributeVersion", "A version attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67786,
                      new Trinity<>("errSecInvalidAttributePrime", "A prime attribute was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67787,
                      new Trinity<>("errSecMissingAttributePrime", "A prime attribute was missing.", "Available in OS X v10.7 and later."));
      macMessages.put(-67788,
                      new Trinity<>("errSecInvalidAttributeBase", "A base attribute was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67789,
                      new Trinity<>("errSecMissingAttributeBase", "A base attribute was missing.", "Available in OS X v10.7 and later."));
      macMessages.put(-67790, new Trinity<>("errSecInvalidAttributeSubprime", "A subprime attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67791, new Trinity<>("errSecMissingAttributeSubprime", "A subprime attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67792, new Trinity<>("errSecInvalidAttributeIterationCount", "An iteration count attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67793, new Trinity<>("errSecMissingAttributeIterationCount", "An iteration count attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67794, new Trinity<>("errSecInvalidAttributeDLDBHandle", "A database handle attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67795, new Trinity<>("errSecMissingAttributeDLDBHandle", "A database handle attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67796, new Trinity<>("errSecInvalidAttributeAccessCredentials", "An access credentials attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67797, new Trinity<>("errSecMissingAttributeAccessCredentials", "An access credentials attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67798, new Trinity<>("errSecInvalidAttributePublicKeyFormat", "A public key format attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67799, new Trinity<>("errSecMissingAttributePublicKeyFormat", "A public key format attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67800, new Trinity<>("errSecInvalidAttributePrivateKeyFormat", "A private key format attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67801, new Trinity<>("errSecMissingAttributePrivateKeyFormat", "A private key format attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67802, new Trinity<>("errSecInvalidAttributeSymmetricKeyFormat", "A symmetric key format attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67803, new Trinity<>("errSecMissingAttributeSymmetricKeyFormat", "A symmetric key format attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67804, new Trinity<>("errSecInvalidAttributeWrappedKeyFormat", "A wrapped key format attribute was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67805, new Trinity<>("errSecMissingAttributeWrappedKeyFormat", "A wrapped key format attribute was missing.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67806, new Trinity<>("errSecStagedOperationInProgress", "A staged operation is in progress.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67807, new Trinity<>("errSecStagedOperationNotStarted", "A staged operation was not started.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67808, new Trinity<>("errSecVerifyFailed", "A cryptographic verification failure has occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67809, new Trinity<>("errSecQuerySizeUnknown", "The query size is unknown.", "Available in OS X v10.7 and later."));
      macMessages.put(-67810,
                      new Trinity<>("errSecBlockSizeMismatch", "A block size mismatch occurred.", "Available in OS X v10.7 and later."));
      macMessages.put(-67811,
                      new Trinity<>("errSecPublicKeyInconsistent", "The public key was inconsistent.", "Available in OS X v10.7 and later."));
      macMessages.put(-67812, new Trinity<>("errSecDeviceVerifyFailed", "A device verification failure has occurred.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67813,
                      new Trinity<>("errSecInvalidLoginName", "An invalid login name was detected.", "Available in OS X v10.7 and later."));
      macMessages.put(-67814, new Trinity<>("errSecAlreadyLoggedIn", "The user is already logged in.", "Available in OS X v10.7 and later."));
      macMessages.put(-67815, new Trinity<>("errSecInvalidDigestAlgorithm", "An invalid digest algorithm was detected.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67816,
                      new Trinity<>("errSecInvalidCRLGroup", "An invalid CRL group was detected.", "Available in OS X v10.7 and later."));
      macMessages.put(-67817, new Trinity<>("errSecCertificateCannotOperate", "The certificate cannot operate.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67818, new Trinity<>("errSecCertificateExpired", "An expired certificate was detected.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67819, new Trinity<>("errSecCertificateNotValidYet", "The certificate is not yet valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67820,
                      new Trinity<>("errSecCertificateRevoked", "The certificate was revoked.", "Available in OS X v10.7 and later."));
      macMessages.put(-67821,
                      new Trinity<>("errSecCertificateSuspended", "The certificate was suspended.", "Available in OS X v10.7 and later."));
      macMessages.put(-67822, new Trinity<>("errSecInsufficientCredentials", "Insufficient credentials were detected.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67823, new Trinity<>("errSecInvalidAction", "The action was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67824, new Trinity<>("errSecInvalidAuthority", "The authority was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67825, new Trinity<>("errSecVerifyActionFailed", "A verify action has failed.", "Available in OS X v10.7 and later."));
      macMessages.put(-67826, new Trinity<>("errSecInvalidCertAuthority", "The certificate authority was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67827,
                      new Trinity<>("errSecInvaldCRLAuthority", "The CRL authority was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67828,
                      new Trinity<>("errSecInvalidCRLEncoding", "The CRL encoding was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67829, new Trinity<>("errSecInvalidCRLType", "The CRL type was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67830, new Trinity<>("errSecInvalidCRL", "The CRL was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67831, new Trinity<>("errSecInvalidFormType", "The form type was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67832, new Trinity<>("errSecInvalidID", "The ID was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67833,
                      new Trinity<>("errSecInvalidIdentifier", "The identifier was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67834, new Trinity<>("errSecInvalidIndex", "The index was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67835, new Trinity<>("errSecInvalidPolicyIdentifiers", "The policy identifiers are not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67836,
                      new Trinity<>("errSecInvalidTimeString", "The time specified was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67837,
                      new Trinity<>("errSecInvalidReason", "The trust policy reason was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67838,
                      new Trinity<>("errSecInvalidRequestInputs", "The request inputs are not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67839, new Trinity<>("errSecInvalidResponseVector", "The response vector was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67840,
                      new Trinity<>("errSecInvalidStopOnPolicy", "The stop-on policy was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67841, new Trinity<>("errSecInvalidTuple", "The tuple was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67842, new Trinity<>("errSecMultipleValuesUnsupported", "Multiple values are not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67843, new Trinity<>("errSecNotTrusted", "The trust policy was not trusted.", "Available in OS X v10.7 and later."));
      macMessages.put(-67844,
                      new Trinity<>("errSecNoDefaultAuthority", "No default authority was detected.", "Available in OS X v10.7 and later."));
      macMessages.put(-67845,
                      new Trinity<>("errSecRejectedForm", "The trust policy had a rejected form.", "Available in OS X v10.7 and later."));
      macMessages.put(-67846, new Trinity<>("errSecRequestLost", "The request was lost.", "Available in OS X v10.7 and later."));
      macMessages.put(-67847, new Trinity<>("errSecRequestRejected", "The request was rejected.", "Available in OS X v10.7 and later."));
      macMessages.put(-67848, new Trinity<>("errSecUnsupportedAddressType", "The address type is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67849,
                      new Trinity<>("errSecUnsupportedService", "The service is not supported.", "Available in OS X v10.7 and later."));
      macMessages.put(-67850,
                      new Trinity<>("errSecInvalidTupleGroup", "The tuple group was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67851, new Trinity<>("errSecInvalidBaseACLs", "The base ACLs are not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67852, new Trinity<>("errSecInvalidTupleCredendtials", "The tuple credentials are not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67853, new Trinity<>("errSecInvalidEncoding", "The encoding was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67854, new Trinity<>("errSecInvalidValidityPeriod", "The validity period was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67855, new Trinity<>("errSecInvalidRequestor", "The requestor was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67856, new Trinity<>("errSecRequestDescriptor", "The request descriptor was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67857, new Trinity<>("errSecInvalidBundleInfo", "The bundle information was not valid.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67858, new Trinity<>("errSecInvalidCRLIndex", "The CRL index was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67859, new Trinity<>("errSecNoFieldValues", "No field values were detected.", "Available in OS X v10.7 and later."));
      macMessages.put(-67860, new Trinity<>("errSecUnsupportedFieldFormat", "The field format is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67861, new Trinity<>("errSecUnsupportedIndexInfo", "The index information is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67862,
                      new Trinity<>("errSecUnsupportedLocality", "The locality is not supported.", "Available in OS X v10.7 and later."));
      macMessages.put(-67863, new Trinity<>("errSecUnsupportedNumAttributes", "The number of attributes is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67864, new Trinity<>("errSecUnsupportedNumIndexes", "The number of indexes is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67865, new Trinity<>("errSecUnsupportedNumRecordTypes", "The number of record types is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67866,
                      new Trinity<>("errSecFieldSpecifiedMultiple", "Too many fields were specified.", "Available in OS X v10.7 and later."));
      macMessages.put(-67867, new Trinity<>("errSecIncompatibleFieldFormat", "The field format was incompatible.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67868,
                      new Trinity<>("errSecInvalidParsingModule", "The parsing module was not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67869, new Trinity<>("errSecDatabaseLocked", "The database is locked.", "Available in OS X v10.7 and later."));
      macMessages.put(-67870, new Trinity<>("errSecDatastoreIsOpen", "The data store is open.", "Available in OS X v10.7 and later."));
      macMessages.put(-67871, new Trinity<>("errSecMissingValue", "A missing value was detected.", "Available in OS X v10.7 and later."));
      macMessages.put(-67872, new Trinity<>("errSecUnsupportedQueryLimits", "The query limits are not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67873, new Trinity<>("errSecUnsupportedNumSelectionPreds", "The number of selection predicates is not supported.",
                                            "Available in OS X v10.7 and later."));
      macMessages.put(-67874,
                      new Trinity<>("errSecUnsupportedOperator", "The operator is not supported.", "Available in OS X v10.7 and later."));
      macMessages.put(-67875,
                      new Trinity<>("errSecInvalidDBLocation", "The database location is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67876,
                      new Trinity<>("errSecInvalidAccessRequest", "The access request is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67877,
                      new Trinity<>("errSecInvalidIndexInfo", "The index information is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67878, new Trinity<>("errSecInvalidNewOwner", "The new owner is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67879,
                      new Trinity<>("errSecInvalidModifyMode", "The modify mode is not valid.", "Available in OS X v10.7 and later."));
      macMessages.put(-67880, new Trinity<>("errSecMissingRequiredExtension", "A required certificate extension is missing.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67881,
                      new Trinity<>("errSecExtendedKeyUsageNotCritical", "The extended key usage extension was not marked critical.",
                                    "Available in OS X v10.8 and later."));
      macMessages.put(-67882, new Trinity<>("errSecTimestampMissing", "A timestamp was expected but was not found.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67883, new Trinity<>("errSecTimestampInvalid", "The timestamp was not valid.", "Available in OS X v10.8 and later."));
      macMessages.put(-67884,
                      new Trinity<>("errSecTimestampNotTrusted", "The timestamp was not trusted.", "Available in OS X v10.8 and later."));
      macMessages.put(-67885, new Trinity<>("errSecTimestampServiceNotAvailable", "The timestamp service is not available.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67886,
                      new Trinity<>("errSecTimestampBadAlg", "Found an unrecognized or unsupported algorithm identifier (AI) in timestamp.",
                                    "Available in OS X v10.8 and later."));
      macMessages.put(-67887, new Trinity<>("errSecTimestampBadRequest", "The timestamp transaction is not permitted or supported.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67888, new Trinity<>("errSecTimestampBadDataFormat", "The timestamp data submitted has the wrong format.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67889,
                      new Trinity<>("errSecTimestampTimeNotAvailable", "The time source for the timestamp authority is not available.",
                                    "Available in OS X v10.8 and later."));
      macMessages.put(-67890,
                      new Trinity<>("errSecTimestampUnacceptedPolicy", "The requested policy is not supported by the timestamp authority.",
                                    "Available in OS X v10.8 and later."));
      macMessages.put(-67891, new Trinity<>("errSecTimestampUnacceptedExtension",
                                            "The requested extension is not supported by the timestamp authority.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67892, new Trinity<>("errSecTimestampAddInfoNotAvailable", "The additional information requested is not available.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67893,
                      new Trinity<>("errSecTimestampSystemFailure", "The timestamp request cannot be handled due to a system failure .",
                                    "Available in OS X v10.8 and later."));
      macMessages.put(-67894, new Trinity<>("errSecSigningTimeMissing", "A signing time was expected but was not found.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67895, new Trinity<>("errSecTimestampRejection", "A timestamp transaction was rejected.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67896,
                      new Trinity<>("errSecTimestampWaiting", "A timestamp transaction is waiting.", "Available in OS X v10.8 and later."));
      macMessages.put(-67897, new Trinity<>("errSecTimestampRevocationWarning", "A timestamp authority revocation warning was issued.",
                                            "Available in OS X v10.8 and later."));
      macMessages.put(-67898,
                      new Trinity<>("errSecTimestampRevocationNotification", "A timestamp authority revocation notification was issued.",
                                    "Available in OS X v10.8 and later."));
    }
  }
}
