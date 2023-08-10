from _typeshed import Incomplete

from pyVmomi.vim import ManagedEntity

def __getattr__(name: str) -> Incomplete: ...

class ContainerView:
    def Destroy(self) -> None: ...

class ViewManager:
    # Doc says the `type` parameter of CreateContainerView is a `list[str]`,
    # but in practice it seems to be `list[Type[ManagedEntity]]`
    # Source: https://pubs.vmware.com/vi-sdk/visdk250/ReferenceGuide/vim.view.ViewManager.html
    @staticmethod
    def CreateContainerView(container: ManagedEntity, type: list[type[ManagedEntity]], recursive: bool) -> ContainerView: ...
