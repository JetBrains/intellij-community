def get_queryset(self):
    return (
        super().get_queryset()
        .filter(user=self.request.user)
        .prefetch_related(
            'lines__oscar_line',
            'lines__oscar_line__product'
        )
    )
