#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import matplotlib
from _pydevd_bundle.tables.images.pydevd_image_loader import save_image_to_storage, DEFAULT_IMAGE_FORMAT

def create_image(matplotlib_figure):
    # type: (matplotlib.figure.Figure) -> str
    return save_image_to_storage(matplotlib_figure, format=DEFAULT_IMAGE_FORMAT, save_func=lambda buffer, fmt: matplotlib_figure.savefig(buffer, format=fmt))
