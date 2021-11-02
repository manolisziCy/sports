import Vue from 'vue';

export const EventBus = new Vue();

export const AlertType = {
    SUCCESS: 'success',
    INFO: 'info',
    ERROR: 'error'
};

class Alert {
    info(message, bar) {
        this.show(AlertType.INFO, message, bar);
    }

    success(message, bar) {
        this.show(AlertType.SUCCESS, message, bar);
    }

    error(message, bar) {
        this.show(AlertType.ERROR, message, bar);
    }

    show(type, message, bar){
        EventBus.$emit('show', type, message, bar);
    }

    hide(){
        EventBus.$emit('hide');
    }
}

const alert = new Alert();
Vue.prototype.$alert = alert;
Vue.$alert = alert;