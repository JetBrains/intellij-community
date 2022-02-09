/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

import { Disposable, commands } from 'vscode';
import { Model } from '../model';
import { pickRemoteSource } from '../remoteSource';
import { GitBaseExtensionImpl } from './extension';
import { API, PickRemoteSourceOptions, PickRemoteSourceResult, RemoteSourceProvider } from './git-base';

export class ApiImpl implements API {

	constructor(private _model: Model) { }

	pickRemoteSource(options: PickRemoteSourceOptions): Promise<PickRemoteSourceResult | string | undefined> {
		return pickRemoteSource(this._model, options as any);
	}

	registerRemoteSourceProvider(provider: RemoteSourceProvider): Disposable {
		return this._model.registerRemoteSourceProvider(provider);
	}
}

export function registerAPICommands(extension: GitBaseExtensionImpl): Disposable {
	const disposables: Disposable[] = [];

	disposables.push(commands.registerCommand('git-base.api.getRemoteSources', (opts?: PickRemoteSourceOptions) => {
		if (!extension.model) {
			return;
		}

		return pickRemoteSource(extension.model, opts as any);
	}));

	return Disposable.from(...disposables);
}
