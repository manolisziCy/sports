<template>
    <div>
      <component @alertEvent="notifyAlertEventListeners" v-if="alert.display" :is="messageTemplate"></component>
    </div>
</template>

<script>
    import { EventBus, AlertType } from '@/helpers/events';
    export default {
        name: "AlertBar",
        props: {
          id: String,
        },
        data() {
            return {
                alert: {
                    type: AlertType.INFO,
                    message: '',
                    display: false,
                    class: ''
                }
            }
        },
        created(){
            EventBus.$on('show', this.show);
            EventBus.$on('hide', this.hide);
        },
        beforeDestroy(){
            EventBus.$off('show', this.show);
            EventBus.$off('hide', this.hide);
        },
        computed: {
          messageTemplate () {
            return {
              template: `<div class="${this.alert.class}">${this.$i18n.t(this.alert.message)}</div>`
            }
          }
        },
        methods:{
            show(type, message, bar){
              if(bar && bar !== this.id){
                return;
              }
              this.alert.type = type;
              this.alert.message = message;
              this.alert.display = true;
              this.alert.class = this.alert.type === AlertType.SUCCESS ? "alert alert-success" :
              this.alert.type === AlertType.ERROR ? "alert  alert-error" :
                        "alert  alert-info";
            },
            hide(){
                this.alert.message = '';
                this.alert.display = false;
            },
            notifyAlertEventListeners(event, args){
              EventBus.$emit(event, args);
            }
        }
    }
</script>

<style scoped>
    .alert {
        font-weight: 500;
        margin-top: 15px;
        padding: 20px 30px;
        border-radius: .25rem;
        font-size: 1rem;
        text-align: left;
        max-width: 1200px;
        margin-left: auto;
        margin-right: auto;
        border-left: 8px solid;
        background-color: #e0e0e0;
    }
    .alert-success {
        border-left-color: #2D7512;
    }

    .alert-error {
        border-left-color: #ca2e2e;
    }

    .alert-info {
      border-left-color: #003476;
    }

    @media (max-width: 1280px) {
      .alert {
        width: 85%;
      }
    }
</style>