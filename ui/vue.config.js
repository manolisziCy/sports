const fs = require('fs')
const packageJson = fs.readFileSync('./package.json')
const meta = JSON.parse(packageJson);
const version = meta.version;
const vueVersion = meta.dependencies.vue;
const vuetifyVersion = meta.dependencies.vuetify;
const libPhoneNumberVersion = meta.dependencies['libphonenumber-js'];
const htmlToPdfVersion = meta.dependencies['html2pdf.js'];
const jsToPdfVersion = meta.dependencies['jspdf'];
const htmlToCanvasVersion = meta.dependencies['html2canvas'];

module.exports = {
    configureWebpack: config => {
        //externalize big vendor libraries to reduce the bundle size.
        //these libraries are included via cdn by index.html
        config.externals = {
            vue: 'Vue',
            'vuetify/lib': 'Vuetify',
            'libphonenumber-js/max' : 'libphonenumber',
            'html2pdf.js' : 'html2pdf'
        }
        config.optimization = {
            splitChunks: false
        }
    },
    chainWebpack: config => {
        const now = Date.now();
        config.output.filename(`js/[name].${now}.js`);
        config.plugin('html')
            .tap(args => {
                return args
            });

        config.plugin("define")
            .tap(args => {
                let _base = args[0]["process.env"];
                args[0]["process.env"] = {
                    ..._base,
                    "APP_VERSION": '"' + version + '"',
                    "VUE_VERSION": '"' + vueVersion + '"',
                    "VUETIFY_VERSION": '"' + vuetifyVersion + '"',
                    "LIBPHONENUMBER_VERSION": '"' + libPhoneNumberVersion + '"',
                    "HTMLTOPDF_VERSION": '"' + htmlToPdfVersion + '"',
                    "HTMLTOCANVAS_VERSION": '"' + htmlToCanvasVersion + '"',
                    "JSTOPDF_VERSION": '"' + jsToPdfVersion + '"',
                };
                return args;
            });

        config.module
            .rule('svg')
            .test(/\.(svg)(\?.*)?$/)
            .use('file-loader')
            .loader(require.resolve('file-loader'))
            .options({ name: `img/[name].${now}.[ext]` })

        config.module
            .rule('images')
            .test(/\.(png|jpe?g|gif|webp)(\?.*)?$/)
            .use('url-loader')
            .loader(require.resolve('url-loader'))
            .options({ name: `img/[name].${now}.[ext]` })
    },

    "devServer": {
        port: 7000,
    },

    css: {
        extract: false,
    },

    productionSourceMap: false
}