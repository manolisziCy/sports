import Vue from 'vue';
import Vuex from 'vuex';
import axios from 'axios';
import router from '@/router';
import {jwtDecode, parseFilters} from '@/helpers/utils';

Vue.use(Vuex);
let apiUrl = process.env.VUE_APP_API_URL;
try {
  if (apiUrl.includes('localhost') && !window.location.href.includes('localhost')) {
    apiUrl = './api';
  }
} catch(e) {
  console.error("error auto-configuring api url", e);
}
axios.defaults.baseURL = apiUrl;
axios.defaults.headers.common['Content-Type'] = 'application/json';
axios.defaults.headers.common['Accept'] = 'application/json';

//Add a request interceptor
axios.interceptors.request.use(function (config) {
  // Add the authorization header in all api requests
  if(Store.getters.userToken){
    config.headers.common['Authorization'] = 'Bearer ' + Store.getters.userToken;
  }
  return config;
}, function (error) {
  console.error('error in request', error);
  return Promise.reject(error);
});

//Add a response interceptor
axios.interceptors.response.use(function (response) {
  if(!response.config.url.startsWith('/logo') && !response.config.url.startsWith('/translations') && !response.config.url.startsWith('/settings')
      && !response.config.url.startsWith('/entrypoints')) {
    Vue.$alert.hide();
  }
  return response;
}, function (error) {
  if(!error.config.url.startsWith('/translations') && !error.config.url.startsWith('/settings')
      && !error.config.url.startsWith('/entrypoints')) {
    Vue.$alert.hide();
  }
  if (error.response && 401 === error.response.status) {
    router.push('/user/logout');
  }
  return Promise.reject(error);
});

const STORAGE_KEY_USER = "boilerplate_user";
const STORAGE_KEY_USERS = "boilerplate_users";

function loadUserFromLocalStorage() {
  const json = localStorage.getItem(STORAGE_KEY_USER);
  if (json != null) {
    try {
      const usr = JSON.parse(json);
      usr.authenticated = usr.authenticated && !hasTokenExpired(usr.tokenExpirationTime);
      if (usr.authenticated) {
        return usr;
      }
    } catch (e) {
      console.error("error loading user from json", json, e);
    }
  }

  return {authenticated: false, username: '', token: '', tokenExpirationTime: 0, id:''};
}

function loadUsersFromLocalStorage() {
  const json = localStorage.getItem(STORAGE_KEY_USERS);
  if (json != null) {
    try {
      return JSON.parse(json);
    } catch (e) {
      console.error("error loading users from json", json, e);
    }
  }

  return {users: [], expirationTime: 0};
}

function hasTokenExpired(tokenExpiration) {
  return !tokenExpiration || (tokenExpiration < (Date.now() / 1000) - 60);
}

