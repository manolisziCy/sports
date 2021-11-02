const fs = require("fs");
const path = require("path");
const pdf = require('pdf-parse');

const downloadDirectory = path.join(__dirname, '..', 'downloads')

const { startDevServer } = require("@cypress/webpack-dev-server");
const webpackConfig = require("@vue/cli-service/webpack.config");

// https://docs.cypress.io/guides/guides/plugins-guide.html
module.exports = (on, config) => {
    on('dev-server:start', options =>
        startDevServer({
            options,
            webpackConfig
        })
    );

    on('before:browser:launch', (browser, options) => {
        if (browser.family === 'chromium' && browser.name !== 'electron') {
            options.preferences.default['download'] = { default_directory: downloadDirectory }
            return options
        } else if (browser.family === 'firefox') {
            options.preferences['browser.download.dir'] = downloadDirectory
            options.preferences['browser.download.folderList'] = 2

            // needed to prevent download prompt for text/csv files.
            options.preferences['browser.helperApps.neverAsk.saveToDisk'] = 'text/csv'
            return options
        }
    })

    on("task", {
        getPdfContent (pdfName) {
            const pdfPathname = path.join(downloadDirectory, pdfName)
            let dataBuffer = fs.readFileSync(pdfPathname);
            return pdf(dataBuffer);
        }
    });

    on("task", {
        getDownloadDirectory(){
            return downloadDirectory;
        }
    });

    return Object.assign({}, config, {
        fixturesFolder: 'tests/fixtures',
        integrationFolder: 'tests/specs/e2e',
        screenshotsFolder: 'tests/screenshots',
        videosFolder: 'tests/videos',
        supportFile: 'tests/support/index.js',
        //todo fix: ./node_modules/@vue/cli-plugin-e2e-cypress/index.js -> https://github.com/cypress-io/cypress/issues/2518
        baseUrl: "http://localhost:7000/#",
        "videoUploadOnPasses": false
    })
};
