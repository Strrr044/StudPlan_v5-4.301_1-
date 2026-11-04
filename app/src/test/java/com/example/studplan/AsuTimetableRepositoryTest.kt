package com.example.studplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsuTimetableRepositoryTest {
    @Test
    fun parsesIcalendarEventIntoDatedLesson() {
        val calendar = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;TZID=Asia/Krasnoyarsk:20261026T112000
            DTEND;TZID=Asia/Krasnoyarsk:20261026T125000
            UID:5017633
            DESCRIPTION:Ахмерова И.Г. (доц.)
            LOCATION:319Л
            SUMMARY:11:20 лек. Гидродинамика и газовая динамика (319Л)
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val lesson = AsuTimetableRepository.parseCalendar(calendar).single()

        assertEquals("11:20", lesson.time)
        assertEquals("12:50", lesson.end)
        assertEquals("Гидродинамика и газовая динамика", lesson.subject)
        assertEquals("319Л", lesson.room)
        assertEquals("Ахмерова И.Г. (доц.)", lesson.teacher)
        assertEquals("лек.", lesson.lessonType)
        assertEquals("asu", lesson.source)
        assertTrue(lesson.date > 0L)
        assertEquals(0, lesson.day)
    }

    @Test
    fun parsesNormalTimetableHtml() {
        val html = """
            <li class="selected_date-box-cell">2026</li>
            <div class="schedule_table-body-rows_group">
              <div data-type="date"><span class="schedule_table-date-dm">26.10</span></div>
              <div class="schedule_table-body-row">
                <div class="schedule_table-cell" data-type="num">3</div>
                <div class="schedule_table-cell" data-type="time">11:20 - 12:50</div>
                <div class="schedule_table-cell" data-type="subject"><span>лек.</span> Гидродинамика и газовая динамика</div>
                <div class="schedule_table-cell" data-type="lecturer"><a>Ахмерова И.Г.</a></div>
                <div class="schedule_table-cell" data-type="room"><a>319Л</a></div>
              </div>
            </div>
        """.trimIndent()

        val lesson = AsuTimetableRepository.parseHtml(html).single()

        assertEquals("11:20", lesson.time)
        assertEquals("12:50", lesson.end)
        assertEquals("Гидродинамика и газовая динамика", lesson.subject)
        assertEquals("319Л", lesson.room)
        assertEquals("Ахмерова И.Г.", lesson.teacher)
        assertEquals("лек.", lesson.lessonType)
        assertEquals("asu", lesson.source)
    }
}
