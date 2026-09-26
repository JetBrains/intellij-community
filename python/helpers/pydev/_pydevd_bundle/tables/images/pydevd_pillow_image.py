#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import PIL
from _pydevd_bundle.tables.images.pydevd_image_loader import save_image_to_storage, DEFAULT_IMAGE_FORMAT

def create_image(pillow_image):
    # type: (PIL.Image.Image) -> str
    image_format = pillow_image.format if pillow_image.format else DEFAULT_IMAGE_FORMAT
    return save_image_to_storage(pillow_image, format=image_format)
