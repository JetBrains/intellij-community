from influxdb_client.client.influxdb_client import InfluxDBClient
from influxdb_client.domain.organization import Organization

def get_org_query_param(org: Organization | str | None, client: InfluxDBClient, required_id: bool = False) -> str: ...
