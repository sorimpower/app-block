package com.sorimpower.app.feature.phoneinsight.reminder

import java.time.ZonedDateTime

internal enum class InsightDigestSlot(val requestCode:Int,val hour:Int,val label:String){
    MORNING(8001,8,"오전 8시"),
    EVENING(1901,19,"오후 7시"),
}

internal object InsightDigestSchedule {
    fun next(now:ZonedDateTime,slot:InsightDigestSlot):ZonedDateTime{
        val today=now.toLocalDate().atTime(slot.hour,0).atZone(now.zone)
        return if(today.isAfter(now))today else today.plusDays(1)
    }
}
