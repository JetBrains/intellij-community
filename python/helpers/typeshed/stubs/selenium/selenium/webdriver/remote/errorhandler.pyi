from typing import Any

from selenium.common.exceptions import (
    ElementClickInterceptedException as ElementClickInterceptedException,
    ElementNotInteractableException as ElementNotInteractableException,
    ElementNotSelectableException as ElementNotSelectableException,
    ElementNotVisibleException as ElementNotVisibleException,
    ErrorInResponseException as ErrorInResponseException,
    ImeActivationFailedException as ImeActivationFailedException,
    ImeNotAvailableException as ImeNotAvailableException,
    InsecureCertificateException as InsecureCertificateException,
    InvalidArgumentException as InvalidArgumentException,
    InvalidCookieDomainException as InvalidCookieDomainException,
    InvalidCoordinatesException as InvalidCoordinatesException,
    InvalidElementStateException as InvalidElementStateException,
    InvalidSelectorException as InvalidSelectorException,
    InvalidSessionIdException as InvalidSessionIdException,
    JavascriptException as JavascriptException,
    MoveTargetOutOfBoundsException as MoveTargetOutOfBoundsException,
    NoAlertPresentException as NoAlertPresentException,
    NoSuchCookieException as NoSuchCookieException,
    NoSuchElementException as NoSuchElementException,
    NoSuchFrameException as NoSuchFrameException,
    NoSuchWindowException as NoSuchWindowException,
    ScreenshotException as ScreenshotException,
    SessionNotCreatedException as SessionNotCreatedException,
    StaleElementReferenceException as StaleElementReferenceException,
    TimeoutException as TimeoutException,
    UnableToSetCookieException as UnableToSetCookieException,
    UnexpectedAlertPresentException as UnexpectedAlertPresentException,
    UnknownMethodException as UnknownMethodException,
    WebDriverException as WebDriverException,
)

class ErrorCode:
    SUCCESS: int
    NO_SUCH_ELEMENT: Any
    NO_SUCH_FRAME: Any
    UNKNOWN_COMMAND: Any
    STALE_ELEMENT_REFERENCE: Any
    ELEMENT_NOT_VISIBLE: Any
    INVALID_ELEMENT_STATE: Any
    UNKNOWN_ERROR: Any
    ELEMENT_IS_NOT_SELECTABLE: Any
    JAVASCRIPT_ERROR: Any
    XPATH_LOOKUP_ERROR: Any
    TIMEOUT: Any
    NO_SUCH_WINDOW: Any
    INVALID_COOKIE_DOMAIN: Any
    UNABLE_TO_SET_COOKIE: Any
    UNEXPECTED_ALERT_OPEN: Any
    NO_ALERT_OPEN: Any
    SCRIPT_TIMEOUT: Any
    INVALID_ELEMENT_COORDINATES: Any
    IME_NOT_AVAILABLE: Any
    IME_ENGINE_ACTIVATION_FAILED: Any
    INVALID_SELECTOR: Any
    SESSION_NOT_CREATED: Any
    MOVE_TARGET_OUT_OF_BOUNDS: Any
    INVALID_XPATH_SELECTOR: Any
    INVALID_XPATH_SELECTOR_RETURN_TYPER: Any
    ELEMENT_NOT_INTERACTABLE: Any
    INSECURE_CERTIFICATE: Any
    INVALID_ARGUMENT: Any
    INVALID_COORDINATES: Any
    INVALID_SESSION_ID: Any
    NO_SUCH_COOKIE: Any
    UNABLE_TO_CAPTURE_SCREEN: Any
    ELEMENT_CLICK_INTERCEPTED: Any
    UNKNOWN_METHOD: Any
    METHOD_NOT_ALLOWED: Any

class ErrorHandler:
    def check_response(self, response) -> None: ...
