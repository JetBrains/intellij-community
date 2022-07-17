from typing import Any

from ..exceptions.exceptions import MissingPluginNames as MissingPluginNames

module_prefix: str
PLUGIN_MAPPING: Any

def get_plugin_modules(plugins): ...
