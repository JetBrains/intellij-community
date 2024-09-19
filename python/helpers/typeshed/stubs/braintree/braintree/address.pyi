from _typeshed import Incomplete
from typing import Final

from braintree.resource import Resource

class Address(Resource):
    class ShippingMethod:
        SameDay: Final = "same_day"
        NextDay: Final = "next_day"
        Priority: Final = "priority"
        Ground: Final = "ground"
        Electronic: Final = "electronic"
        ShipToStore: Final = "ship_to_store"
        PickupInStore: Final = "pickup_in_store"

    @staticmethod
    def create(params: Incomplete | None = None): ...
    @staticmethod
    def delete(customer_id, address_id): ...
    @staticmethod
    def find(customer_id, address_id): ...
    @staticmethod
    def update(customer_id, address_id, params: Incomplete | None = None): ...
    @staticmethod
    def create_signature(): ...
    @staticmethod
    def update_signature(): ...
