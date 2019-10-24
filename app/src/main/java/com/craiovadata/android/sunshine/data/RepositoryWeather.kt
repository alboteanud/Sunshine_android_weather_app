package com.craiovadata.android.sunshine.data

import android.text.format.DateUtils
import android.util.Log

import com.craiovadata.android.sunshine.AppExecutors
import com.craiovadata.android.sunshine.data.database.ListWeatherEntry
import com.craiovadata.android.sunshine.data.database.WeatherDao
import com.craiovadata.android.sunshine.data.database.WeatherEntry
import com.craiovadata.android.sunshine.data.network.NetworkDataSource
import com.craiovadata.android.sunshine.utilities.Utils

import java.util.Date
import java.util.concurrent.TimeUnit

import androidx.lifecycle.LiveData

import android.text.format.DateUtils.DAY_IN_MILLIS
import android.text.format.DateUtils.HOUR_IN_MILLIS
import java.lang.System.currentTimeMillis


/**
 * Handles data operations in Sunshine. Acts as a mediator between [NetworkDataSource]
 * and [WeatherDao]
 */
class RepositoryWeather private constructor(
    private val mWeatherDao: WeatherDao,
    private val mNetworkDataSource: NetworkDataSource,
    private val mExecutors: AppExecutors
) {
    private var mInitialized = false
    private var mInitializedCW = false

    init {
        mNetworkDataSource.forecasts.observeForever { newForecastsFromNetwork ->
            mExecutors.diskIO().execute {
                // Deletes old historical data
                deleteOldData()
                // Insert our new weather data into Sunshine's database
                mWeatherDao.bulkInsert(*newForecastsFromNetwork)
                Log.d(LOG_TAG, "Old weather deleted. New values inserted.")
            }
        }

        mNetworkDataSource.currentWeather.observeForever { newDataFromNetwork ->
            mExecutors.diskIO().execute {
                mWeatherDao.bulkInsert(*newDataFromNetwork)
            }
        }
    }

    /**
     * Database related operations
     */
    val dayWeatherEntries: LiveData<List<ListWeatherEntry>>
        get() {
            initializeData()
            val offset = Utils.getCityOffset()

            val daysSinceEpoch = TimeUnit.MILLISECONDS.toDays(currentTimeMillis())
            val tomorrowMidnightNormalizedUtc = (daysSinceEpoch + 1) * DAY_IN_MILLIS

            return mWeatherDao.getMidDayForecast(
                Date(tomorrowMidnightNormalizedUtc),
                offset,
                HOUR_IN_MILLIS
            )
        }

    val currentWeather: LiveData<List<WeatherEntry>>
        get() {
            initDataCurrentWeather()
            val recentlyMills = currentTimeMillis() - DateUtils.MINUTE_IN_MILLIS * 16
            val recentDate = Date(recentlyMills)
            return mWeatherDao.getCurrentWeather(recentDate)

        }

    val currentWeatherList: List<WeatherEntry>
        get() {
            initDataCurrentWeather()

            val nowMills = currentTimeMillis()
            val nowDate = Date(nowMills)

            val recentlyMills = nowMills - DateUtils.MINUTE_IN_MILLIS * 16
            val recentDate = Date(recentlyMills)

            return mWeatherDao.getCurrentWeatherList(nowDate, recentDate)
        }

    val nextHoursWeather: LiveData<List<ListWeatherEntry>>
        get() {
//            initializeData()   // not needed. DaysWeather will init it already
            val utcNowMillis = currentTimeMillis()
            val date = Date(utcNowMillis)
            return mWeatherDao.getCurrentForecast(date)
        }

    /**
     * Checks if there are enough days of future weather for the app to display all the needed data.
     *
     * @return Whether a fetch is needed
     */
    private val isFetchNeeded: Boolean
        get() {
            val now = Date(currentTimeMillis())
            val count = mWeatherDao.countAllFutureWeatherEntries(now)
            return count < NetworkDataSource.NUM_MIN_DATA_COUNTS
        }

    private val isFetchNeededCW: Boolean
        get() {
            val dateRecently = Date(currentTimeMillis() - DateUtils.MINUTE_IN_MILLIS * 16)
            val count = mWeatherDao.countCurrentWeather(dateRecently)
            return count < 1
        }


    /**
     * Creates periodic sync tasks and checks to see if an immediate sync is required. If an
     * immediate sync is required, this method will take care of making sure that sync occurs.
     */
    @Synchronized
    fun initializeData() {

        // Only perform initialization once per app lifetime. If initialization has already been
        // performed, we have nothing to do in this method.
        if (mInitialized) return
        mInitialized = true

        // This method call triggers Sunshine to create its task to synchronize weather data
        // periodically.
        mNetworkDataSource.scheduleRecurringFetchWeatherSync()

        mExecutors.diskIO().execute {
            Log.d(LOG_TAG, "execute initData")
            if (isFetchNeeded)
                startFetchWeatherService()
        }
    }

    @Synchronized
    private fun initDataCurrentWeather() {
        if (mInitializedCW) return
        mInitializedCW = true

        mExecutors.diskIO().execute {
            Log.d(LOG_TAG, "execute initDataCurrentWeather")
            if (isFetchNeededCW) {
                mNetworkDataSource.startFetchCurrentWeatherService()
            }
        }
    }

    @Synchronized
    fun fetchCurrentWeatherIfNeeded() {
        mExecutors.diskIO().execute {
            if (isFetchNeededCW) {
                mNetworkDataSource.startFetchCurrentWeatherService()
            }
        }
    }

    private fun deleteOldData() {
        //        Date today = SunshineDateUtils.getNormalizedUtcDateForToday();
        val oldTime = currentTimeMillis() - HOUR_IN_MILLIS
        val date = Date(oldTime)
        mWeatherDao.deleteOldWeather(date)
    }

    private fun startFetchWeatherService() {
        mNetworkDataSource.startFetchWeatherService()
    }

    fun insertFakeDataCW() {
        val recentlyMills = currentTimeMillis() - DateUtils.MINUTE_IN_MILLIS * 16
        val recentDate = Date(recentlyMills)

        mExecutors.diskIO().execute {
            val entries = mWeatherDao.getCurrentWeather2(recentDate)

            val newEntries = entries.map { entry ->
                entry.cityName = "d" + entry.cityName
                entry.date.time = entry.date.time + DateUtils.MINUTE_IN_MILLIS * 1
                entry
            }

            val array = newEntries.toTypedArray<WeatherEntry>()
            mWeatherDao.bulkInsert(*array)
        }

    }

    companion object {
        private val LOG_TAG = RepositoryWeather::class.java.simpleName

        // For Singleton instantiation
        private val LOCK = Any()
        private var sInstance: RepositoryWeather? = null

        @Synchronized
        fun getInstance(
            weatherDao: WeatherDao, networkDataSource: NetworkDataSource,
            executors: AppExecutors
        ): RepositoryWeather {
            Log.d(LOG_TAG, "Getting the repository")
            if (sInstance == null) {
                synchronized(LOCK) {
                    sInstance = RepositoryWeather(
                        weatherDao, networkDataSource,
                        executors
                    )
                    Log.d(LOG_TAG, "Made new repository")
                }
            }
            return sInstance!!
        }
    }
}