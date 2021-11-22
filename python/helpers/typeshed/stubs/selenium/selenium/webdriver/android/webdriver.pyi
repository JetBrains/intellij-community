from selenium.webdriver.common.desired_capabilities import DesiredCapabilities as DesiredCapabilities
from selenium.webdriver.remote.webdriver import WebDriver as RemoteWebDriver

class WebDriver(RemoteWebDriver):
    def __init__(self, host: str = ..., port: int = ..., desired_capabilities=...) -> None: ...
