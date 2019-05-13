current_report_group = status_reports.values('report_group_id').annotate(rcount=Count('report_group_id')).order_by(
    "-report_group_id")[:1]
