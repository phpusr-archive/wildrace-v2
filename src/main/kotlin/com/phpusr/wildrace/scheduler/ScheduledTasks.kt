package com.phpusr.wildrace.scheduler

import com.phpusr.wildrace.service.SyncService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledTasks(private val syncService: SyncService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 1000 * 60 * 5)
    fun syncPostsJob() {
        logger.info("--- Sync posts job start ---")
        syncService.syncPosts()
        logger.info("--- Sync posts job end ---")
    }
}