package com.phpusr.wildrace.service

import com.phpusr.wildrace.domain.Config
import com.phpusr.wildrace.domain.ConfigRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class ConfigService(private val configRepo: ConfigRepo) {
    private var config = configRepo.get()

    @Synchronized
    fun get() = config

    @Transactional
    @Synchronized
    fun update(value: Config) {
        configRepo.save(value)
        config = value
    }
}