const webpack = require('webpack');
const TerserPlugin = require("terser-webpack-plugin");

if (!config.plugins) {
    config.plugins = [];
}

config.plugins.push(new webpack.optimize.LimitChunkCountPlugin({
    maxChunks: 1
}));

if (!config.optimization) {
  config.optimization = {
      minimizer: []
  };
}

config.optimization.minimize = true;
config.optimization.minimizer.push(new TerserPlugin({
    terserOptions: {
        keep_classnames: true,
        keep_fnames: true
    }
}));
