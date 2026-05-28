package com.quitsmoke.app.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class SmokeRepositoryTest {

    private lateinit var dao: SmokeRecordDao
    private lateinit var repo: SmokeRepository

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val todayStr get() = sdf.format(Date())

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repo = SmokeRepository(dao)
    }

    @Test
    fun `getTodayStr returns today date in correct format`() {
        val result = repo.getTodayStr()
        assertTrue(result.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        assertEquals(todayStr, result)
    }

    @Test
    fun `getCurrentHour returns valid hour`() {
        val hour = repo.getCurrentHour()
        assertTrue(hour in 0..23)
    }

    @Test
    fun `recordSmoke creates record with correct fields`() = runTest {
        coEvery { dao.insert(any()) } returns 42L

        val record = repo.recordSmoke()

        assertEquals(todayStr, record.dateStr)
        assertTrue(record.hourOfDay in 0..23)
        assertTrue(record.timestamp > 0)
        assertEquals(42L, record.id)
        coVerify { dao.insert(any()) }
    }

    @Test
    fun `undoLastSmoke returns false when no records`() = runTest {
        coEvery { dao.getLatestRecord() } returns null

        val result = repo.undoLastSmoke()

        assertFalse(result)
    }

    @Test
    fun `undoLastSmoke returns false when latest is not today`() = runTest {
        val oldRecord = SmokeRecord(
            id = 1,
            timestamp = System.currentTimeMillis() - 86400000,
            dateStr = "2020-01-01",
            hourOfDay = 10
        )
        coEvery { dao.getLatestRecord() } returns oldRecord

        val result = repo.undoLastSmoke()

        assertFalse(result)
    }

    @Test
    fun `undoLastSmoke deletes and returns true when latest is today`() = runTest {
        val todayRecord = SmokeRecord(
            id = 1,
            timestamp = System.currentTimeMillis(),
            dateStr = todayStr,
            hourOfDay = 10
        )
        coEvery { dao.getLatestRecord() } returns todayRecord
        coEvery { dao.deleteById(any()) } returns Unit

        val result = repo.undoLastSmoke()

        assertTrue(result)
        coVerify { dao.deleteById(1) }
    }

    @Test
    fun `getTodayCount delegates to dao`() = runTest {
        coEvery { dao.getCountByDate(todayStr) } returns 5

        val count = repo.getTodayCount()

        assertEquals(5, count)
    }

    @Test
    fun `getTotalCount delegates to dao`() = runTest {
        coEvery { dao.getTotalCount() } returns 42

        val total = repo.getTotalCount()

        assertEquals(42, total)
    }

    @Test
    fun `insertRecord delegates to dao`() = runTest {
        coEvery { dao.insert(any()) } returns 1L

        val record = SmokeRecord(timestamp = 1000L, dateStr = "2024-01-01", hourOfDay = 10)
        repo.insertRecord(record)

        coVerify { dao.insert(record) }
    }

    @Test
    fun `insertRecords skips duplicates`() = runTest {
        val records = listOf(
            SmokeRecord(timestamp = 1000L, dateStr = "2024-01-01", hourOfDay = 10),
            SmokeRecord(timestamp = 2000L, dateStr = "2024-01-01", hourOfDay = 11)
        )
        coEvery { dao.findDuplicate(1000L, "2024-01-01", 10, "") } returns records[0]
        coEvery { dao.findDuplicate(2000L, "2024-01-01", 11, "") } returns null
        coEvery { dao.insertAll(any()) } returns listOf(2L)

        val inserted = repo.insertRecords(records)

        assertEquals(1, inserted)
        coVerify { dao.insertAll(match { it.size == 1 }) }
    }

    @Test
    fun `insertRecords returns zero when all duplicates`() = runTest {
        val records = listOf(
            SmokeRecord(timestamp = 1000L, dateStr = "2024-01-01", hourOfDay = 10)
        )
        coEvery { dao.findDuplicate(any(), any(), any(), any()) } returns records[0]

        val inserted = repo.insertRecords(records)

        assertEquals(0, inserted)
    }

    @Test
    fun `previewInsertRecords returns correct count`() = runTest {
        val records = listOf(
            SmokeRecord(timestamp = 1000L, dateStr = "2024-01-01", hourOfDay = 10),
            SmokeRecord(timestamp = 2000L, dateStr = "2024-01-02", hourOfDay = 11)
        )
        coEvery { dao.findDuplicate(1000L, "2024-01-01", 10, "") } returns null
        coEvery { dao.findDuplicate(2000L, "2024-01-02", 11, "") } returns records[1]

        val preview = repo.previewInsertRecords(records)

        assertEquals(1, preview)
    }

    @Test
    fun `getGoalReport calculates remaining correctly`() = runTest {
        coEvery { dao.getCountByDate(todayStr) } returns 3
        coEvery { dao.getDailyStatsDesc(any()) } returns emptyList()
        coEvery { dao.getDailyStatsSince(any()) } returns emptyList()

        val report = repo.getGoalReport(10)

        assertEquals(10, report.dailyTarget)
        assertEquals(3, report.todayCount)
        assertEquals(7, report.remaining)
    }

    @Test
    fun `getGoalReport remaining is zero when exceeded`() = runTest {
        coEvery { dao.getCountByDate(todayStr) } returns 15
        coEvery { dao.getDailyStatsDesc(any()) } returns emptyList()
        coEvery { dao.getDailyStatsSince(any()) } returns emptyList()

        val report = repo.getGoalReport(10)

        assertEquals(0, report.remaining)
    }

    @Test
    fun `getGoalReport streak starts at tracking start when no prior data`() = runTest {
        coEvery { dao.getCountByDate(todayStr) } returns 0
        coEvery { dao.getFirstRecordDate() } returns null
        coEvery { dao.getDailyStatsDesc(any()) } returns emptyList()
        coEvery { dao.getDailyStatsSince(any()) } returns emptyList()

        val report = repo.getGoalReport(10)

        assertEquals(1, report.targetStreak)
        assertEquals(1, report.noSmokeStreak)
    }

    @Test
    fun `getGoalReport streak breaks on first violation`() = runTest {
        coEvery { dao.getCountByDate(todayStr) } returns 5

        val today = todayStr
        val yesterday = sdf.format(Date().apply { time -= 86400000 })
        coEvery { dao.getDailyStatsDesc(any()) } returns listOf(
            DailyStat(today, 5),
            DailyStat(yesterday, 15)
        )
        coEvery { dao.getDailyStatsSince(any()) } returns listOf(
            DailyStat(today, 5),
            DailyStat(yesterday, 15)
        )

        val report = repo.getGoalReport(10)

        assertEquals(1, report.targetStreak)
        assertEquals(0, report.noSmokeStreak)
    }

    @Test
    fun `getWeekReport returns 7 daily stats`() = runTest {
        val sixDaysAgo = sdf.format(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
        }.time)
        coEvery { dao.getFirstRecordDate() } returns sixDaysAgo
        coEvery { dao.getDailyStatsRange(any(), any()) } returns emptyList()

        val report = repo.getWeekReport()

        assertEquals(7, report.dailyStats.size)
        assertEquals(0, report.totalWeek)
        assertEquals("0", report.avgDaily)
    }

    @Test
    fun `getWeekReport averages only days since tracking start`() = runTest {
        val startDate = sdf.format(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -2)
        }.time)
        coEvery { dao.getFirstRecordDate() } returns startDate
        coEvery { dao.getDailyStatsRange(any(), any()) } returnsMany listOf(
            listOf(DailyStat(todayStr, 6)),
            emptyList()
        )

        val report = repo.getWeekReport()

        assertEquals(3, report.dailyStats.size)
        assertEquals(6, report.totalWeek)
        assertEquals("2.0", report.avgDaily)
    }

    @Test
    fun `getWeekReport trend is down when current less than previous`() = runTest {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(cal.time)
        val oldStartDate = sdf.format(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }.time)

        coEvery { dao.getFirstRecordDate() } returns oldStartDate
        coEvery { dao.getDailyStatsRange(any(), any()) } returnsMany listOf(
            listOf(DailyStat(today, 5)),
            listOf(DailyStat(today, 15))
        )

        val report = repo.getWeekReport()

        assertEquals(-1, report.trend)
    }
}
