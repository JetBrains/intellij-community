from qrcode import image as image
from qrcode.constants import (
    ERROR_CORRECT_H as ERROR_CORRECT_H,
    ERROR_CORRECT_L as ERROR_CORRECT_L,
    ERROR_CORRECT_M as ERROR_CORRECT_M,
    ERROR_CORRECT_Q as ERROR_CORRECT_Q,
)
from qrcode.main import make as make

def run_example(data: str = "http://www.lincolnloop.com", *args, **kwargs) -> None: ...
