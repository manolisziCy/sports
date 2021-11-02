<template>
  <v-main style="margin-bottom: 30px">
    <div class="container-root container-max-width">
      <v-container fluid grid-list-md text-xs-center>
        <v-layout row wrap justify-start>
          <v-flex xs12 md9 style="justify-content: flex-start">
            <v-btn text class="back-btn" @click="goBack">
              <svg class="back-btn-icon" focusable="false" viewBox="0 0 24 24" aria-hidden="true" fill="white">
                <path d="M14 7l-5 5 5 5V7z"></path>
              </svg>
              <span class="back-btn-text">{{ $t('back') }}</span>
            </v-btn>
            <v-form ref="form" v-model="valid" lazy-validation>
              <h1 v-if="isAction(this.ACTION.SEND_VERIFY_EMAIL)" style="display: flex" id="h1-sendVerificationEmail">
                {{ $t('sendVerificationEmail') }}</h1>
              <h1 v-else-if="isAction(this.ACTION.RESET_PASSWORD_EMAIL)" style="display: flex"
                  id="h1-resetPasswordEmail">{{ $t('resetPassword') }}</h1>
              <h1 v-else-if="isAction(this.ACTION.REGISTER)" style="display: flex" id="h1-register">{{
                  $t('register')
                }}</h1>
              <h1 v-else-if="isAction(this.ACTION.RESET_PASSWORD)" style="display: flex" id="h1-resetPassword">{{
                  $t('resetPassword')
                }}</h1>
              <h1 v-else-if="isAction(this.ACTION.SET_PASSWORD)" style="display: flex" id="h1-setPassword">{{
                  $t('setPassword')
                }}</h1>
              <h1 v-else style="display: flex" id="h1-login">{{ $t('login') }}</h1>

                <v-text-field
                    v-if="isAction([this.ACTION.LOGIN, this.ACTION.REGISTER, this.ACTION.RESET_PASSWORD_EMAIL, this.ACTION.SEND_VERIFY_EMAIL])"
                    outlined id="email" type="text" class="field-input" color="#003399"
                    :label="$t('email')" required :rules="emailRules" v-model="email"></v-text-field>

                <v-text-field
                    v-if="isAction([this.ACTION.LOGIN, this.ACTION.REGISTER, this.ACTION.SEND_VERIFY_EMAIL, this.ACTION.RESET_PASSWORD, this.ACTION.SET_PASSWORD])"
                    outlined id="password" type="password" class="field-input" color="#003399"
                    style="margin-bottom: 3px !important;"
                    :label="$t('password')" required :hint="$t('password_desc')" :rules="passwordRules"
                    v-model="password"></v-text-field>

                <v-text-field v-if="isAction([this.ACTION.RESET_PASSWORD, this.ACTION.REGISTER, this.ACTION.SET_PASSWORD])"
                              :label="$t('confirmPassword')" outlined id="confirm_password" type="password"
                              class="field-input" color="#003399" style="margin-bottom: 3px !important;"
                              required :rules="passwordRules.concat(passwordConfirmationRule)"
                              v-model="confirmPassword"></v-text-field>

              </v-form>

            <div v-if="isAction(this.ACTION.LOGIN)" style="max-width: 810px; text-align: right;">
              <router-link to="/user/register" id="registerLinkBtn">{{ $t("register") }}</router-link>
              <span class="separator">|</span>
              <router-link to="/user/reset-password-email" id="resetPasswordLinkBtn">{{$t("resetPassword")}}</router-link>
            </div>

          </v-flex>
          <v-flex xs12 md3><!--right-side blank space--></v-flex>

          <div style="display: flex; margin-top: 20px">
            <v-btn text class="continue-btn" id="userMgtButton"
                   :disabled="!valid || loading" @click.stop="doAction">{{ $t('submit') }}
            </v-btn>
          </div>
        </v-layout>
      </v-container>
    </div>
  </v-main>
</template>

<script>
import {EventBus} from "@/helpers/events";
import {jwtDecode} from "../helpers/utils";

