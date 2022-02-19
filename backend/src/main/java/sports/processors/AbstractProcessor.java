package sports.processors;

import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sports.config.ConfigurationHandler;
import sports.config.ReconfigureEvent;
import sports.daos.UserDao;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractProcessor {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected AtomicInteger executorVersion;
    protected volatile boolean pause;
    protected int threads;
    protected long sleepMs;
    protected ThreadPoolExecutor executor;
    protected RateLimiter rateLimiter;

    @Inject protected Instance<UserDao> udao;
    @Inject protected Instance<ConfigurationHandler> ich;

    public abstract String getPauseConfigKey();

    public abstract String getThreadsConfigKey();

    public abstract String getSleepMsConfigKey();

    public abstract String getTpsConfigKey();

    public abstract boolean process();

    public void onStart(@Observes StartupEvent ev) {
        logger.debug("observed initialization");
    }

    @PostConstruct
    public void init() {
        logger.info("initializing");
        executorVersion = new AtomicInteger();
        reconfigureExecutor();
    }

    @PreDestroy
    public void stop() {
        logger.info("stopping");
        executorVersion.incrementAndGet();
        executor.shutdown();
    }

    public void reconfigure(@Observes ReconfigureEvent reconfigureEvent) {
        boolean newPause = isPause(ich.get());
        if (this.pause != newPause) {
            logger.info("received reconfigure event, changing pause from:{} to:{}", this.pause, newPause);
        }

        this.monitorConfiguration();
    }

    @Scheduled(every = "30s")
    public void monitorConfiguration() {
        var config = ich.get();
        this.pause = isPause(config);
        int configThreads = getThreads(config);
        this.sleepMs = getSleepMs(config);
        configRateLimiter(config);
        if (this.threads != configThreads) {
            reconfigureExecutor();
        }
    }

    protected void reconfigureExecutor() {
        final int currV = executorVersion.incrementAndGet();
        var config = ich.get();
        threads = getThreads(config);
        pause = isPause(config);
        configRateLimiter(config);
        ThreadPoolExecutor old = executor;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads,
                new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").setDaemon(false).build());
        for (int i = 0 ; i < threads; i++) {
            executor.submit(() -> this.checkProcess(currV));
        }
        if (old != null) {
            old.shutdown();
        }
    }

    protected void checkProcess(int runningVersion) {
        while (runningVersion >= executorVersion.get()) {
            if (pause) {
                logger.debug("paused, sleeping");
                sleep();
                continue;
            }
            var arc = Arc.container();
            ManagedContext mc = null;
            var processed = false;
            try {
                mc = arc.requestContext();
                mc.activate();
                processed = process();
            } catch (Exception e) {
                logger.warn("error during batch processing", e);
            } finally {
                try {
                    if (mc != null) {
                        mc.terminate();
                    }
                } catch (Exception e) {
                    logger.warn("error terminating custom request context", e);
                }
            }
            if (!processed) {
                sleep();
            }
        }
    }

    public void acquireTpsPermit(int num) {
        if (num > 0) {
            rateLimiter.acquire(num);
        }
    }

    protected void configRateLimiter(ConfigurationHandler config) {
        double tps = getTps(config);
        if (this.rateLimiter == null) {
            this.rateLimiter = RateLimiter.create(tps);
        } else if (this.rateLimiter.getRate() != tps) {
            this.rateLimiter.setRate(tps);
        }
    }

    public boolean isPause(ConfigurationHandler config) {
        return config.get(getPauseConfigKey(), Boolean.class, false);
    }

    public int getThreads(ConfigurationHandler config) {
        return config.get(getThreadsConfigKey(), Integer.class, 1);
    }

    public long getSleepMs(ConfigurationHandler config) {
        return config.get(getSleepMsConfigKey(), Long.class, 1000L);
    }

    public double getTps(ConfigurationHandler config) {
        return config.get(getTpsConfigKey(), Double.class, 100.0);
    }

    protected void sleep() {
        if (sleepMs <= 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(sleepMs);
        } catch (Exception e) {
            logger.warn("exception while sleeping", e);
        }
    }
}
