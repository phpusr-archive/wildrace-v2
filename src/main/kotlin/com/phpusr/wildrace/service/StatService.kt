package com.phpusr.wildrace.service

import com.phpusr.wildrace.domain.*
import com.phpusr.wildrace.dto.EventType
import com.phpusr.wildrace.dto.RunnerDto
import com.phpusr.wildrace.dto.StatDto
import com.phpusr.wildrace.enum.StatType
import com.phpusr.wildrace.util.Util
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.function.BiConsumer
import kotlin.NoSuchElementException

@Service
class StatService(
        private val postRepo: PostRepo,
        private val tempDataRepo: TempDataRepo,
        private val statSender: BiConsumer<EventType, Map<String, Any>>,
        private val lastSyncDateSender: BiConsumer<EventType, Long>,
        private val statLogRepo: StatLogRepo,
        private val configService: ConfigService,
        private val vkApiService: VKApiService
) {

    fun calcStat(statType: StatType?, startRange: String?, endRange: String?): StatDto {
        val stat = StatDto()

        if (statType == StatType.Date) {
            val df = SimpleDateFormat("yyyy-MM-dd")
            try {
                stat.startDate = df.parse(startRange)
            } catch (ignored: ParseException) {}
            try {
                // Change time at end of day
                stat.endDate = Date(df.parse(endRange).time + (24 * 3600 * 1000 - 1))
            } catch (ignored: ParseException) {}
        } else if (statType == StatType.Distance) {
            stat.startDistance = startRange?.toIntOrNull()
            stat.endDistance = endRange?.toIntOrNull()
        }

        val firstRunning = getOneRunning(Sort.Direction.ASC)
        val lastRunning = getOneRunning(Sort.Direction.DESC)

        if (firstRunning == null || lastRunning == null) {
            throw NoSuchElementException("not_found_posts")
        }

        val firstIntRunning = getOneRunning(Sort.Direction.ASC, stat)
        if (stat.startDate == null) {
            stat.startDate = firstIntRunning?.date
        }
        val lastIntRunning = getOneRunning(Sort.Direction.DESC, stat)
        if (stat.endDate == null) {
            stat.endDate = lastIntRunning?.date
        }

        val runners = getRunners()
        stat.topAllRunners = getTopRunners(runners)
        val intRunners = getRunners(firstIntRunning, lastIntRunning)
        stat.topIntervalRunners = getTopRunners(intRunners)

        stat.daysCountAll = getCountDays(firstRunning.date, lastRunning.date)
        stat.daysCountInterval = getCountDays(stat.startDate, stat.endDate)

        stat.distanceAll = lastRunning.sumDistance ?: -1
        stat.distanceMaxOneMan = runners.first()

        stat.runnersCountAll = runners.size
        stat.runnersCountInterval = intRunners.size
        setNewRunners(stat, intRunners, stat.startDate)

        stat.trainingCountAll = lastRunning.number ?: -1
        stat.trainingMaxOneMan = runners.sortedBy { it.numberOfRuns * -1 }.first()

        return stat
    }

    fun getOneRunning(direction: Sort.Direction, stat: StatDto? = null): Post? {
        val sort = Sort(direction, "date")
        val pageable = PageRequest.of(0, 1, sort)

        return postRepo.findRunningPage(pageable, stat?.startDate, stat?.endDate, stat?.startDistance, stat?.endDistance).firstOrNull()
    }

    private fun getRunners(firstIntRunning: Post? = null, lastIntRunning: Post? = null): List<RunnerDto> {
        return postRepo.calcSumDistanceForRunners(firstIntRunning?.date, lastIntRunning?.date).map{
            val el = it as Array<*>
            RunnerDto(el[0] as Profile, el[1] as Long, el[2] as Long)
        }
    }

    private fun getTopRunners(runners: List<RunnerDto>): List<RunnerDto> {
        if (runners.size > 5) {
            return runners.subList(0, 5)
        }

        return runners
    }

    private fun setNewRunners(stat: StatDto, runners: List<RunnerDto>, startDate: Date?) {
        if (startDate == null) {
            stat.newRunners = listOf()
            return
        }

        val newRunners = runners.filter {
            it.profile.joinDate >= startDate
        }.map{ it.profile }.sortedBy { it.joinDate }

        stat.countNewRunners = newRunners.size

        val max = 25
        if (newRunners.size > max) {
            stat.newRunners = newRunners.subList(0, max)
            return
        }

        stat.newRunners = newRunners
    }

    private fun getCountDays(startDate: Date?, endDate: Date?): Int {
        if (startDate == null || endDate == null) {
            return 0
        }

        return Duration.of(endDate.time - startDate.time, ChronoUnit.MILLIS).toDays().toInt() + 1
    }

    fun getStat(): Map<String, Any> {
        val lastPost = getOneRunning(Sort.Direction.DESC)

        return mapOf(
                "sumDistance" to (lastPost?.sumDistance ?: 0),
                "numberOfRuns" to (lastPost?.number ?: 0),
                "numberOfPosts" to postRepo.count()
        )
    }

    fun updateStat() {
        val tempData = tempDataRepo.save(tempDataRepo.get().copy(lastSyncDate = Date()))
        lastSyncDateSender.accept(EventType.Update, tempData.lastSyncDate.time)
        statSender.accept(EventType.Update, getStat())
    }

    fun publishStatPost(stat: StatDto) {
        val response = vkApiService.wallPost(createPostText(stat))
        val postId = (response["response"] as Map<*, *>)["post_id"] as String?
        if (postId != null) {
            statLogRepo.save(stat.createStatLog(postId.toLong()))
        }
    }

    private fun createPostText(stat: StatDto): String {
        //TODO добавить вывод города
        val s = with(stat) {
            val newRunnersString = if (newRunners.isNotEmpty()) {
                newRunners.map { it.vkLinkForPost }.joinToString(", ")
            } else "В этот раз без новичков"
            val segment = if (startDistance != null && endDistance != null) {
                "$startDistance-$endDistance"
            } else {
                val dfPost = SimpleDateFormat()
                "(${dfPost.format(stat.startDate)}-${dfPost.format(endDate)})"
            }

            val str = StringBuilder()
            str.append("СТАТИСТИКА\n")
            if (endDistance != null) {
                str.append("Отметка в $endDistance км преодолена!\n")
            }
            if (newRunners.isNotEmpty()) {
                str.append("Поприветствуем наших новичков:\n")
            }
            str.append("$newRunnersString${if (newRunners.size < countNewRunners) "..." else "."}\n")

            str.append("\n\nНаши итоги в цифрах:\n")
            str.append("1. Количество дней бега:\n")
            str.append("- Всего - $daysCountAll дн.\n")
            str.append("- Отрезок $segment - $daysCountInterval дн.\n")
            str.append("2. Километраж:\n")
            str.append("- Средний в день - ${Util.floatRoundToString(distancePerDayAvg, 1)} км/д\n")
            str.append("- Средняя длина одной пробежки - ${Util.floatRoundToString(distancePerTrainingAvg,1)} км/тр\n")
            str.append("3. Тренировки:\n")
            str.append("- Всего - $trainingCountAll тр.\n")
            str.append("- Среднее в день - ${Util.floatRoundToString(trainingCountPerDayAvg, 1)} тр.\n")
            str.append("- Максимум от одного человека - ${trainingMaxOneMan.numberOfRuns} тр. (${trainingMaxOneMan.profile.vkLinkForPost})\n")
            str.append("4. Бегуны:\n")
            str.append("- Всего отметилось - $runnersCountAll чел.\n")
            str.append("- Отметилось на отрезке $segment - $runnersCountInterval чел.\n")
            str.append("- Новых на отрезке ${segment} - ${newRunners.size} чел.\n")
            str.append("5. Топ 5 бегунов на отрезке:\n")
            str.append(topIntervalRunners.map { "- ${it.profile.vkLinkForPost} (${it.sumDistance} км)" }.joinToString("\n"))
            str.append("\n")
            str.append("6. Топ 5 бегунов за все время:\n")
            str.append(topAllRunners.map { "- ${it.profile.vkLinkForPost} (${it.sumDistance} км)" }.joinToString("\n"))
            str.append("\n")

            // Добавление ссылки на предыдущий пост со статистикой
            val lastLog = statLogRepo.findFirstOrderByPublishDateDesc()
            if (lastLog != null) {
                str.append("\nПредыдущий пост со статистикой: ${lastLog.getVKLink(configService.get())}\n")
            }
            //TODO ссылка на источник статистики

            str.append("\nВсем отличного бега!\n")

            str.append("\n#ДикийЗабегСтатистика\n")
        }

        return s.toString()
    }

}