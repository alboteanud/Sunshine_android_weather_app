package com.craiovadata.android.sunshine.data.network

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import com.craiovadata.android.sunshine.CityData.inTestMode
import com.craiovadata.android.sunshine.ui.main.MainActivity
import com.craiovadata.android.sunshine.ui.models.WeatherEntry
import com.craiovadata.android.sunshine.utilities.AppExecutors
import com.craiovadata.android.sunshine.utilities.LogUtils.addTestText
import com.craiovadata.android.sunshine.utilities.NotifUtils
import java.util.concurrent.TimeUnit

/**
 * Provides an API for doing all operations with the server data
 */
class NetworkDataSource private constructor(
    private val context: Context,
    private val mExecutors: AppExecutors
) {

    private val mWorkManager: WorkManager = WorkManager.getInstance(context)

    // LiveData storing the latest downloaded weather forecasts
    private val mDownloadedWeatherForecasts: MutableLiveData<Array<WeatherEntry>> =
        MutableLiveData()
    private val mDownloadedCurrentWeather: MutableLiveData<Array<WeatherEntry>> = MutableLiveData()

    val forecasts: LiveData<Array<WeatherEntry>>
        get() = mDownloadedWeatherForecasts

    val currentWeather: LiveData<Array<WeatherEntry>>
        get() = mDownloadedCurrentWeather

    fun scheduleFetchWeather() {
//        val input = workDataOf("some_key" to "some_val")
        val constraints: Constraints = Constraints.Builder().apply {
            setRequiredNetworkType(NetworkType.CONNECTED)
            setRequiresBatteryNotLow(true)
//            setRequiresDeviceIdle(true)     // not working with BackoffPolicy
        }.build()

        val repeatIntervalHours: Long = if (inTestMode) 2 else 6

        val request: PeriodicWorkRequest = PeriodicWorkRequest.Builder(MyWorker::class.java, repeatIntervalHours, TimeUnit.HOURS, 3, TimeUnit.HOURS)
//                .setInputData(input)
            .setConstraints(constraints)
            .setInitialDelay(repeatIntervalHours, TimeUnit.HOURS)
//                .addTag(TAG_WORK_NAME)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        mWorkManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun fetchWeather(function: (success: Boolean) -> Unit) {
        val weatherRequestUrl = NetworkUtils.getUrlString(context)
        NetworkUtils.getResponseFromHttpUrl(context, weatherRequestUrl) { jsonWeatherResponse ->
            // Parse the JSON into a list of weather forecasts
            val response = WeatherJsonParser().parseForecastWeather(jsonWeatherResponse)

            Log.d(LOG_TAG, "weather JSON has ${response.weatherForecast.size} values")
            addTestText(context, "${response.weatherForecast.size}sy")

            // As long as there are weather forecasts, update the LiveData storing the most recent
            // weather forecasts. This will trigger observers of that LiveData, such as the Repo

            if (response.weatherForecast.isNullOrEmpty()) {
                function.invoke(false)
                addTestText(context, "syFailNullWe")
            } else {
                val entries = response.weatherForecast
                mDownloadedWeatherForecasts.postValue(entries)
                NotifUtils.notifyIfNeeded(context, entries[0])
                function.invoke(true)
            }

        }
    }

    fun fetchWeatherForMultipleCitiesTest(context: Context, cityIds: List<Int>
    ) {
        Log.d(LOG_TAG, "Fetch weather days started")
        mExecutors.networkIO().execute {
            try {
                val descriptions = mutableMapOf<Int, String>()
                cityIds.forEach { id ->
                    val weatherRequestUrl =
                        NetworkUtils.getUrl2(
                            context,
                            id,
                            MainActivity.languageParamMultipleCitiesTest
                        ) ?: return@execute

                    // Use the URL to retrieve the JSON
                    val jsonWeatherResponse = NetworkUtils.getResponseFromHttpUrl(weatherRequestUrl)

                    // Parse the JSON into a list of weather forecasts
                    val response = WeatherJsonParser().parseForecastWeather2(jsonWeatherResponse)
                    Log.d(LOG_TAG, "parsing finished. Size: ${response.weatherForecast.size} ")
                    response.weatherForecast.forEach { entry ->

                        if (!descriptions.containsKey(entry.id)) {
                            descriptions[entry.id] = entry.description
                            if (inTestMode) {
                                val descriptionStringName = "condition_" + entry.id.toString()
                                val res: Resources = context.resources
                                val isTranslated = res.getString(
                                    res.getIdentifier(
                                        descriptionStringName, "string", context.packageName
                                    )
                                ) == entry.description

                                if (!isTranslated)
                                    Log.e(
                                        "NEW description",
                                        entry.id.toString() + "  " + entry.description
                                    )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Server probably invalid
                e.printStackTrace()
            }
        }
    }

    fun fetchCurrentWeather() {
        val weatherRequestUrl = NetworkUtils.getUrlCurrentWeather(context)
        NetworkUtils.getResponseFromHttpUrl(context, weatherRequestUrl) { jsonWeatherResponse ->
            val response = WeatherJsonParser().parseCurrentWeather(jsonWeatherResponse)
            Log.e(
                LOG_TAG,
                "JSON Parsing finished Current Weather: ${response.weatherForecast[0].degrees}"
            )

            // As long as there are weather forecasts, update the LiveData storing the most recent
            // weather forecasts. This will trigger observers of that LiveData, such as the RepositoryWeather.
            if (response.weatherForecast.isNotEmpty()) {
                val entries = response.weatherForecast
                mDownloadedCurrentWeather.postValue(entries)
                // Will eventually do something with the downloaded data
            }

        }

    }

    companion object {
        private val LOG_TAG = NetworkDataSource::class.java.simpleName
        val NUM_MIN_DATA_COUNTS = if (inTestMode) 10 else 39

        const val WORK_NAME = "my-work-name-v3"

        // For Singleton instantiation
        private val LOCK = Any()
        private var sInstance: NetworkDataSource? = null

        // Get the singleton for this class
        fun getInstance(context: Context, executors: AppExecutors): NetworkDataSource {
            Log.d(LOG_TAG, "Getting the network data source")
            if (sInstance == null) {
                synchronized(LOCK) {
                    sInstance = NetworkDataSource(context.applicationContext, executors)
                    Log.d(LOG_TAG, "Made new network data source")
                }
            }
            return sInstance!!
        }



    }

}