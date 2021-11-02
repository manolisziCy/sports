<template>
  <div>
    <v-toolbar dark color="#003399" class="toolbar" extended extension-height="5">
      <v-container fluid class="grid-container">
        <v-row class="grid-row align-center pa-0">
          <v-spacer class="hidden-sm-and-down"></v-spacer>
          <v-col cols="4" class="hidden-sm-and-down">
            <v-img :src="images.logo" contain max-height="80"></v-img>
          </v-col>
          <v-col cols="auto" md="6" sm="8" xs="12" lg="5" class="pa-0">
            <a tabindex="0" role="button" aria-disabled="false" class="grid-title" href="/">
              <h3 class="hidden-sm-and-down">{{ $t('projectName') }}</h3>
              <h3 class="hidden-md-and-up">{{ $t('projectNameShort') }}</h3>
            </a>
          </v-col>
          <v-spacer></v-spacer>
          <v-col cols="auto" class="align-center pa-0">
            <div class="d-flex align-center">
              <v-btn id="menu_button" class="plf-menu-btn" dark icon v-if="isLoggedIn" @click.stop="drawer = !drawer">
                <v-icon>mdi-menu</v-icon>
              </v-btn>
              <div v-if="!isLoggedIn" style="margin-left: 10px" class="login-label">{{ $t('login')}}</div>
              <v-btn class="plf-menu-btn"  dark icon v-if="!isLoggedIn" @click.stop="$router.push('/user/login')">
                <v-icon>mdi-account</v-icon>
              </v-btn>
            </div>
          </v-col>
        </v-row>
      </v-container>
    </v-toolbar>
    <v-navigation-drawer v-model="drawer" v-if="isLoggedIn" temporary app right hide-overlay color="#F0F3F7">
      <v-toolbar color="#003399" dark class="toolbar" extended extension-height="5">
        <v-container justify="start" fluid>
          <v-row no-gutters align="center" class="grid-row">
            <v-list-item-title align="left" class="toolbarTitle">{{ username }}</v-list-item-title>
          </v-row>
        </v-container>
      </v-toolbar>
      <v-divider></v-divider>
      <v-list align="left" nav>
        <div v-for="item in authorizedMenu" :key="item.title">
          <v-list-item v-if="!item.subLinks" :to="item.path" :id="item.id">
            <v-list-item-icon>
              <v-icon>{{ item.icon }}</v-icon>
            </v-list-item-icon>
            <v-list-item-content>
              <v-list-item-title>{{ $t(item.title) }}</v-list-item-title>
            </v-list-item-content>
          </v-list-item>
          <v-list-group v-else :key="item.title" :prepend-icon="item.icon">
            <template v-slot:activator>
              <v-list-item-title>{{ $t(item.title) }}</v-list-item-title>
            </template>

            <v-list-item v-for="link in item.subLinks" :key="link.title" :to="link.path" :id="link.id">
              <v-list-item-icon><v-icon>{{ link.icon }}</v-icon></v-list-item-icon>
              <v-list-item-title>{{ $t(link.title) }}</v-list-item-title>
            </v-list-item>
          </v-list-group>
        </div>
      </v-list>
    </v-navigation-drawer>
    <alert-bar id="mainAlertBar"/>
  </div>
</template>

<script>
import {mapGetters} from 'vuex';
import AlertBar from "@/components/AlertBar";
import {deepCopy} from "@/helpers/utils";

export default {
  name: 'AppHeader',
  components: { AlertBar },
  data () {
    return {
      drawer: false,
      images: {
        logo: require('../assets/images/logo.png')
      }
    };
  },
  computed: {
    ...mapGetters(['isLoggedIn', 'username']),
    authorizedMenu() { return this.filterMenu(this.menu); },
    menu() {
      let menu = [
        { title: 'home', icon: 'mdi-account', id: 'navigationBtnHome', path: '/home' },
        { title: 'logout', icon: 'mdi-logout', id: 'navigationBtnLogout', path: '/user/logout' }
      ];
      return menu;
    }
  },
  watch: {
    $route (to, from) {
      this.drawer = from.path === '/user/login';
    },
  },
  methods: {
    navigate (item) {
      this.$router.push(item.path);
    },
    filterMenu(array) {
      return deepCopy(array).reduce(
          (menu, item) => {
            const newItem = item;
            if (item.subLinks) {
              // recursive call for subLinks
              newItem.subLinks = this.filterMenu(item.subLinks);
            }
            //authorize the new new menu item
            if(!newItem.authorize || newItem.authorize.includes(this.userRole)) {
              if (!newItem.permissions || newItem.permissions.some(p => this.userPermissions.includes(p))) {
                menu.push(newItem);
              }
            }
            return menu;
          },
          // initialize accumulator (empty array)
          []
      );
    }
  }
};
</script>

<style scoped>

.v-application .primary--text {
  color: #000000 !important;
  caret-color: white !important;
}

.header-image a {
  margin-left: -8px;
  align-items: center;
  display: flex;
}

a {
  align-items: center;
  display: flex;
  padding: 4px;
}

h3 {
  /*border-left: 1px solid #fff;*/
  padding-left: 16px;
  color: #fff;
  margin: 32px 0px 32px 0px;
  font-size: 1.5rem;
  margin-block-start: 1em;
  margin-block-end: 1em;
  margin-inline-start: 0px;
  margin-inline-end: 0px;
  font-weight: bold;
}

.toolbar {
  width: 100%;
  display: flex;
  box-sizing: border-box;
  flex-shrink: 0;
  flex-direction: column;
  color: #fff;
  position: static;
  transform: translateZ(0);
  min-height: 75px;
}

.v-toolbar >>> .v-toolbar__content {
  padding: 0px !important;
  min-height: 75px;
}

.grid-title {
  text-decoration: none;
}

.grid-image {
  padding-top: 10px;
  padding-right: 50px;
}

.v-toolbar >>> .v-toolbar__extension {
  padding: 2px;
  background-color: #FFCC00;
}

</style>

<style>

.v-application--wrap {
  padding-bottom: 40px !important;
}

#search_panel .v-text-field.v-text-field--enclosed .v-text-field__details {
  display: none;
}

.hint {
  text-align: left;
  padding-top: 10px;
  padding-bottom: 15px;
  font-size: 15px;
}

.row {
  margin: 0px;
}
.continue-btn.theme--light.v-btn.v-btn--disabled{
  color: white !important;
  background-color: #b0b0b0 !important;
}

.field-input {
  max-width: 810.66px;
  margin: 15px 0px 30px;
  border-radius: 8px;
  border-width: 1px;
  border-color: #003476;
  font-size: 1.1429rem;
}

.continue-btn, .default-btn {
  color: white !important;
}

@media screen and (min-width: 1280px) {
  .continue-btn, .default-btn {
    font-size: 17px !important;
  }
}

.field-input {
  margin: 15px 0px 30px !important;
}

.v-input--has-state.error--text .v-label {
  animation: none !important;
}

#homePageTitle, #homePageMainText {
  color: rgba(0,0,0,.87);
}

#app {
  font-family: "Roboto", "Helvetica", "Arial", "sans-serif";
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
}

body {
  color: rgba(0, 0, 0, 0.87);
  margin: 0;
  font-size: 1rem;
  font-family: "Roboto", "Helvetica", "Arial", sans-serif;
  font-weight: 400;
  line-height: 1.43;
  letter-spacing: 0.01071em;
  background-color: #fafafa;
  overflow-x: hidden;
}

@media screen and (max-width: 550px) {
  .router_container {
    padding-bottom: 250px !important;
  }
}

</style>
