# TODO(MichalPokorny): more precise types

from typing import Any, Tuple, Optional

GLOBAL_SSL = ...  # type: int
GLOBAL_WIN32 = ...  # type: int
GLOBAL_ALL = ...  # type: int
GLOBAL_NOTHING = ...  # type: int
GLOBAL_DEFAULT = ...  # type: int

def global_init(option: int) -> None: ...
def global_cleanup() -> None: ...

version = ...  # type: str

def version_info() -> Tuple[int, str, int, str, int, str,
                            int, str, tuple, Any, int, Any]: ...

class error(Exception):
    pass

class Curl(object):
    def close(self) -> None: ...
    def setopt(self, option: int, value: Any) -> None: ...
    def perform(self) -> None: ...
    def getinfo(self, info: Any) -> Any: ...
    def reset(self) -> None: ...
    def unsetopt(self, option: int) -> Any: ...
    def pause(self, bitmask: Any) -> Any: ...
    def errstr(self) -> str: ...

    # TODO(MichalPokorny): wat?
    USERPWD = ...  # type: int

class CurlMulti(object):
    def close(self) -> None: ...
    def add_handle(self, obj: Curl) -> None: ...
    def remove_handle(self, obj: Curl) -> None: ...
    def perform(self) -> Tuple[Any, int]: ...
    def fdset(self) -> tuple: ...
    def select(self, timeout: float = ...) -> int: ...
    def info_read(self, max_objects: int) -> tuple: ...

class CurlShare(object):
    def close(self) -> None: ...
    def setopt(self, option: int, value: Any) -> Any: ...

