num_synapses = nest.GetDefaults("excitatory")["num_connections"] + nest.GetDefaults("inhibitory")["num_connections"]