const Store = new Vuex.Store({
  state: {
    user: loadUserFromLocalStorage(),
    users: loadUsersFromLocalStorage(),
  },
  mutations: {
    persistUser(state, user) {
      state.user.authenticated = user != null && user.authenticated;
      if (user == null) {
        user = {};
      }
      state.user.username = user.username;
      state.user.token = user.token;
      state.user.tokenExpirationTime = user.tokenExpirationTime;
      state.user.id = user.id;

      if (state.user.authenticated) {
        localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(state.user));
      } else {
        localStorage.removeItem(STORAGE_KEY_USER);
      }
    },
    updateUser(state,user){
      state.user.id = user.id;
      state.user.amka = user.amka;
      state.user.username = user.username;
      localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(state.user));
    },
    persistUsers(state, users) {
      const empty = users == null;
      state.users.users = empty ? [] : users;
      state.users.expirationTime = empty ? 0 : Date.now() + Number.parseInt(process.env.VUE_APP_CACHE_EXPIRATION_MILLIS);

      if (!empty) {
        localStorage.setItem(STORAGE_KEY_USERS, JSON.stringify(state.users));
      } else {
        localStorage.removeItem(STORAGE_KEY_USERS);
      }
    },
  },
  actions: {
    async login(context, user) {
      Vue.$alert.info("pleaseWait");
      try {
        const resp = await axios.post('/users/login', user);
        const userResponse = resp.data;
        const username = userResponse.username;
        const id = userResponse.id;
        const token = userResponse.token;
        if (token) {
          const jwtObj = jwtDecode(token);
          const tokenExpirationTime = jwtObj.exp;
          if (hasTokenExpired(tokenExpirationTime)) {
            Vue.$alert.error("loginError");
            return;
          }
          context.commit('persistUser', {authenticated: true, username: username, token: token, tokenExpirationTime: tokenExpirationTime,id:id});
          router.push('/home');
        } else {
          Vue.$alert.error("loginError");
        }
      } catch (e) {
        console.error("error logging in", e);
        if (e.response && e.response.data && e.response.data.error) {
          if(e.response.data.error === 3) {
            Vue.$alert.error("pendingAccountError");
          }
        } else {
          Vue.$alert.error("loginError");
        }
      }
    },
    async register(context, user) {
      Vue.$alert.info("pleaseWait");
      try {
        const resp = await axios.post('/users', user);
        const userResponse = resp.data;
        console.debug("received response for registering", userResponse);
        context.commit('persistUser', null);

        await router.push('/user/login');
        Vue.$alert.success("registerSuccess");
      } catch(error) {
        console.error("error registering", error);
        Vue.$alert.error("registerError");
      }
    },
    async verifyEmail(context, jwt) {
      Vue.$alert.info("pleaseWait");

      const jwtObj = jwtDecode(jwt);
      if(!jwtObj || hasTokenExpired(jwtObj.exp)) {
        Vue.$alert.error("expiredEmailVerificationUrlError");
        return Promise.reject("expired");
      }

      try {
        const response = await axios.put('/users/verify-email', {}, {headers: {Authorization: 'Bearer ' + jwt}});
        const userResponse = response.data;
        console.debug("received response for verifying email", userResponse);
        context.commit('persistUser', null);

        await router.push('/user/login');
        Vue.$alert.success("verifyEmailSuccess");
      } catch(error) {
        console.error("error verifying email", error);
        await router.push('/user/send-verify-email');
        if (error.response && error.response.data && error.response.data.error) {
          if(error.response.data.error === 3) {
            Vue.$alert.error("activeAccountError");
          }
        } else {
          Vue.$alert.error("verifyEmailError");
        }
      }
    },
    async sendVerificationEmail(context, user) {
      Vue.$alert.info("pleaseWait");
      try {
        const response = await axios.put('/users/resend-verification-email', user);
        const userResponse = response.data;
        context.commit('persistUser', null);

        await router.push('/user/login');
        Vue.$alert.success("resendVerificationEmailSuccess");
      } catch(error) {
        console.error(error);
        if (error && error.error) {
          if(error.error === 3) {
            Vue.$alert.error("activeAccountError");
          }
        } else {
          Vue.$alert.error("resendVerificationEmailError");
        }
      }
    },
    async sendResetPasswordEmail(context, user) {
      Vue.$alert.info("pleaseWait");
      try {
        const response = await axios.post('/users/reset-password', user);
        const userResponse = response.data;
        console.debug("received response for sending reset password email", userResponse);
        context.commit('persistUser', null);
        Vue.$alert.success("resetPasswordEmailSuccess");
      } catch(error) {
        console.error(error);
        Vue.$alert.error("resetPasswordEmailError");
      }
    },
    checkTokenExpiration(context, jwt){
      // taken the username from the jwt and check if it is expired
      const jwtObj = jwtDecode(jwt);
      if(!jwtObj || hasTokenExpired(jwtObj.exp)) {
        Vue.$alert.error("expiredResetPasswordUrlError");
        return Promise.reject("expired");
      }
    },
    async resetPassword(context, {jwt, password, lang}) {
      Vue.$alert.info("pleaseWait");

      // taken the username from the jwt and check if it is expired
      const jwtObj = jwtDecode(jwt);
      if(!jwtObj || hasTokenExpired(jwtObj.exp)) {
        Vue.$alert.error("expiredResetPasswordUrlError");
        return Promise.reject("expired");
      }
      try {
        const response = await axios.put('/users/reset-password', {username: jwtObj.upn, password: password, lang: lang}, {headers: {'Authorization': 'Bearer ' + jwt}});
        const userResponse = response.data;
        this.username = userResponse.username;
        this.token = userResponse.token;

        await router.push('/user/login');
        Vue.$alert.success("resetPasswordSuccess");
      } catch(error) {
        console.error(error);
        Vue.$alert.error("resetPasswordError");
      }
    },
    logout(context) {
      context.commit('persistUser', null);
      context.commit('persistUsers', null);
      router.push('/user/login');
    },
    async scheduleRefreshToken(context) {
      //schedule the 'refresh' method every minute
      const timerId = setInterval(() => context.dispatch('refreshToken'), 60_000);
      //also trigger an immediate refresh
      await context.dispatch('refreshToken');
    },
    async refreshToken(context) {
      if (!context.getters.isLoggedIn) {
        return;
      }

      //check if there is any need to refresh
      const exp = context.state.user.tokenExpirationTime;
      const expiresAtMs = (exp * 1000) - Date.now();
      if (expiresAtMs > 600000) {
        //valid for a long time, leave it as is
        return;
      }

      try {
        const response = await axios.post('/users/refresh');
        const existing = {...context.state.user};
        const jwtObj = jwtDecode(response.token);
        existing.tokenExpirationTime = jwtObj.exp;
        existing.token = response.token
        context.commit('persistUser', existing);
      } catch (e) {
        console.error("error refreshing token", e);
      }
    },

    // Users Actions
    async getUsers(context, req) {
      if (context.state.users.expirationTime > Date.now()) {
        return context.state.users.users;
      }

      try {
        let filterQuery = parseFilters(req.filters);
        let endPoint = `/users?lastId=${req.lastId}&limit=${req.limit}&forward=${req.forward}&sortDesc=${req.sortDesc}${filterQuery}`;
        const response = await axios.get(endPoint);

        const users = response.data;
        return !users ? [] : users;

      } catch (e) {
        console.error("error getting all users", e);
        Vue.$alert.error("errorLoadingAllUsers");
      }
    },

    async getUser(context, user) {
      try {
        const response = await axios.get('/users/' + user.id);
        return response.data;
      } catch (e) {
        console.error("error getting user", e);
        Vue.$alert.error("errorLoadingUsersProfileData");
      }
    },

    async createUser(context, user) {
      return await axios.post('/users', user);
    },

    async updateUser(context, user) {
      return await axios.put('/users/' + user.id, user);
    },

    async deleteUser(context, user) {
      return await axios.delete('/users/' + user.id);
    },


  },
  getters: {
    user(state) {
      return state.user ? state.user : null;
    },
    userToken(state) {
      return state.user.authenticated ? state.user.token : null;
    },
    isLoggedIn(state) {
      return state.user.authenticated && !hasTokenExpired(state.user.tokenExpirationTime);
    },
    username(state) {
      return state.user.authenticated ? state.user.username : null;
    }
  }
});

export default Store;