ADDRESS_SCOPE = ...  # type: int
APPCONNECT_TIME = ...  # type: int
AUTOREFERER = ...  # type: int
BUFFERSIZE = ...  # type: int
CAINFO = ...  # type: int
CAPATH = ...  # type: int
COMPILE_LIBCURL_VERSION_NUM = ...  # type: int
COMPILE_PY_VERSION_HEX = ...  # type: int
CONNECTTIMEOUT = ...  # type: int
CONNECTTIMEOUT_MS = ...  # type: int
CONNECT_ONLY = ...  # type: int
CONNECT_TIME = ...  # type: int
CONTENT_LENGTH_DOWNLOAD = ...  # type: int
CONTENT_LENGTH_UPLOAD = ...  # type: int
CONTENT_TYPE = ...  # type: int
COOKIE = ...  # type: int
COOKIEFILE = ...  # type: int
COOKIEJAR = ...  # type: int
COOKIELIST = ...  # type: int
COPYPOSTFIELDS = ...  # type: int
CRLF = ...  # type: int
CRLFILE = ...  # type: int
CSELECT_ERR = ...  # type: int
CSELECT_IN = ...  # type: int
CSELECT_OUT = ...  # type: int
CURL_HTTP_VERSION_1_0 = ...  # type: int
CURL_HTTP_VERSION_1_1 = ...  # type: int
CURL_HTTP_VERSION_LAST = ...  # type: int
CURL_HTTP_VERSION_NONE = ...  # type: int
CUSTOMREQUEST = ...  # type: int
DEBUGFUNCTION = ...  # type: int
DNS_CACHE_TIMEOUT = ...  # type: int
DNS_USE_GLOBAL_CACHE = ...  # type: int
EFFECTIVE_URL = ...  # type: int
EGDSOCKET = ...  # type: int
ENCODING = ...  # type: int
FAILONERROR = ...  # type: int
FILE = ...  # type: int
FOLLOWLOCATION = ...  # type: int
FORBID_REUSE = ...  # type: int
FORM_CONTENTS = ...  # type: int
FORM_CONTENTTYPE = ...  # type: int
FORM_FILE = ...  # type: int
FORM_FILENAME = ...  # type: int
FRESH_CONNECT = ...  # type: int
FTPAPPEND = ...  # type: int
FTPAUTH_DEFAULT = ...  # type: int
FTPAUTH_SSL = ...  # type: int
FTPAUTH_TLS = ...  # type: int
FTPLISTONLY = ...  # type: int
FTPMETHOD_DEFAULT = ...  # type: int
FTPMETHOD_MULTICWD = ...  # type: int
FTPMETHOD_NOCWD = ...  # type: int
FTPMETHOD_SINGLECWD = ...  # type: int
FTPPORT = ...  # type: int
FTPSSLAUTH = ...  # type: int
FTPSSL_ALL = ...  # type: int
FTPSSL_CONTROL = ...  # type: int
FTPSSL_NONE = ...  # type: int
FTPSSL_TRY = ...  # type: int
FTP_ACCOUNT = ...  # type: int
FTP_ALTERNATIVE_TO_USER = ...  # type: int
FTP_CREATE_MISSING_DIRS = ...  # type: int
FTP_ENTRY_PATH = ...  # type: int
FTP_FILEMETHOD = ...  # type: int
FTP_RESPONSE_TIMEOUT = ...  # type: int
FTP_SKIP_PASV_IP = ...  # type: int
FTP_SSL = ...  # type: int
FTP_SSL_CCC = ...  # type: int
FTP_USE_EPRT = ...  # type: int
FTP_USE_EPSV = ...  # type: int
HEADER = ...  # type: int
HEADERFUNCTION = ...  # type: int
HEADER_SIZE = ...  # type: int
HTTP200ALIASES = ...  # type: int
HTTPAUTH = ...  # type: int
HTTPAUTH_ANY = ...  # type: int
HTTPAUTH_ANYSAFE = ...  # type: int
HTTPAUTH_AVAIL = ...  # type: int
HTTPAUTH_BASIC = ...  # type: int
HTTPAUTH_DIGEST = ...  # type: int
HTTPAUTH_GSSNEGOTIATE = ...  # type: int
HTTPAUTH_NONE = ...  # type: int
HTTPAUTH_NTLM = ...  # type: int
HTTPGET = ...  # type: int
HTTPHEADER = ...  # type: int
HTTPPOST = ...  # type: int
HTTPPROXYTUNNEL = ...  # type: int
HTTP_CODE = ...  # type: int
HTTP_CONNECTCODE = ...  # type: int
HTTP_CONTENT_DECODING = ...  # type: int
HTTP_TRANSFER_DECODING = ...  # type: int
HTTP_VERSION = ...  # type: int
IGNORE_CONTENT_LENGTH = ...  # type: int
INFILE = ...  # type: int
INFILESIZE = ...  # type: int
INFILESIZE_LARGE = ...  # type: int
INFOTYPE_DATA_IN = ...  # type: int
INFOTYPE_DATA_OUT = ...  # type: int
INFOTYPE_HEADER_IN = ...  # type: int
INFOTYPE_HEADER_OUT = ...  # type: int
INFOTYPE_SSL_DATA_IN = ...  # type: int
INFOTYPE_SSL_DATA_OUT = ...  # type: int
INFOTYPE_TEXT = ...  # type: int
INFO_COOKIELIST = ...  # type: int
INFO_FILETIME = ...  # type: int
INTERFACE = ...  # type: int
IOCMD_NOP = ...  # type: int
IOCMD_RESTARTREAD = ...  # type: int
IOCTLDATA = ...  # type: int
IOCTLFUNCTION = ...  # type: int
IOE_FAILRESTART = ...  # type: int
IOE_OK = ...  # type: int
IOE_UNKNOWNCMD = ...  # type: int
IPRESOLVE = ...  # type: int
IPRESOLVE_V4 = ...  # type: int
IPRESOLVE_V6 = ...  # type: int
IPRESOLVE_WHATEVER = ...  # type: int
ISSUERCERT = ...  # type: int
KRB4LEVEL = ...  # type: int
LASTSOCKET = ...  # type: int
LOCALPORT = ...  # type: int
LOCALPORTRANGE = ...  # type: int
LOCK_DATA_COOKIE = ...  # type: int
LOCK_DATA_DNS = ...  # type: int
LOW_SPEED_LIMIT = ...  # type: int
LOW_SPEED_TIME = ...  # type: int
MAXCONNECTS = ...  # type: int
MAXFILESIZE = ...  # type: int
MAXFILESIZE_LARGE = ...  # type: int
MAXREDIRS = ...  # type: int
MAX_RECV_SPEED_LARGE = ...  # type: int
MAX_SEND_SPEED_LARGE = ...  # type: int
NAMELOOKUP_TIME = ...  # type: int
NETRC = ...  # type: int
NETRC_FILE = ...  # type: int
NETRC_IGNORED = ...  # type: int
NETRC_OPTIONAL = ...  # type: int
NETRC_REQUIRED = ...  # type: int
NEW_DIRECTORY_PERMS = ...  # type: int
NEW_FILE_PERMS = ...  # type: int
NOBODY = ...  # type: int
NOPROGRESS = ...  # type: int
NOSIGNAL = ...  # type: int
NUM_CONNECTS = ...  # type: int
OPENSOCKETFUNCTION = ...  # type: int
OPT_FILETIME = ...  # type: int
OS_ERRNO = ...  # type: int
POLL_IN = ...  # type: int
POLL_INOUT = ...  # type: int
POLL_NONE = ...  # type: int
POLL_OUT = ...  # type: int
POLL_REMOVE = ...  # type: int
PORT = ...  # type: int
POST = ...  # type: int
POST301 = ...  # type: int
POSTFIELDS = ...  # type: int
POSTFIELDSIZE = ...  # type: int
POSTFIELDSIZE_LARGE = ...  # type: int
POSTQUOTE = ...  # type: int
PREQUOTE = ...  # type: int
PRETRANSFER_TIME = ...  # type: int
PRIMARY_IP = ...  # type: int
PROGRESSFUNCTION = ...  # type: int
PROXY = ...  # type: int
PROXYAUTH = ...  # type: int
PROXYAUTH_AVAIL = ...  # type: int
PROXYPORT = ...  # type: int
PROXYTYPE = ...  # type: int
PROXYTYPE_HTTP = ...  # type: int
PROXYTYPE_SOCKS4 = ...  # type: int
PROXYTYPE_SOCKS5 = ...  # type: int
PROXYUSERPWD = ...  # type: int
PROXY_TRANSFER_MODE = ...  # type: int
PUT = ...  # type: int
QUOTE = ...  # type: int
RANDOM_FILE = ...  # type: int
RANGE = ...  # type: int
READDATA = ...  # type: int
READFUNCTION = ...  # type: int
READFUNC_ABORT = ...  # type: int
REDIRECT_COUNT = ...  # type: int
REDIRECT_TIME = ...  # type: int
REDIRECT_URL = ...  # type: int
REFERER = ...  # type: int
REQUEST_SIZE = ...  # type: int
RESPONSE_CODE = ...  # type: int
RESUME_FROM = ...  # type: int
RESUME_FROM_LARGE = ...  # type: int
SHARE = ...  # type: int
SH_SHARE = ...  # type: int
SH_UNSHARE = ...  # type: int
SIZE_DOWNLOAD = ...  # type: int
SIZE_UPLOAD = ...  # type: int
SOCKET_TIMEOUT = ...  # type: int
SPEED_DOWNLOAD = ...  # type: int
SPEED_UPLOAD = ...  # type: int
SSH_AUTH_ANY = ...  # type: int
SSH_AUTH_DEFAULT = ...  # type: int
SSH_AUTH_HOST = ...  # type: int
SSH_AUTH_KEYBOARD = ...  # type: int
SSH_AUTH_NONE = ...  # type: int
SSH_AUTH_PASSWORD = ...  # type: int
SSH_AUTH_PUBLICKEY = ...  # type: int
SSH_AUTH_TYPES = ...  # type: int
SSH_HOST_PUBLIC_KEY_MD5 = ...  # type: int
SSH_PRIVATE_KEYFILE = ...  # type: int
SSH_PUBLIC_KEYFILE = ...  # type: int
SSLCERT = ...  # type: int
SSLCERTPASSWD = ...  # type: int
SSLCERTTYPE = ...  # type: int
SSLENGINE = ...  # type: int
SSLENGINE_DEFAULT = ...  # type: int
SSLKEY = ...  # type: int
SSLKEYPASSWD = ...  # type: int
SSLKEYTYPE = ...  # type: int
SSLVERSION = ...  # type: int
SSLVERSION_DEFAULT = ...  # type: int
SSLVERSION_SSLv2 = ...  # type: int
SSLVERSION_SSLv3 = ...  # type: int
SSLVERSION_TLSv1 = ...  # type: int
SSL_CIPHER_LIST = ...  # type: int
SSL_ENGINES = ...  # type: int
SSL_SESSIONID_CACHE = ...  # type: int
SSL_VERIFYHOST = ...  # type: int
SSL_VERIFYPEER = ...  # type: int
SSL_VERIFYRESULT = ...  # type: int
STARTTRANSFER_TIME = ...  # type: int
STDERR = ...  # type: int
TCP_NODELAY = ...  # type: int
TIMECONDITION = ...  # type: int
TIMECONDITION_IFMODSINCE = ...  # type: int
TIMECONDITION_IFUNMODSINCE = ...  # type: int
TIMECONDITION_LASTMOD = ...  # type: int
TIMECONDITION_NONE = ...  # type: int
TIMEOUT = ...  # type: int
TIMEOUT_MS = ...  # type: int
TIMEVALUE = ...  # type: int
TOTAL_TIME = ...  # type: int
TRANSFERTEXT = ...  # type: int
UNRESTRICTED_AUTH = ...  # type: int
UPLOAD = ...  # type: int
URL = ...  # type: int
USERAGENT = ...  # type: int
USERPWD = ...  # type: int
VERBOSE = ...  # type: int
WRITEDATA = ...  # type: int
WRITEFUNCTION = ...  # type: int
WRITEHEADER = ...  # type: int

