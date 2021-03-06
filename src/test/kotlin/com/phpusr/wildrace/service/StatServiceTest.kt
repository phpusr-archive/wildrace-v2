package com.phpusr.wildrace.service

import com.phpusr.wildrace.consts.Consts
import com.phpusr.wildrace.enum.StatType
import org.hamcrest.Matchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit4.SpringRunner
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@TestPropertySource("/application-test.properties")
@Sql(value = ["/create-data-before.sql"])
@RunWith(SpringRunner::class)
@SpringBootTest
internal class StatServiceTest {

    @Autowired
    private lateinit var statService: StatService

    private val startDate = createDate(2015, 9, 1, 3, 56, 9)
    private val endDate = createDate(2015, 9, 24, 10, 52, 34)

    @get:Rule
    val collector = ErrorCollector()

    private fun createDate(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, utc: Boolean = true): Date {
        val zoneId = if (utc) ZoneId.of("UTC") else ZoneId.systemDefault()
        return Date.from(ZonedDateTime.of(year, month, day, hour, minute, second, 0, zoneId).toInstant())
    }

    private fun parseDateToLong(dateString: String): Long {
        return SimpleDateFormat(Consts.JSDateFormat).parse(dateString).time
    }

    @Test
    fun calcStatWithoutParamsTest() {
        val stat = statService.calcStat(StatType.Date, null, null)
        collector.checkThat(stat.startDistance, nullValue())
        collector.checkThat(stat.endDistance, nullValue())

        // Dates
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(24))

        // Distance
        collector.checkThat(stat.distanceAll, equalTo(1003L))
        collector.checkThat(stat.distancePerDayAvg.toDouble(), closeTo(41.792, 0.001))
        collector.checkThat(stat.distancePerTrainingAvg.toDouble(), closeTo(6.965, 0.001))
        collector.checkThat(stat.distanceMaxOneMan.profile.id, equalTo(63399502L))
        collector.checkThat(stat.distanceMaxOneMan.numberOfRuns, equalTo(12))
        collector.checkThat(stat.distanceMaxOneMan.sumDistance, equalTo(98))

        // Trainings
        collector.checkThat(stat.trainingCountAll, equalTo(144))
        collector.checkThat(stat.trainingCountPerDayAvg.toDouble(), closeTo(6.0, 0.001))
        collector.checkThat(stat.trainingMaxOneMan.profile.id, equalTo(63399502L))
        collector.checkThat(stat.trainingMaxOneMan.numberOfRuns, equalTo(12))
        collector.checkThat(stat.trainingMaxOneMan.sumDistance, equalTo(98))

        // Runners
        collector.checkThat(stat.runnersCountAll, equalTo(35))
        collector.checkThat(stat.runnersCountInterval, equalTo(35))
        collector.checkThat(stat.newRunners, iterableWithSize(25))
        val newRunnersIdsActual = stat.newRunners.joinToString(", ") { it.id.toString() }
        val newRunnersIdsExpect = "39752943, 8429458, 84566235, 82121484, 18273238, 2437792, 11351451, 63399502, 117963335, 258765420, 282180599, 10811344, 1553750, 52649788, 151575104, 6465387, 53388774, 139202307, 67799362, 5488260, 18530540, 122104881, 179202944, 24237989, 293509406"
        collector.checkThat(newRunnersIdsActual, equalTo(newRunnersIdsExpect))
        collector.checkThat(stat.countNewRunners, equalTo(35))

        collector.checkThat(stat.topAllRunners, iterableWithSize(5))
        val topAllRunnersActually = stat.topAllRunners.joinToString(", ") { "(${it.profile.id},${it.numberOfRuns},${it.sumDistance})" }
        val topAllRunnersExpect = "(63399502,12,98), (12274403,4,85), (5488260,3,74), (82121484,11,71), (2437792,7,54)"
        collector.checkThat(topAllRunnersActually, equalTo(topAllRunnersExpect))

        collector.checkThat(stat.topIntervalRunners, iterableWithSize(5))
        val topIntervalRunnersActually = stat.topIntervalRunners.joinToString(", ") { "(${it.profile.id},${it.numberOfRuns},${it.sumDistance})" }
        collector.checkThat(topIntervalRunnersActually, equalTo(topAllRunnersExpect))

