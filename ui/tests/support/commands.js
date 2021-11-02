// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add("login", (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add("drag", { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add("dismiss", { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This is will overwrite an existing command --
// Cypress.Commands.overwrite("visit", (originalFn, url, options) => { ... })

const backendServer = {
    host: Cypress.env('backend_host') || 'localhost',
    port: Cypress.env('backend_port') || '8080'
}
const backendServerBaseUrl = 'http://' + backendServer.host + ':' + backendServer.port;
const admin = {username: 'admin@cytech.gr', password: '123qwe', token: ''};

Cypress.Commands.add('stubSubmissionRequestResponse', (status, stubbedResponse, endpoint) => {
    cy.intercept("POST", backendServerBaseUrl + '/api/' + endpoint, {
        statusCode: status,
        body: stubbedResponse
    });
});

Cypress.Commands.add('stubSubmissionRequestResponsePut', (status, stubbedResponse, endpoint) => {
    cy.intercept("PUT", backendServerBaseUrl + '/api/' + endpoint, {
        statusCode: status,
        body: stubbedResponse
    });
});

Cypress.Commands.add('stubGetApiRequestResponse', (status, stubbedResponse, endpoint) => {
    cy.intercept("GET", backendServerBaseUrl + '/api/' + endpoint, {
        statusCode: status,
        body: stubbedResponse
    });
});

Cypress.Commands.add('stubgetFormByHashResponse', (status, stubbedResponse) => {
    cy.intercept('GET', backendServerBaseUrl + '/api/form/*', {
        statusCode: status,
        body: stubbedResponse
    });
});

Cypress.Commands.add('getVueFormDataObj', () => {
    return cy.window().then(win => {
        return win.document.getElementById('app').__vue__.$children[2];
    });
});

Cypress.Commands.add('getVueStore', () => {
    return cy.window().then(win => {
        return win.document.getElementById('app').__vue__.$store;
    });
});

Cypress.Commands.add('getVueObj', () => {
    return cy.window().then(win => {
        return win.document.getElementById('app').__vue__;
    });
});

Cypress.Commands.add('getVueUserModel', () => {
   return cy.getVueStore().then(store => {
       return store.state.user;
   });
});

Cypress.Commands.add('createNewUser', (user) => {
    cy.getAdminToken();
    return cy.apiRequest({method: 'POST', url: '/api/users', body: user}, true).then(resp => {
        return cy.retrieveStubEmail(resp.body.username).then(email => {
            const urlParts = email.split("href")[1].split("\"")[1].split("/");
            const token = urlParts[urlParts.length - 1];
            return cy.apiRequest({method: 'PUT', url: '/api/users/reset-password', body: user,
                headers: {Authorization: 'Bearer ' + token}});
        });
    });
});

Cypress.Commands.add('removeUser', (user) => {
    cy.getAdminToken();
    return cy.apiRequest({method: 'DELETE', url: `/api/users/${user.id}`}, true).then(resp => {
        user = resp.body;
        return user;
    });
});

Cypress.Commands.add('getAllUsers', () => {
    cy.getAdminToken();
    return cy.apiRequest({method: 'GET', url: '/api/users?lastId=0&limit=0&forward=true&sortBy=id&sortDesc=false'}, true).then(resp => {
        return resp.body;
    });
});

Cypress.Commands.add('removeUser', (user) => {
    cy.getAdminToken();
    return cy.apiRequest({method: 'DELETE', url: `/api/users/${user.id}`}, true).then(resp => {
        user = resp.body;
        return user;
    });
});

Cypress.Commands.add('uiLogin', (username, password) => {
    cy.visit('/user/login');
    //check if logged in, so as to logout first
    cy.getVueUserModel().then(u => {
        if (u && u.token) {
            cy.uiLogout();
        }
    });
    cy.get('#h1-login').should('exist');
    cy.get('#email').clear().type(username);
    cy.get('#password').clear().type(password);
    cy.get('#userMgtButton').click();
});

Cypress.Commands.add('uiLogout', () => {
    // find menu button and click it
    cy.get('#menu_button').click({force: true});
    cy.get('#navigationBtnLogout').click({force: true});
    cy.get('#h1-login').should('exist');
});

Cypress.Commands.add('setVueUser', (user) => {
    return cy.getVueStore().then(store => {
        if (user != null && user.authenticated === undefined) {
            user.authenticated = true;
        }
        store.commit('persistUser', user);
        return user;
    });
});

