from _typeshed import Incomplete, ReadableBuffer
from collections.abc import Iterable, Sequence
from typing import Literal, overload

import _win32typing

def CryptProtectData(
    DataIn: ReadableBuffer,
    DataDescr: str | None = None,
    OptionalEntropy: bytes | None = None,
    Reserved: None = None,
    PromptStruct: (
        tuple[int]
        | tuple[int, int | _win32typing.PyHANDLE | None]
        | tuple[int, int | _win32typing.PyHANDLE | None, str | None]
        | None
    ) = None,
    Flags: int = 0,
) -> bytes: ...
def CryptUnprotectData(
    DataIn: ReadableBuffer,
    OptionalEntropy: bytes | None = None,
    Reserved: None = None,
    PromptStruct: (
        tuple[int]
        | tuple[int, int | _win32typing.PyHANDLE | None]
        | tuple[int, int | _win32typing.PyHANDLE | None, str | None]
        | None
    ) = None,
    Flags: int = 0,
) -> tuple[str, bytes]: ...
def CryptEnumProviders() -> list[tuple[str, int]]: ...
def CryptEnumProviderTypes() -> list[tuple[str | None, int]]: ...
def CryptGetDefaultProvider(ProvType: int, Flags: int) -> str: ...
def CryptSetProviderEx(ProvName: str | None, ProvType: int, Flags: int) -> None: ...
def CryptAcquireContext(
    Container: str | None, Provider: str | None, ProvType: int, Flags: int
) -> _win32typing.PyCRYPTPROV | None: ...
def CryptFindLocalizedName(CryptName: str) -> str | None: ...
def CertEnumSystemStore(
    Flags: int, SystemStoreLocationPara: str | Sequence[int | _win32typing.PyHANDLE | None | str] | None = None
) -> list[str]: ...
def CertEnumSystemStoreLocation(Flags: int = 0) -> list[str]: ...
def CertEnumPhysicalStore(SystemStore: str, Flags: int) -> list[str]: ...
def CertRegisterSystemStore(SystemStore: str | Sequence[int | _win32typing.PyHANDLE | None | str], Flags: int) -> None: ...
def CertUnregisterSystemStore(SystemStore: str, Flags: int) -> None: ...
def CertOpenStore(
    StoreProvider: int,
    MsgAndCertEncodingType: int,
    CryptProv: _win32typing.PyCRYPTPROV | None,
    Flags: int,
    Para: str | Sequence[int | _win32typing.PyHANDLE | None | str] | None,
) -> _win32typing.PyCERTSTORE | None: ...
def CertOpenSystemStore(SubsystemProtocol: str, Prov: _win32typing.PyCRYPTPROV | None = None) -> _win32typing.PyCERTSTORE: ...

@overload
def CryptFindOIDInfo(KeyType: Literal[1], Key: bytes, GroupId: int = 0) -> _win32typing.OIDInfo: ...
@overload
def CryptFindOIDInfo(KeyType: Literal[2], Key: str, GroupId: int = 0) -> _win32typing.OIDInfo: ...
@overload
def CryptFindOIDInfo(KeyType: Literal[3], Key: int, GroupId: int = 0) -> _win32typing.OIDInfo: ...
@overload
def CryptFindOIDInfo(KeyType: Literal[4], Key: tuple[int, int], GroupId: int = 0) -> _win32typing.OIDInfo: ...

def CertAlgIdToOID(AlgId: int) -> bytes | None: ...
def CertOIDToAlgId(ObjId: str) -> int: ...
def CryptGetKeyIdentifierProperty(
    KeyIdentifier: ReadableBuffer, PropId: int = 2, Flags: int = 0, ComputerName: str | None = None
) -> _win32typing.ProveInfo: ...
def CryptEnumKeyIdentifierProperties(
    KeyIdentifier: ReadableBuffer | None = None, PropId: int = 0, Flags: int = 0, ComputerName: str | None = None
) -> list[_win32typing.KeyIdentifier]: ...
def CryptEnumOIDInfo(GroupId: int = 0) -> list[_win32typing.OIDInfo]: ...
def CertAddSerializedElementToStore(
    CertStore: _win32typing.PyCERTSTORE | None,
    Element: ReadableBuffer,
    AddDisposition: int,
    ContextTypeFlags: int = 2,
    Flags: int = 0,
) -> _win32typing.PyCERT_CONTEXT | _win32typing.PyCTL_CONTEXT | None: ...

