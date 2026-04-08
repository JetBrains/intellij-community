from django.db.migrations.serializer import BaseSerializer
from typing_extensions import override

class RangeSerializer(BaseSerializer):
    @override
    def serialize(self) -> tuple[str, set[str]]: ...