export default {
  name: 'AccountManagement',
  mixins: [],
  data () {
    return {
      valid: false,
      loading: false,
      constants: {
        ROLE_SET_PASSWORD: "SetPassword"
      },
      action: '',
      email: '',
      password: '',
      confirmPassword: '',
      jwt: '',
      emailRules: [
        v => !!v || '',
        v => /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])*(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])*)+$/.test(
            v) || '',
      ],
      passwordRules: [
        v => !!v || '',
        v => v.length >= 6 || '',
      ],
      ACTION: {
        LOGIN: "login",
        REGISTER: "register",
        VERIFY_EMAIL: "verify-email",
        SEND_VERIFY_EMAIL: "send-verify-email",
        RESET_PASSWORD_EMAIL: "reset-password-email",
        RESET_PASSWORD: "reset-password",
        SET_PASSWORD: "set-password",
        LOGOUT: "logout",
      },
    };
  },
  mounted () {
    this.parseAction(this.$route);
  },
  watch: {
    $route (to, from) {
      this.$refs.form.resetValidation();
      this.parseAction(to);
    },
  },
  computed: {
    passwordConfirmationRule () {
      return () =>
          this.password === this.confirmPassword || "";
    },
  },
  methods: {
    parseAction (route) {
      this.action = route.params.action;

      this.jwt = route.params.jwt;

      if (this.action === this.ACTION.VERIFY_EMAIL) {
        if (this.jwt) {
          this.$store.dispatch('verifyEmail', this.jwt);
        }
        this.action = this.ACTION.LOGIN;
      } else if (this.action === this.ACTION.LOGOUT) {
        this.$store.dispatch('logout');
      } else if (this.action === this.ACTION.RESET_PASSWORD) {
        this.$store.dispatch('checkTokenExpiration', this.jwt);
        const jwtObj = jwtDecode(this.jwt);
        const role = jwtObj.groups[0];
        if(role === this.constants.ROLE_SET_PASSWORD){
          this.action = this.ACTION.SET_PASSWORD;
        }
      }
    },
    async doAction () {
      if (this.$refs.form.validate()) {
        this.loading = true;
        try {
          if (this.action === this.ACTION.LOGIN) {
            await this.$store.dispatch('login',
                { username: this.email, password: this.password, lang: this.$i18n.locale, status: null, id: null });
          } else if (this.action === this.ACTION.REGISTER) {
            await this.$store.dispatch('register',
                { username: this.email, password: this.password, lang: this.$i18n.locale });
          } else if (this.action === this.ACTION.SEND_VERIFY_EMAIL) {
            await this.$store.dispatch('sendVerificationEmail',
                { username: this.email, password: this.password, lang: this.$i18n.locale });
          } else if (this.action === this.ACTION.RESET_PASSWORD_EMAIL) {
            await this.$store.dispatch('sendResetPasswordEmail', { username: this.email, lang: this.$i18n.locale });
          } else if (this.action === this.ACTION.RESET_PASSWORD || this.action === this.ACTION.SET_PASSWORD ) {
            await this.$store.dispatch('resetPassword',
                { jwt: this.jwt, password: this.password, lang: this.$i18n.locale });
          }
        } finally {
          this.loading = false;
        }
      }
    },
    isAction (action) {
      if (Array.isArray(action)) {
        return action.includes(this.action);
      }
      return this.action === action;
    },
    goBack () {
      if (this.action === this.ACTION.LOGIN) {
        this.$router.push('/')
      } else {
        this.$router.push('/user/login')
      }
    }
  },
};
</script>

<style scoped>
.container-root {
  width: 100%;
  display: block;
  box-sizing: border-box;
  margin-left: auto;
  margin-right: auto;
  padding-left: 16px;
  padding-right: 16px;
}

@media screen and (max-width: 597px) {
  h1 {
    font-size: 36px;
  }

  h6 {
    font-size: 19.42px;
  }

  .back-btn {
    width: 100%;
  }
}

@media screen and (min-width: 597px) and (max-width: 1280px) {
  h1 {
    font-size: 36px;
  }

  h6 {
    font-size: 19.42px;
  }

  .continue-btn {
    width: 118px;
  }
}