        collector.checkThat(stat.type, equalTo(StatType.Date))
    }

    @Test(expected = NoSuchElementException::class)
    fun calcStatWithSameParamsTest() {
        statService.calcStat(StatType.Distance, 1, 1)
    }

    @Test(expected = NoSuchElementException::class)
    fun calcStatWithNothingFoundTest() {
        statService.calcStat(StatType.Date, null, parseDateToLong("2015-01-01"))
    }

    @Test(expected = NoSuchElementException::class)
    fun calcStatWithNothingFound2Test() {
        statService.calcStat(StatType.Date, parseDateToLong("2015-10-01"), null)
    }

    @Test(expected = NoSuchElementException::class)
    fun calcStatWithNothingFound3Test() {
        statService.calcStat(StatType.Date, parseDateToLong("2015-10-01"), parseDateToLong("2015-01-01"))
    }

    @Test(expected = NoSuchElementException::class)
    fun calcStatWithNothingFound4Test() {
        statService.calcStat(StatType.Distance, null, 0)
    }

    @Test(expected = NoSuchElementException::class)
    fun calcStatWithNothingFound5Test() {
        statService.calcStat(StatType.Distance, 2000, null)
    }

    @Test(expected = NoSuchElementException::class)
    fun calcStatWithNothingFound6Test() {
        statService.calcStat(StatType.Distance, 2000, 0)
    }

    @Test
    fun calcStatWithOthersParamsTest() {
        // -- Stat type is Date

        // Without start range, end range < last running date
        var stat = statService.calcStat(StatType.Date, null, parseDateToLong("2015-09-20"))
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        var endInterval = createDate(2015, 9, 20, 23, 59, 59, utc = false)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(20))
        collector.checkThat(stat.daysCountInterval, equalTo(20))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Without start range, end range > last running date
        stat = statService.calcStat(StatType.Date, null, parseDateToLong("2015-10-15"))
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        endInterval = createDate(2015, 10, 15, 23, 59, 59, utc = false)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(45))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range < first running, without end range
        stat = statService.calcStat(StatType.Date, parseDateToLong("2015-06-01"), null)
        var startInterval = createDate(2015, 6, 1, utc = false)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(116))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range < first running, end range < last running date
        stat = statService.calcStat(StatType.Date, parseDateToLong("2015-06-01"), parseDateToLong("2015-09-20"))
        startInterval = createDate(2015, 6, 1, utc = false)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        endInterval = createDate(2015, 9, 20, 23, 59, 59, utc = false)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(20))
        collector.checkThat(stat.daysCountInterval, equalTo(112))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range < first running, end range > last running date
        stat = statService.calcStat(StatType.Date, parseDateToLong("2015-06-01"), parseDateToLong("2015-10-15"))
        startInterval = createDate(2015, 6, 1, utc = false)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        endInterval = createDate(2015, 10, 15, 23, 59, 59, utc = false)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(137))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range > first running, without end range
        stat = statService.calcStat(StatType.Date, parseDateToLong("2015-09-10"), null)
        startInterval = createDate(2015, 9, 10, utc = false)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(15))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range > first running, end range < last running date
        stat = statService.calcStat(StatType.Date, parseDateToLong("2015-09-10"), parseDateToLong("2015-09-20"))
        startInterval = createDate(2015, 9, 10, utc = false)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        endInterval = createDate(2015, 9, 20, 23, 59, 59, utc = false)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(20))
        collector.checkThat(stat.daysCountInterval, equalTo(11))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range > first running, end range > last running date
        stat = statService.calcStat(StatType.Date, parseDateToLong("2015-09-10"), parseDateToLong("2015-10-15"))
        startInterval = createDate(2015, 9, 10, utc = false)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        endInterval = createDate(2015, 10, 15, 23, 59, 59, utc = false)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(36))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // -- Stat type is Distance

        // Without start range, end range is date
        stat = statService.calcStat(StatType.Distance, null, parseDateToLong("2015-06-01"))
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(24))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Without start range, end range < end range sum last running
        stat = statService.calcStat(StatType.Distance, null, 200)
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        endInterval = createDate(2015, 9, 6, 11, 16, 30)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(6))
        collector.checkThat(stat.daysCountInterval, equalTo(6))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Without start range, end range > end sum last running
        stat = statService.calcStat(StatType.Distance, null, 2000)
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(24))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range < 0, without end range
        stat = statService.calcStat(StatType.Distance, -200, null)
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(24))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range < 0, end range < sum last running
        stat = statService.calcStat(StatType.Distance, -200, 500)
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        endInterval = createDate(2015, 9, 13, 5, 43, 22)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(13))
        collector.checkThat(stat.daysCountInterval, equalTo(13))
        collector.checkThat(stat.type, equalTo(StatType.Distance))

        // Start range < 0, end range > sum last running
        stat = statService.calcStat(StatType.Distance, -200, 2000)
        collector.checkThat(stat.startDate, comparesEqualTo(startDate))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(24))
        collector.checkThat(stat.type, equalTo(StatType.Distance))

        // Start range > 0, without end range
        stat = statService.calcStat(StatType.Distance, 200, null)
        startInterval = createDate(2015, 9, 6, 12, 0, 29)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(18))
        collector.checkThat(stat.type, equalTo(StatType.Date))

        // Start range > 0, end range < sum last running
        stat = statService.calcStat(StatType.Distance, 200, 500)
        startInterval = createDate(2015, 9, 6, 12, 0, 29)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        endInterval = createDate(2015, 9, 13, 5, 43, 22)
        collector.checkThat(stat.endDate, comparesEqualTo(endInterval))
        collector.checkThat(stat.daysCountAll, equalTo(13))
        collector.checkThat(stat.daysCountInterval, equalTo(7))
        collector.checkThat(stat.type, equalTo(StatType.Distance))

        // Start range > 0, end range > sum last running
        stat = statService.calcStat(StatType.Distance, 200, 2000)
        startInterval = createDate(2015, 9, 6, 12, 0, 29)
        collector.checkThat(stat.startDate, comparesEqualTo(startInterval))
        collector.checkThat(stat.endDate, comparesEqualTo(endDate))
        collector.checkThat(stat.daysCountAll, equalTo(24))
        collector.checkThat(stat.daysCountInterval, equalTo(18))
        collector.checkThat(stat.type, equalTo(StatType.Distance))
    }

    @Test
    fun calcStatDistanceIntervalTest() {
        val stat = statService.calcStat(StatType.Distance, 200, 500)
        collector.checkThat(stat.startDistance, equalTo(200L))
        collector.checkThat(stat.endDistance, equalTo(500L))

        // Dates
        collector.checkThat(stat.startDate, comparesEqualTo(createDate(2015, 9, 6, 12, 0, 29)))
        collector.checkThat(stat.endDate, comparesEqualTo(createDate(2015, 9, 13, 5, 43, 22)))
        collector.checkThat(stat.daysCountAll, equalTo(13))
        collector.checkThat(stat.daysCountInterval, equalTo(7))

        // Distance
        collector.checkThat(stat.distanceAll, equalTo(500L))
        collector.checkThat(stat.distancePerDayAvg.toDouble(), closeTo(38.462, 0.001))
        collector.checkThat(stat.distancePerTrainingAvg.toDouble(), closeTo(6.85, 0.001))
        collector.checkThat(stat.distanceMaxOneMan.profile.id, equalTo(63399502L))
        collector.checkThat(stat.distanceMaxOneMan.numberOfRuns, equalTo(6))
        collector.checkThat(stat.distanceMaxOneMan.sumDistance, equalTo(56))

        // Trainings
        collector.checkThat(stat.trainingCountAll, equalTo(73))
        collector.checkThat(stat.trainingCountPerDayAvg.toDouble(), closeTo(5.615, 0.001))
        collector.checkThat(stat.trainingMaxOneMan.profile.id, equalTo(63399502L))
        collector.checkThat(stat.trainingMaxOneMan.numberOfRuns, equalTo(6))
        collector.checkThat(stat.trainingMaxOneMan.sumDistance, equalTo(56))

        // Runners
        collector.checkThat(stat.runnersCountAll, equalTo(31))
        collector.checkThat(stat.runnersCountInterval, equalTo(23))
        collector.checkThat(stat.newRunners, iterableWithSize(10))
        val newRunnersIdsActual = stat.newRunners.joinToString(", ") { it.id.toString() }
        val newRunnersIdsExpect = "122104881, 179202944, 24237989, 293509406, 12274403, 29244944, 148329750, 2123686, 5967972, 30441799"
        collector.checkThat(newRunnersIdsActual, equalTo(newRunnersIdsExpect))
        collector.checkThat(stat.countNewRunners, equalTo(10))

        collector.checkThat(stat.topAllRunners, iterableWithSize(5))
        val topAllRunnersActually = stat.topAllRunners.joinToString(", ") { "(${it.profile.id},${it.numberOfRuns},${it.sumDistance})" }
        val topAllRunnersExpect = "(63399502,6,56), (12274403,3,43), (6465387,6,39), (2437792,5,36), (5488260,2,32)"
        collector.checkThat(topAllRunnersActually, equalTo(topAllRunnersExpect))

        collector.checkThat(stat.topIntervalRunners, iterableWithSize(5))
        val topIntervalRunnersActually = stat.topIntervalRunners.joinToString(", ") { "(${it.profile.id},${it.numberOfRuns},${it.sumDistance})" }
        val topIntervalRunnersExpect = "(12274403,3,43), (63399502,3,29), (293509406,2,27), (2437792,3,27), (5488260,1,20)"
        collector.checkThat(topIntervalRunnersActually, equalTo(topIntervalRunnersExpect))

        collector.checkThat(stat.type, equalTo(StatType.Distance))
    }

    @Test
    fun calcStatDateIntervalTest() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("+5")));

        val stat = statService.calcStat(StatType.Date, parseDateToLong("2015-09-06"),
                parseDateToLong("2015-09-13"))
        collector.checkThat(stat.startDistance, nullValue())
        collector.checkThat(stat.endDistance, nullValue())

        // Dates
        collector.checkThat(stat.startDate, comparesEqualTo(createDate(2015, 9, 6, utc = false)))
        collector.checkThat(stat.endDate, comparesEqualTo(createDate(2015, 9, 13, 23, 59, 59, utc = false)))
        collector.checkThat(stat.daysCountAll, equalTo(13))
        collector.checkThat(stat.daysCountInterval, equalTo(8))

        // Distance
        collector.checkThat(stat.distanceAll, equalTo(508L))
        collector.checkThat(stat.distancePerDayAvg.toDouble(), closeTo(39.077, 0.001))
        collector.checkThat(stat.distancePerTrainingAvg.toDouble(), closeTo(6.773, 0.001))
        collector.checkThat(stat.distanceMaxOneMan.profile.id, equalTo(63399502L))
        collector.checkThat(stat.distanceMaxOneMan.numberOfRuns, equalTo(6))
        collector.checkThat(stat.distanceMaxOneMan.sumDistance, equalTo(56))

        // Trainings
        collector.checkThat(stat.trainingCountAll, equalTo(75))
        collector.checkThat(stat.trainingCountPerDayAvg.toDouble(), closeTo(5.769, 0.001))
        collector.checkThat(stat.trainingMaxOneMan.profile.id, equalTo(63399502L))
        collector.checkThat(stat.trainingMaxOneMan.numberOfRuns, equalTo(6))
        collector.checkThat(stat.trainingMaxOneMan.sumDistance, equalTo(56))

        // Runners
        collector.checkThat(stat.runnersCountAll, equalTo(31))
        collector.checkThat(stat.runnersCountInterval, equalTo(26))
        collector.checkThat(stat.newRunners, iterableWithSize(13))
        val newRunnersIdsActual = stat.newRunners.joinToString(", ") { it.id.toString() }
        val newRunnersIdsExpect = "67799362, 5488260, 18530540, 122104881, 179202944, 24237989, 293509406, 12274403, 29244944, 148329750, 2123686, 5967972, 30441799"
        collector.checkThat(newRunnersIdsActual, equalTo(newRunnersIdsExpect))
        collector.checkThat(stat.countNewRunners, equalTo(13))

        collector.checkThat(stat.topAllRunners, iterableWithSize(5))
        val topAllRunnersActually = stat.topAllRunners.joinToString(", ") { "(${it.profile.id},${it.numberOfRuns},${it.sumDistance})" }
        val topAllRunnersExpect = "(63399502,6,56), (12274403,3,43), (6465387,6,39), (2437792,5,36), (5488260,2,32)"
        collector.checkThat(topAllRunnersActually, equalTo(topAllRunnersExpect))

        collector.checkThat(stat.topIntervalRunners, iterableWithSize(5))
        val topIntervalRunnersActually = stat.topIntervalRunners.joinToString(", ") { "(${it.profile.id},${it.numberOfRuns},${it.sumDistance})" }
        val topIntervalRunnersExpect = "(12274403,3,43), (5488260,2,32), (63399502,3,29), (293509406,2,27), (2437792,3,27)"
        collector.checkThat(topIntervalRunnersActually, equalTo(topIntervalRunnersExpect))

        collector.checkThat(stat.type, equalTo(StatType.Date))
    }

    @Test
    /** People with post without distance shouldn't get here */
    fun findNewRunnersTest() {
        val stat = statService.calcStat(StatType.Distance, null, 122)
        collector.checkThat(stat.type, equalTo(StatType.Date))
        collector.checkThat(stat.newRunners, iterableWithSize(15))
        val newRunnersIdsActual = stat.newRunners.joinToString(", ") { it.id.toString() }
        val newRunnersIdsExpect = "39752943, 8429458, 84566235, 82121484, 18273238, 2437792, 11351451, 63399502, " +
                "117963335, 258765420, 282180599, 10811344, 1553750, 52649788, 151575104"
        collector.checkThat(newRunnersIdsActual, equalTo(newRunnersIdsExpect))
        collector.checkThat(stat.countNewRunners, equalTo(15))
    }

}