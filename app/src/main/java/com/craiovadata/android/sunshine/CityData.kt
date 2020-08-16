package com.craiovadata.android.sunshine

import android.content.Context
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar.DST_OFFSET
import java.util.Calendar.ZONE_OFFSET
import java.util.TimeZone.getTimeZone

object CityData {
//  https://api.windy.com/api/webcams/v2/list/orderby=popularity/nearby=42.39,-83.12,30?key=D9shU62zYfuI35AkCM9F6xq5x6lZ1qfb&show=webcams:location,image
    const val TIME_ZONE_ID = "America/Detroit"

    private val images = intArrayOf(
        R.drawable.city1,
        R.drawable.city2,
        R.drawable.city3,
        R.drawable.stabil1,
        R.drawable.stabil2,
        R.drawable.stabil3,
        R.drawable.stabil4,
        R.drawable.stabil5,
        R.drawable.stabil6,
        R.drawable.stabil7,
        R.drawable.stabil8,
        R.drawable.stabil9,
        R.drawable.stabil10
    )

    const val IS_IMPERIAL_UNITS_DEFAULT = true
    val inTestMode = BuildConfig.DEBUG
    const val DEFAULT_ZOOM_LEVEL: Int = 10

    @JvmStatic
    fun getCityTimeZone(): TimeZone {
        val tz = getTimeZone(TIME_ZONE_ID)
        if (inTestMode) {
            require(tz.id != "GMT") { "timeZone probably wrong: GMT" }
        }
        return tz
    }

    @JvmStatic
    fun getCityOffset(): Long {
        val cal = GregorianCalendar()
        cal.timeZone = getCityTimeZone()
        return (cal.get(ZONE_OFFSET) + cal.get(DST_OFFSET)).toLong()
    }

    @JvmStatic
    fun getFormatterCityTZ(pattern: String): SimpleDateFormat {
        val cal = GregorianCalendar()
        cal.timeZone = getCityTimeZone()
        val simpleDateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        simpleDateFormat.calendar = cal
        return simpleDateFormat
    }

    fun getBackResId(context: Context): Int {
//        if (BuildConfig.DEBUG) {
//            val imgNo = Random().nextInt(2)
//            return images[imgNo]
//        }
//        val now = System.currentTimeMillis()
//        val hoursSinceEpoch = TimeUnit.MILLISECONDS.toHours(now)
//        val n = (hoursSinceEpoch % images.size).toInt()

        val noImg = if (inTestMode) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)

            val storedResNo = preferences.getInt("prefKeyI", 0)

            if (storedResNo == images.size) {  // checking bound
                preferences.edit().putInt("prefKeyI", 0).apply()
                val imgRandomNumber = 2 + Random().nextInt(images.size - 2)
                imgRandomNumber
            } else {
                preferences.edit().putInt("prefKeyI", storedResNo + 1).apply()
                storedResNo
            }
        } else {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val isFirstOpen = pref.getBoolean("key_first_open", true)
            if (isFirstOpen) {
                pref.edit().putBoolean("key_first_open", false).apply()
                0
            } else {
                Random().nextInt(images.size)
            }
        }

        return images[noImg]
    }


}
