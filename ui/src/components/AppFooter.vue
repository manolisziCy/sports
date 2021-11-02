<template>
  <v-footer absolute padless app fluid class="footer-container footer-content">
    <v-col class="text-center" cols="12">
      <div class="footer-div">
        <div><strong>v{{ version }}</strong> <div style="display: inline-block" v-html="$t('footer_made_by')"></div></div>
      </div>
    </v-col>
  </v-footer>
</template>

<script>

export default {
  name: 'AppFooter',
  data() {
    return {
      version: process.env.APP_VERSION
    };
  }
};
</script>

<style scoped>
.footer-content {
  margin-top: 48px;
  padding: 15px;
  background: linear-gradient(315deg, #003399 .09%, #003399);
  text-align: center;
  display: inline-flex;
  width: 100%;
}

.footer-div {
  margin: 16px 0 0;
  color: #fff;
  flex-grow: 1;
  font-size: 0.85rem;
  text-align: center;
  line-height: 1.8rem;
}

a {
  padding: 0 5px;
}

.footer-container {
  margin: 0px;
  padding: 0px;
}

</style>