@overload
def CryptQueryObject(
    ObjectType: Literal[1],
    Object: ReadableBuffer,
    ExpectedContentTypeFlags: int = 0x00003FFE,
    ExpectedFormatTypeFlags: int = 0x0000000E,
    Flags: int = 0,
) -> _win32typing.QueryObject: ...
@overload
def CryptQueryObject(
    ObjectType: Literal[2],
    Object: str,
    ExpectedContentTypeFlags: int = 0x00003FFE,
    ExpectedFormatTypeFlags: int = 0x0000000E,
    Flags: int = 0,
) -> _win32typing.QueryObject: ...

def CryptDecodeMessage(
    EncodedBlob: ReadableBuffer,
    DecryptPara: _win32typing.DecryptMessagePara,
    VerifyPara: _win32typing.VerifyMessagePara | None = None,
    MsgTypeFlags: int = -1,
    SignerIndex: int = 0,
    PrevInnerContentType: int = 0,
    ReturnData: bool | Literal[0, 1] = True,
) -> _win32typing.DecodeMessage: ...
def CryptEncryptMessage(
    EncryptPara: _win32typing.EncryptMessagePara,
    RecipientCert: Iterable[_win32typing.PyCERT_CONTEXT] | None,
    ToBeEncrypted: ReadableBuffer,
) -> bytes: ...
def CryptDecryptMessage(
    DecryptPara: _win32typing.DecryptMessagePara, EncryptedBlob: ReadableBuffer
) -> tuple[bytes, _win32typing.PyCERT_CONTEXT]: ...
def CryptSignAndEncryptMessage(
    SignPara: _win32typing.SignMessagePara,
    EncryptPara: _win32typing.EncryptMessagePara,
    RecipientCert: Iterable[_win32typing.PyCERT_CONTEXT] | None,
    ToBeSignedAndEncrypted: ReadableBuffer,
) -> bytes: ...
def CryptVerifyMessageSignature(
    SignedBlob: ReadableBuffer,
    SignerIndex: int = 0,
    VerifyPara: _win32typing.VerifyMessagePara | None = None,
    ReturnData: bool | Literal[0, 1] = False,
) -> _win32typing.VerifyMessageSignature: ...
def CryptGetMessageCertificates(
    SignedBlob: ReadableBuffer,
    MsgAndCertEncodingType: int = ...,
    CryptProv: _win32typing.PyCRYPTPROV | None = None,
    Flags: int = 0,
) -> _win32typing.PyCERTSTORE: ...
def CryptGetMessageSignerCount(SignedBlob: ReadableBuffer, MsgEncodingType: int = ...) -> int: ...
def CryptSignMessage(
    SignPara: _win32typing.SignMessagePara, ToBeSigned: Iterable[Incomplete], DetachedSignature: bool | Literal[0, 1] = False
) -> bytes: ...
def CryptVerifyDetachedMessageSignature(
    SignerIndex: int,
    DetachedSignBlob: ReadableBuffer,
    ToBeSigned: Iterable[Incomplete],
    VerifyPara: _win32typing.VerifyMessagePara | None = None,
) -> _win32typing.PyCERT_CONTEXT: ...
def CryptDecryptAndVerifyMessageSignature(
    EncryptedBlob: ReadableBuffer,
    DecryptPara: _win32typing.DecryptMessagePara,
    VerifyPara: _win32typing.VerifyMessagePara | None = None,
    SignerIndex: int = 0,
) -> _win32typing.DecryptAndVerifyMessageSignature: ...
def CryptEncodeObjectEx(
    StructType: int | bytes,
    StructInfo: Iterable[int | bytes] | _win32typing.CryptBitBlob,
    Flags: int = 0,
    CertEncodingType: int = ...,
    EncodePara: None = None,
) -> bytes: ...
def CryptDecodeObjectEx(
    StructType: int | bytes, Encoded: ReadableBuffer, Flags: int = 0, CertEncodingType: int = ..., DecodePara: None = None
): ...
def CertNameToStr(Name: ReadableBuffer, StrType: int = 1, CertEncodingType: int = 0x00000001) -> str: ...
def CryptFormatObject(
    StructType: int | bytes,
    Encoded: ReadableBuffer,
    FormatStrType: int = 0,
    CertEncodingType: int = 0x00000001,
    FormatType: int = 0,
    FormatStruct: None = None,
) -> str: ...
def PFXImportCertStore(PFX: ReadableBuffer, Password: str | None, Flags: int) -> _win32typing.PyCERTSTORE: ...
def PFXVerifyPassword(PFX: ReadableBuffer, Password: str | None, Flags: int) -> bool: ...
def PFXIsPFXBlob(PFX: ReadableBuffer) -> bool: ...
def CryptBinaryToString(Binary: ReadableBuffer, Flags: int) -> str: ...
def CryptStringToBinary(String: str, Flags: int) -> tuple[bytes, int, int]: ...