E_ABORTED_BY_CALLBACK = ...  # type: int
E_BAD_CONTENT_ENCODING = ...  # type: int
E_BAD_DOWNLOAD_RESUME = ...  # type: int
E_BAD_FUNCTION_ARGUMENT = ...  # type: int
E_CALL_MULTI_PERFORM = ...  # type: int
E_CONV_FAILED = ...  # type: int
E_CONV_REQD = ...  # type: int
E_COULDNT_CONNECT = ...  # type: int
E_COULDNT_RESOLVE_HOST = ...  # type: int
E_COULDNT_RESOLVE_PROXY = ...  # type: int
E_FAILED_INIT = ...  # type: int
E_FILESIZE_EXCEEDED = ...  # type: int
E_FILE_COULDNT_READ_FILE = ...  # type: int
E_FTP_ACCESS_DENIED = ...  # type: int
E_FTP_CANT_GET_HOST = ...  # type: int
E_FTP_CANT_RECONNECT = ...  # type: int
E_FTP_COULDNT_GET_SIZE = ...  # type: int
E_FTP_COULDNT_RETR_FILE = ...  # type: int
E_FTP_COULDNT_SET_ASCII = ...  # type: int
E_FTP_COULDNT_SET_BINARY = ...  # type: int
E_FTP_COULDNT_STOR_FILE = ...  # type: int
E_FTP_COULDNT_USE_REST = ...  # type: int
E_FTP_PORT_FAILED = ...  # type: int
E_FTP_QUOTE_ERROR = ...  # type: int
E_FTP_SSL_FAILED = ...  # type: int
E_FTP_WEIRD_227_FORMAT = ...  # type: int
E_FTP_WEIRD_PASS_REPLY = ...  # type: int
E_FTP_WEIRD_PASV_REPLY = ...  # type: int
E_FTP_WEIRD_SERVER_REPLY = ...  # type: int
E_FTP_WEIRD_USER_REPLY = ...  # type: int
E_FTP_WRITE_ERROR = ...  # type: int
E_FUNCTION_NOT_FOUND = ...  # type: int
E_GOT_NOTHING = ...  # type: int
E_HTTP_POST_ERROR = ...  # type: int
E_HTTP_RANGE_ERROR = ...  # type: int
E_HTTP_RETURNED_ERROR = ...  # type: int
E_INTERFACE_FAILED = ...  # type: int
E_LDAP_CANNOT_BIND = ...  # type: int
E_LDAP_INVALID_URL = ...  # type: int
E_LDAP_SEARCH_FAILED = ...  # type: int
E_LIBRARY_NOT_FOUND = ...  # type: int
E_LOGIN_DENIED = ...  # type: int
E_MULTI_BAD_EASY_HANDLE = ...  # type: int
E_MULTI_BAD_HANDLE = ...  # type: int
E_MULTI_INTERNAL_ERROR = ...  # type: int
E_MULTI_OK = ...  # type: int
E_MULTI_OUT_OF_MEMORY = ...  # type: int
E_OK = ...  # type: int
E_OPERATION_TIMEOUTED = ...  # type: int
E_OUT_OF_MEMORY = ...  # type: int
E_PARTIAL_FILE = ...  # type: int
E_READ_ERROR = ...  # type: int
E_RECV_ERROR = ...  # type: int
E_REMOTE_FILE_NOT_FOUND = ...  # type: int
E_SEND_ERROR = ...  # type: int
E_SEND_FAIL_REWIND = ...  # type: int
E_SHARE_IN_USE = ...  # type: int
E_SSH = ...  # type: int
E_SSL_CACERT = ...  # type: int
E_SSL_CACERT_BADFILE = ...  # type: int
E_SSL_CERTPROBLEM = ...  # type: int
E_SSL_CIPHER = ...  # type: int
E_SSL_CONNECT_ERROR = ...  # type: int
E_SSL_ENGINE_INITFAILED = ...  # type: int
E_SSL_ENGINE_NOTFOUND = ...  # type: int
E_SSL_ENGINE_SETFAILED = ...  # type: int
E_SSL_PEER_CERTIFICATE = ...  # type: int
E_SSL_SHUTDOWN_FAILED = ...  # type: int
E_TELNET_OPTION_SYNTAX = ...  # type: int
E_TFTP_DISKFULL = ...  # type: int
E_TFTP_EXISTS = ...  # type: int
E_TFTP_ILLEGAL = ...  # type: int
E_TFTP_NOSUCHUSER = ...  # type: int
E_TFTP_NOTFOUND = ...  # type: int
E_TFTP_PERM = ...  # type: int
E_TFTP_UNKNOWNID = ...  # type: int
E_TOO_MANY_REDIRECTS = ...  # type: int
E_UNKNOWN_TELNET_OPTION = ...  # type: int
E_UNSUPPORTED_PROTOCOL = ...  # type: int
E_URL_MALFORMAT = ...  # type: int
E_WRITE_ERROR = ...  # type: int
