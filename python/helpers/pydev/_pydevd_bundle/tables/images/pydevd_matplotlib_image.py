#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
from _pydevd_bundle.tables.images.pydevd_image_loader import save_image_to_storage, DEFAULT_IMAGE_FORMAT


def create_image(figure):
    # type: (Union[matplotlib.figure.Figure | plotly.graph_objs._figure.Figure]) -> str
    try:
        try:
            import matplotlib.figure
        except ImportError:
            matplotlib = None

        try:
            from plotly.graph_objects import Figure as PlotlyFigure
        except ImportError:
            PlotlyFigure = None

        if matplotlib and isinstance(figure, matplotlib.figure.Figure):
            return save_image_to_storage(figure, format=DEFAULT_IMAGE_FORMAT, save_func=lambda buffer, fmt: figure.savefig(buffer, format=fmt))
        elif PlotlyFigure and isinstance(figure, PlotlyFigure):
            return save_image_to_storage(figure, format=DEFAULT_IMAGE_FORMAT, save_func=lambda buffer, fmt: buffer.write(figure.to_image(format=fmt)))
        else:
            return "Error: Unsupported figure type."
    except Exception as e:
        return "Error: {}".format(e)
