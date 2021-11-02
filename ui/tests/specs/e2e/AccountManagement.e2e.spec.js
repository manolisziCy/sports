/// <reference types="cypress" />
const user = {username: "devs+test+" + new Date().getTime() + "@cytech.gr", password: "123qwe"};
describe('User register', () => {
    it('Visits registration page', () => {
        //make sure local storage is clear
        //localStorage.clear();
        cy.visit('/user/login');

        //we should arrive in login/register page
        cy.get('#h1-login').should('exist');

        cy.get('#registerLinkBtn').click();
        cy.get('#h1-register').should('exist');
    });

    it ('Register user', () => {
        cy.get('#email').type(user.username);
        cy.get('#password').type(user.password);
        cy.get('#confirm_password').type(user.password);
        cy.get('#userMgtButton').click();

        //make sure registration succeeded
        cy.get('.alert.alert-success').should('exist');
    });

    it ('Check throttling error on resend email verification', () => {
        cy.visit('/user/send-verify-email');

        cy.get('#email').type(user.username);
        cy.get('#password').type(user.password);
        cy.get('#userMgtButton').click();

        //make sure on re-submission the user gets back a throttling error
        cy.get('.alert.alert-error').contains('1 minute');
    });

    it ('Verifies registered email address', () => {
        //the backend should have generated a verification email. we need to get it to find the verification jwt
        cy.retrieveStubEmail(user.username).then(email => {
            //we have the full email body. now we need to extract the verification url from it (it would be inside an a tag)
            const verificationUrl = email.split("href")[1].split("\"")[1];
            //we have the url, now let's visit it
            cy.visit(verificationUrl);
            //make sure verification succeeded
            cy.get('.alert.alert-success').should('exist');
        });
    });

    it ('Logs in using the new account', () => {
        login(user.username, user.password);
    });

    it ('Logs out', () => {
        logout();
    });
});

describe('User forgot password', () => {
    it ('Requests a password reset', () => {
        cy.get('#resetPasswordLinkBtn').click();
        cy.get('#h1-resetPasswordEmail').should('exist');
        cy.get('#email').type(user.username);

        cy.get('#userMgtButton').click();
        cy.get('.alert.alert-success').should('exist')
    });

    it ('Check throttling error on reset password', () => {
        cy.get('#userMgtButton').click();
        cy.get('.alert.alert-error').contains('recently');
    });

    it ('Resets the password using the reset password url from the email', () => {
        //the backend should have generated a password-reset email. we need to get it to find the jwt
        cy.retrieveStubEmail(user.username).then(email => {
            //we have the full email body. now we need to extract the url from it (it would be inside an a tag)
            const url = email.split("href")[1].split("\"")[1];
            //we have the url, now let's visit it
            cy.visit(url);

            //make sure we're in the password reset page
            cy.get('#h1-resetPassword').should('exist');
            user.password = user.password + "!";
            cy.get('#password').type(user.password);
            cy.get('#confirm_password').type(user.password);
            cy.get('#userMgtButton').click();

            //make sure everything was a success
            cy.get('.alert.alert-success').should('exist')
        });
    });

    it ('Logs in using the new password', () => {
        login(user.username, user.password);
    });

    it ('Logs out', () => {
        logout();
    });
});

function login(username, password) {
    cy.get('#h1-login').should('exist');
    cy.get('#email').type(username);
    cy.get('#password').type(password);
    cy.get('#userMgtButton').click();
}

function logout() {
    // find menu button and click it
    cy.get('#navigationBtnLogout').click();
    cy.get('#h1-login').should('exist');
}