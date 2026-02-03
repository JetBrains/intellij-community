from abc import ABCMeta, abstractmethod


class IUserService(metaclass=ABCMeta):
    @abstractmethod
    def get_user_by_id(self, *, user_id):
        pass

the_user_service = get_service(IUserService) # type: IUserService
user = the_user_service.get_user_by_id(<warning descr="Unexpected argument">123</warning><warning descr="Parameter 'user_id' unfilled">)</warning>