import Vue from 'vue'
import VueRouter from 'vue-router'
import Store from "@/store"
import AccountManagement from "@/views/AccountManagement";
import Home from "@/views/Home";

Vue.use(VueRouter)

const router = new VueRouter({
  mode: 'hash',
  base: process.env.BASE_URL,
  routes: [
    {path: '/user/:action', name: 'AccountManagement', component: AccountManagement},
    {path: '/user/:action/:jwt', name: 'AccountManagementWithJwt', component: AccountManagement},
    {path: '/home', name: 'Home', component: Home, meta: {authRequired: false}},
    {path :'*', redirect: '/user/logout'},
  ]
});

router.beforeEach((to, from, next) => {
  Vue.$alert.hide();

  if (to.matched.some(record => record.meta.authRequired)) {
    //check user is authenticated or whether the token has been expired
    if (!Store.getters.isLoggedIn) {
      return next({ path: '/user/logout' });
    }
  }

  // check if route is restricted by role
  if (to.meta.authorize && to.meta.authorize.length > 0 && Store.getters.userRole !== 'admin' && !to.meta.authorize.includes(Store.getters.userRole)) {
    //todo: go here to a forbidden access page or display an error message
    //notification.error("accessDenied");
    return next({ path: '/user/login' });
  }

  next();
});

export default router;
