<template>
  <v-app id="app">
    <app-header />
    <router-view class="router_container" :key="$route.fullPath" style="padding-bottom: 100px"></router-view>
    <app-footer />
  </v-app>
</template>

<script>
  import AppHeader from '@/components/AppHeader';
  import AppFooter from '@/components/AppFooter';

  export default {
    name: 'Boilerplate',
    components: { AppHeader, AppFooter },
    created() {
        this.$store.dispatch('scheduleRefreshToken');
      }
    }
</script>
