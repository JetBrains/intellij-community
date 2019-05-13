personnes = [p for p in Personne.objects.filter(
    Q(administrateur__societe__notation_ok=True) | Q(dirigeant__societe__notation_ok=True))]