@media screen and (min-width: 1280px) {
  .container-max-width {
    max-width: 1280px;
  }

  h1 {
    font-size: 48px;
  }

  h6 {
    font-size: 1.4286rem;
  }

  .continue-btn {
    width: 200px;
    font-size: 14px;
    font-weight: 500;
  }

}

v-flex {
  justify-content: flex-start;
}

h1 {
  font-weight: bold;
  margin-bottom: 32px;
  text-align: left;
}

h6 {
  font-weight: normal;
  color: #4d4d4d;
  padding: 8px 0px 0px;
  text-align: left;
}

.error-quote {
  border-left: 8px solid;
  background-color: #e0e0e0;
  border-left-color: #ca2e2e;
}

.field-input {
  max-width: 810.66px;
  margin: 15px 0px 30px;
  border-radius: 8px;
  border-width: 1px;
  border-color: #003399;
  font-size: 1.1429rem;
}

.back-btn-icon {
  width: 22px;
  height: 22px;
  margin-left: -4px;
  margin-right: 8px;
  margin-top: 2px;
}

.back-btn-text {
  font-weight: 500;
  font-size: 17px;
  color: white;
}

.back-btn {
  padding: 8px 11px;
  border-bottom: 1px solid;
  text-transform: none;
  display: flex;
  background-color: #003399;
  border-radius: 4px;
}

.continue-btn {
  padding: 8px 22px;
  height: 45px !important;
  border-bottom: 1px solid;
  text-transform: none;
  display: flex;
  background-color: #003399;
  color: white;
  width: 160px;
}

.continue-btn:disabled {
  color: white !important;
  opacity: 0.8;
}

select {
  font-size: 1.1429rem;
  font-color: #869299;
  max-width: 810.66px;
  max-height: 58.71px;
  width: inherit !important;
  outline: none;
  margin: 0px 0px 0px;
  padding-left: 12px;
  border-radius: 8px;
  border-width: 1px;
  border-color: #9d9d9d;
  border-style: solid;
}

select:focus {
  border: solid;
  border-color: #003399;
  border-width: 2px;
}

select {
  font-size: 1.1429rem;
  font-color: #869299;
  width: 810.66px;
  height: 58.71px;
  outline: none;
  margin: 0px 0px 0px;
  padding-left: 12px;
  border-radius: 8px;
  border-width: 1px;
  border-color: #9d9d9d;
  border-style: solid;
}

::v-deep .select-input .v-messages__wrapper {
  margin-bottom: 8px;
  padding: 0 12px;
}

::v-deep .no-arrows input::-webkit-outer-spin-button,
::v-deep .no-arrows input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

/*radio style*/
div.v-item--active {
  border: 2px solid #003399;
  background-color: #eeeeee;
  width: 100%;
  margin: 4px 0px;
  padding: 4px;
}

div.v-radio {
  margin: 4px 0px;
  margin-bottom: 0px !important;
  padding: 4px 15px;
  max-width: 810px;
  max-height: 57.43px;
}

.v-radio >>> label {
  color: black;

  font-weight: 500;
  line-height: 1.5em;

  font-family: "Roboto", "Helvetica", "Arial", sans-serif;
  font-size: 18.2864px;

  margin: 4px 0px;
  padding: 4px;
}

.v-radio >>> i {
  transform: scale(1.1);
}

div.radio-group {
  width: 100%;
}

div.v-input--radio-group {
  width: 100%;
  margin-top: 0px;
}

div.error-div .show-error-msg {
  display: flex !important;
}

::v-deep .v-text-field__details {
  display: none !important;
}

::v-deep .v-messages.error--text {
  display: none;
}

a {
  color: rgb(0, 0, 238);
  box-sizing: border-box;
  cursor: pointer;
  -webkit-text-decoration-color: rgb(0, 0, 238);
  text-decoration-color: rgb(0, 0, 238);
  -webkit-text-decoration-line: underline;
  text-decoration-line: underline;
  -webkit-text-decoration-style: solid;
  text-decoration-style: solid;
  font-size: 18px;
  text-decoration: none;
}

.separator {
  padding-left: 8px;
  padding-right: 8px;
  font-size: 19px;
}

</style>

