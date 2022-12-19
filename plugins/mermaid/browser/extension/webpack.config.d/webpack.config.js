const webpack = require('webpack');

if (!config.plugins) {
    config.plugins = [];
}

config.plugins.push(new webpack.optimize.LimitChunkCountPlugin({
    maxChunks: 1
}));
