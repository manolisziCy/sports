import Vue from 'vue'
import App from '@/App'
import {i18n} from '@/plugins/i18n';
import vuetify from '@/plugins/vuetify';
import router from '@/router';
import store from '@/store'

String.prototype.capitalize = function() {
  return this.charAt(0).toUpperCase() + this.slice(1);
}

Vue.config.productionTip = process.env.NODE_ENV != null && process.env.NODE_ENV.startsWith('prod');

new Vue({
  i18n,
  vuetify,
  router,
  store,
  render: h => h(App)
}).$mount('#app');

