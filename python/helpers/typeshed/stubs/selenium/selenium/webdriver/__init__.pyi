from .android.webdriver import WebDriver as Android
from .blackberry.webdriver import WebDriver as BlackBerry
from .chrome.options import Options as ChromeOptions
from .chrome.webdriver import WebDriver as Chrome
from .common.action_chains import ActionChains as ActionChains
from .common.desired_capabilities import DesiredCapabilities as DesiredCapabilities
from .common.proxy import Proxy as Proxy
from .common.touch_actions import TouchActions as TouchActions
from .edge.webdriver import WebDriver as Edge
from .firefox.firefox_profile import FirefoxProfile as FirefoxProfile
from .firefox.options import Options as FirefoxOptions
from .firefox.webdriver import WebDriver as Firefox
from .ie.options import Options as IeOptions
from .ie.webdriver import WebDriver as Ie
from .opera.webdriver import WebDriver as Opera
from .phantomjs.webdriver import WebDriver as PhantomJS
from .remote.webdriver import WebDriver as Remote
from .safari.webdriver import WebDriver as Safari
from .webkitgtk.options import Options as WebKitGTKOptions
from .webkitgtk.webdriver import WebDriver as WebKitGTK

# We need an explicit __all__ because some of the above won't otherwise be exported.
# This could also be fixed using assignments
__all__ = [
    "Firefox",
    "FirefoxProfile",
    "FirefoxOptions",
    "Chrome",
    "ChromeOptions",
    "Ie",
    "IeOptions",
    "Edge",
    "Opera",
    "Safari",
    "BlackBerry",
    "PhantomJS",
    "Android",
    "WebKitGTK",
    "WebKitGTKOptions",
    "Remote",
    "DesiredCapabilities",
    "ActionChains",
    "TouchActions",
    "Proxy",
]
