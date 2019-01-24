package com.phpusr.wildrace.scheduler

import com.phpusr.wildrace.service.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class ScheduledTasks(
        private val syncService: SyncService,
        private val configService: ConfigService,
        private val statService: StatService,
        private val restService: RestService,
        private val environmentService: EnvironmentService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @Scheduled(fixedRate = 5 * 60 * 1000) // Run every 5 min
    fun syncPostsJob() {
        logger.info("--- Sync posts job start ---")

        if (configService.get().syncPosts.not()) {
            logger.info(">> Sync posts job is disabled")
            return
        }

        syncService.syncPosts()
        logger.info("--- Sync posts job end ---")
    }

    @Transactional
    @Scheduled(fixedRate = 60 * 60 * 1000) // Run every 1 hour
    fun statPublishJob() {
        logger.info("--- Stat publish job start ---")

        if (configService.get().publishStat.not()) {
            logger.info(">> Publish job is disabled")
            return
        }

        statService.publishStatPost(1000)

        logger.info("--- Stat publish job end ---")
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    fun herokuDontStopJob() {
        if (environmentService.isProduction.not()) {
            return
        }

        logger.info("--- Heroku don't stop job start ---")
        val totalPosts = restService.get("https://wildrace.herokuapp.com/post")["totalElements"] as Int
        logger.info("--- Heroku don't stop job end (total posts: $totalPosts) ---")
    }
}