Cypress.Commands.add('apiLogin', (user) => {
    if (user.token != null && user.token != '') {
        return cy.wrap(user);
    }
    return cy.apiRequest({ method: 'POST', url: '/api/users/login', body: JSON.stringify(user) }).then(response => {
        const data = response.body;
        user.token = data.token;
        return user;
    });
});

Cypress.Commands.add('getAdminToken', () => {
   if (admin.token && admin.token != '') {
       return cy.wrap(admin.token);
   }
   return cy.apiLogin(admin).then(admin => {
      return admin.token;
   });
});

Cypress.Commands.add('apiRequest', (options, authorize, token) => {
    if (!options.url || !options.url.startsWith('http')) {
        options.url = `${backendServerBaseUrl}${options.url}`
    }
    if (!options.headers) options.headers = {};
    options.headers["Content-Type"] = 'application/json';
    options.headers["Accept"] = 'application/json';

    if (token) {
        options.headers["Authorization"] = `Bearer ${token}`;
    } else if (authorize && admin.token) {
        options.headers["Authorization"] = `Bearer ${admin.token}`;
    }

    return cy.request(options);
});

Cypress.Commands.add('retrieveStubEmail', (email, attempt) => {
    return cy.request({
        method: 'GET',
        url: 'http://localhost:9000/retrieve-stub-email?email=' + encodeURIComponent(email),
        headers: {'Accept': 'application/json'},
        failOnStatusCode: false
    }).then(response => {
        attempt = attempt !== undefined ? attempt + 1 : 1;
        if (response.status !== 200 && attempt < 10) {
            cy.wait(100);
            return cy.retrieveStubEmail(email, attempt);
        }
        const body = JSON.parse(response.body);
        return body.email;
    });
});

Cypress.Commands.add('requestStubDelete', (prms) => {
    prms = prms || {users: true, forms: true, schemas: true};
    return cy.request({ method: 'GET',
        url: `http://localhost:9000/delete?users=${prms.users === true}&forms=${prms.forms === true}&schemas=${prms.schemas === true}`,
        headers: {'Accept': 'application/json'},
        failOnStatusCode: false
    });
});

Cypress.Commands.add('clickVisibleOKBtn', () => {
    cy.clickByCssAndText('button.v-btn span.v-btn__content:visible', 'OK');
});

Cypress.Commands.add('autocompleteSelect', (selector, value) => {
    cy.get(selector).clear({force: true}).type(value);
    //pass selector to clickByCssAndText
    cy.clickByCssAndText('.menuable__content__active .v-list-item__content' , value);
});

Cypress.Commands.add('clickByCssAndText', (css, txt) => {
    cy.get(css).each(function(e) {
        if (e.text() == txt) {
            cy.get(e).click();
        }
    });
});

Cypress.Commands.add('getByCssAndText', (css, txt) => {
    return cy.get(css).each(function(e) {
        if (e.text() == txt) {
            return cy.get(e);
        }
    });
});

Cypress.Commands.add('vselect', (selector, value) => {
    cy.get(selector).click({force: true});
    cy.clickByCssAndText('.menuable__content__active .v-list-item__title', value);
});

Cypress.Commands.add('goToHome', () => {
    cy.visit('/');
    cy.contains('Πλατφόρμα Συλλογής Απογραφικών Δελτίων');
    // cy.get('.btnStartHere').should('exist');
});

Cypress.Commands.add('openEditWizard', () => {
    cy.get("#menu_button").click();
    cy.get("#navigationBtnHome").click();
    cy.get(".edit_form").first().click();
});

Cypress.Commands.add('goToAdminUsers', () => {
    cy.get("#menu_button").click();
    cy.get("#adminUsers").click();
    cy.get("#createNewUser").should('exist');
});

Cypress.Commands.add('openCreateWizard', () => {
    cy.get("#menu_button").click({force: true});
    cy.get("#navigationBtnNewCensusForm").click({force: true});
});

Cypress.Commands.add('selectRadioByValue', (selector, choice) => {
    cy.document().then((doc) => {

        const childElements = doc.getElementById(selector).childNodes;

        for(let i = 0; i < childElements.length; i++) {
            // surpass the legend
            if(childElements[i].tagName !== 'DIV')
                continue;

            const input = doc.getElementById(selector).querySelectorAll('input')[i - 1];

            // input value is the multiple choices
            if(choice === input.value) {
                // set id to the parent div so you can click it
                choice = choice.replace(" ", "_");
                childElements[i].id = choice;
                cy.get("#" + childElements[i].id).click();
            }
        }
    });
});


